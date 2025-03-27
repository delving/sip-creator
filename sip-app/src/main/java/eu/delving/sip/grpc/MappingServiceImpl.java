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
import java.util.ArrayList;
import java.util.List;

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
    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
    private final XmlSerializer serializer = new XmlSerializer();
    private final String basePath;

    public MappingServiceImpl(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void mapRecord(SingleRecordRequest request, StreamObserver<MappingResult> responseObserver) {
        processRecord(request, null, responseObserver);
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

    private void processRecord(SingleRecordRequest request, EditPath editPath,
            StreamObserver<MappingResult> responseObserver) {
        try {
            // Get workspace path and find SIP files
            Path sipDir = constructWorkspacePath(
                    request.getDataset().getWorkspaceId(),
                    request.getDataset().getDatasetId());
            SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);

            // Initialize RecMapping from files first
            RecMapping recMapping;
            String mappingFileUsed;
            String recordDefinitionUsed;

            if (request.hasMappingFile() && request.hasRecordDefinition()) {
                // If both are provided in the request, use those
                recMapping = getRecMappingFromStrings(
                        request.getMappingFile(),
                        request.getRecordDefinition());
                mappingFileUsed = "provided in request";
                recordDefinitionUsed = "provided in request";
            } else {
                // Otherwise use file-based initialization
                recMapping = getRecMapping(sipFiles.getMappingFile(), sipFiles.getRecordDefinition());
                mappingFileUsed = sipFiles.getMappingFile().toString();
                recordDefinitionUsed = sipFiles.getRecordDefinition().toString();
            }

            // Parse the input XML into a MetadataRecord
            MetadataRecord record = parseRecord(request.getRecordXml(), request.getLocalRecordId());

            if (request.hasEditPath() && request.getEditPath() != null) {
                NodeMapping nodeMapping = findNodeMapping(request.getEditPath().getNodeMapping(), recMapping);
                editPath = new EditPath(nodeMapping, request.getEditPath().getGroovyCode());
            }

            // Generate and compile the mapping code
            String code;
            if (editPath != null) {
                code = new CodeGenerator(recMapping)
                        .withEditPath(editPath)
                        .withTrace(true)
                        .toRecordMappingCode();
            } else {
                code = new CodeGenerator(recMapping)
                        .withTrace(true)
                        .toRecordMappingCode();
            }

            // System.out.printf("mapping code: \n %s", code);

            MappingRunner mappingRunner = new BulkMappingRunner(recMapping, code);

            logger.info("Running mapping for record with local ID: {}; with code:\n {}", request.getLocalRecordId(),
                    code);

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
