/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.grpc;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import eu.delving.groovy.BulkMappingRunner;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.cli.SIPFilesFinder;
import eu.delving.sip.cli.SIPFilesFinder.SIPFiles;
import eu.delving.sip.xml.MetadataParser;
import io.grpc.stub.StreamObserver;

public class MappingServiceImpl extends MappingServiceGrpc.MappingServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MappingServiceImpl.class);

    /**
     * Maximum number of compiled mappings kept in {@link #runnerCache}. Each
     * entry pins one compiled Groovy script class; bulk indexing typically
     * cycles through a handful of active datasets, so a small bound suffices
     * while capping Metaspace held by cached script classes.
     */
    private static final int MAX_CACHED_MAPPINGS = 32;

    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
    private final XmlSerializer serializer = new XmlSerializer();
    private final String basePath;

    /**
     * LRU cache of compiled mapping runners, keyed by a SHA-256 over the
     * mapping XML and record-definition XML of the request. Bulk indexing
     * sends thousands of records that share identical mapping content;
     * without this cache every record pays a full
     * parse + code-generation + Groovy-compilation cycle, which is the root
     * cause of slow indexing. {@code BulkMappingRunner} is safe to share
     * across concurrent calls: its compiled script is immutable and bindings
     * are created per invocation. Access-ordered {@link LinkedHashMap} in a
     * synchronized wrapper; concurrent misses on the same key may compile
     * twice, which is benign (last one wins).
     *
     * <p>Edit-path requests (Mapper live-preview edits) are deliberately
     * <em>never</em> cached: each keystroke in an editing session changes the
     * Groovy code and would mint a fresh one-shot entry, thrashing the LRU —
     * evicting the hot bulk-indexing runners and marching
     * {@code EngineHolder} toward its periodic engine reset. Preview compiles
     * are compiled and served, then discarded.
     */
    private final Map<String, MappingRunner> runnerCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHED_MAPPINGS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MappingRunner> eldest) {
                    return size() > MAX_CACHED_MAPPINGS;
                }
            });

    public MappingServiceImpl(String basePath) {
        this.basePath = basePath;
    }

    /**
     * gRPC entry point for mapping a single record: delegates to
     * {@link #processRecord(SingleRecordRequest, StreamObserver)}, which
     * compiles (or reuses) the mapping and streams back exactly one
     * {@link MappingResult} — mapped XML on success, an error payload on
     * failure.
     *
     * @param request          the record XML plus mapping/recdef content or a
     *                         workspace/dataset reference, optionally with an
     *                         edit path for Mapper live previews
     * @param responseObserver receives the single result and stream completion
     */
    @Override
    public void mapRecord(SingleRecordRequest request, StreamObserver<MappingResult> responseObserver) {
        processRecord(request, responseObserver);
    }

    private MetadataRecord parseRecord(String xmlContent, String localRecordId) throws Exception {
        // Create input stream with record wrapper and local ID
        String wrappedXml = String.format(
                "<pockets><pocket id=\"%s\">%s</pocket></pockets>",
                localRecordId,
                xmlContent.trim());

        InputStream inputStream = new ByteArrayInputStream(
                wrappedXml.getBytes(StandardCharsets.UTF_8));

        // Create parser and get first record
        MetadataParser parser = new MetadataParser(inputStream, 1);
        MetadataRecord record = parser.nextRecord();
        parser.close();

        if (record == null) {
            throw new IOException("Failed to parse record from XML input");
        }

        return record;
    }

    private RecMapping getRecMappingFromStrings(String mappingContent, String recDefContent) throws IOException {
        try (ByteArrayInputStream mappingStream = new ByteArrayInputStream(
                mappingContent.getBytes(StandardCharsets.UTF_8));
                ByteArrayInputStream recDefStream = new ByteArrayInputStream(
                        recDefContent.getBytes(StandardCharsets.UTF_8))) {
            RecDef recDef = RecDef.read(recDefStream);
            RecDefTree recDefTree = RecDefTree.create(recDef);
            return RecMapping.read(mappingStream, recDefTree);
        }
    }

    /**
     * Maps one record and streams back exactly one {@link MappingResult}.
     * Resolves the mapping and record definition either from the request
     * content (the Orchestra path) or from the workspace filesystem, compiles
     * the generated Groovy — reusing a cached runner when possible — runs it
     * against the parsed record, and completes the stream. All failures are
     * reported as an error payload inside the result rather than a gRPC
     * error, so the stream always completes normally.
     *
     * <p>Caching: only content-in-request calls without an edit path are
     * cached (see {@link #runnerCache}). File-based calls are never cached
     * because files can change on disk; edit-path calls (Mapper live preview)
     * are never cached because each edit is a one-shot compile that would
     * only thrash the LRU.
     *
     * @param request          the record XML plus mapping/recdef content or a
     *                         workspace/dataset reference, optionally with an
     *                         edit path applying in-progress Groovy edits
     * @param responseObserver receives the single result and stream completion
     */
    private void processRecord(SingleRecordRequest request, StreamObserver<MappingResult> responseObserver) {
        try {
            boolean contentProvided = request.hasMappingFile() && request.hasRecordDefinition();
            String cacheKey = (contentProvided && !request.hasEditPath()) ? cacheKey(request) : null;

            MappingRunner mappingRunner = cacheKey != null ? runnerCache.get(cacheKey) : null;
            String mappingFileUsed = contentProvided ? "provided in request" : null;
            String recordDefinitionUsed = contentProvided ? "provided in request" : null;

            if (mappingRunner == null) {
                // Initialize RecMapping — prefer content-in-request over filesystem
                RecMapping recMapping;
                if (contentProvided) {
                    recMapping = getRecMappingFromStrings(
                            request.getMappingFile(),
                            request.getRecordDefinition());
                } else {
                    // Fall back to file-based initialization
                    Path sipDir = constructWorkspacePath(
                            request.getDataset().getWorkspaceId(),
                            request.getDataset().getDatasetId());
                    SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
                    recMapping = getRecMapping(sipFiles.getMappingFile(), sipFiles.getRecordDefinition());
                    mappingFileUsed = sipFiles.getMappingFile().toString();
                    recordDefinitionUsed = sipFiles.getRecordDefinition().toString();
                }

                // Generate and compile the mapping code, weaving in the
                // in-progress Groovy edit when the Mapper preview sent one
                CodeGenerator codeGenerator = new CodeGenerator(recMapping).withTrace(true);
                if (request.hasEditPath()) {
                    NodeMapping nodeMapping = findNodeMapping(request.getEditPath().getNodeMapping(), recMapping);
                    codeGenerator.withEditPath(new EditPath(nodeMapping, request.getEditPath().getGroovyCode()));
                }
                String code = codeGenerator.toRecordMappingCode();

                mappingRunner = new BulkMappingRunner(recMapping, code);

                if (cacheKey != null) {
                    runnerCache.put(cacheKey, mappingRunner);
                    logger.info("Compiled mapping {} ({} mappings cached)",
                            cacheKey.substring(0, 12), runnerCache.size());
                }
            }

            // Parse the input XML into a MetadataRecord
            MetadataRecord record = parseRecord(request.getRecordXml(), request.getLocalRecordId());

            logger.debug("Running mapping for record with local ID: {}", request.getLocalRecordId());

            // Run the mapping
            Node mappedNode = mappingRunner.runMapping(record);

            // Handle validation
            List<String> validationMessages = new ArrayList<>();
            String validationSchemaUsed = "";

            // TODO: implement validation schema handling

            // Convert result to string
            String resultXml = serializer.toXml(mappedNode, true);

            // Send response
            MappingResult result = MappingResult.newBuilder()
                    .setMappedXml(resultXml)
                    .addAllValidationMessages(validationMessages)
                    .setMappingFileUsed(mappingFileUsed)
                    .setRecordDefinitionUsed(recordDefinitionUsed)
                    .setValidationSchemaUsed(validationSchemaUsed)
                    .build();

            responseObserver.onNext(result);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error mapping record", e);
            ErrorStatus error = ErrorStatus.newBuilder()
                    .setErrorMessage(e.getMessage())
                    .setStackTrace(stackTraceToString(e))
                    .build();

            MappingResult result = MappingResult.newBuilder()
                    .setError(error)
                    .build();

            responseObserver.onNext(result);
            responseObserver.onCompleted();
        }
    }

    /**
     * Computes the cache key for a content-in-request mapping call: a SHA-256
     * over the mapping XML and the record-definition XML, separated by a NUL
     * byte so field boundaries cannot be forged by concatenation. Any change
     * to the mapping or the recdef therefore produces a different key and a
     * fresh compilation. Edit-path requests never reach this method — they
     * are excluded from caching entirely (see {@link #runnerCache}).
     *
     * @param request the request carrying mapping and record-definition content
     * @return a hex-encoded SHA-256 digest identifying the compiled mapping
     */
    private String cacheKey(SingleRecordRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMappingFile().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(request.getRecordDefinition().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every JVM; if it is somehow absent we
            // must not silently serve a wrong mapping, so fail the call.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private RecMapping getRecMapping(Path mappingFile, Path recDefFile) throws IOException {
        try (FileInputStream mappingStream = new FileInputStream(mappingFile.toFile());
                FileInputStream recDefStream = new FileInputStream(recDefFile.toFile())) {
            RecDef recDef = RecDef.read(recDefStream);
            RecDefTree recDefTree = RecDefTree.create(recDef);
            return RecMapping.read(mappingStream, recDefTree);
        }
    }

    private NodeMapping findNodeMapping(String nodeMappingPath, RecMapping recMapping) {
        return recMapping.findNodeMapping(nodeMappingPath);
    }

    @Override
    public void startMapping(MappingRequest request, StreamObserver<MappingProgress> responseObserver) {
        try {

            Path sipDir = constructWorkspacePath(request.getWorkspaceId(), request.getDatasetId());
            SIPGRPC sipGrpc = new SIPGRPC();

            // Create a progress tracker that will send updates through the stream
            GrpcProgressTracker progressTracker = new GrpcProgressTracker(responseObserver);

            // Start the mapping process
            sipGrpc.startMappingGrpc(sipDir, progressTracker);

            // Complete the stream
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error during mapping process", e);
            MappingProgress errorProgress = MappingProgress.newBuilder()
                    .setError(ErrorStatus.newBuilder()
                            .setErrorMessage(e.getMessage())
                            .setStackTrace(stackTraceToString(e))
                            .build())
                    .build();
            responseObserver.onNext(errorProgress);
            responseObserver.onCompleted();
        }
    }

    private Path constructWorkspacePath(String workspaceId, String datasetId) {
        return Paths.get(basePath, workspaceId, "PocketMapper", "work", datasetId);
    }

    private String stackTraceToString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
