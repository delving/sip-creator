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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.delving.groovy.EngineHolder;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import io.grpc.stub.StreamObserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the compile-once behavior of {@link MappingServiceImpl#mapRecord}.
 *
 * <p>Orchestra's indexer streams thousands of {@code mapRecord} calls that all
 * carry the <em>same</em> mapping and record-definition content, varying only in
 * the record XML. Each unique mapping should therefore be parsed, generated,
 * and Groovy-compiled exactly once, with subsequent calls reusing the compiled
 * script. Without that reuse, every record pays a full parse+codegen+compile
 * cycle, which is the root cause of slow bulk indexing (2026-07-07 analysis).
 *
 * <p>Compilations are observed through {@link EngineHolder#getCompilationCount()},
 * which {@code BulkMappingRunner} increments on every real script compilation.
 * The engine's automatic reset (normally every 100 compilations) is disabled
 * during the test so the counter is deterministic.
 */
public class MappingServiceImplCacheTest {

    private static final String FIXTURES = "/grpc/";

    /**
     * Display path of the title node mapping as {@code findNodeMapping}
     * expects it (RecDefNode.toString() form), captured while building the
     * fixture mapping so the edit-path test never guesses the format.
     */
    private String titleNodePath;

    /**
     * Raises the EngineHolder reset threshold so the compilation counter keeps
     * counting (threshold 0 would disable counting entirely) but an automatic
     * engine reset can never zero it mid-test.
     */
    @BeforeEach
    public void disableEngineReset() {
        EngineHolder.setResetThreshold(1_000_000);
    }

    /**
     * Restores the production reset threshold so other tests in the same JVM
     * keep the Metaspace-protection behavior.
     */
    @AfterEach
    public void restoreEngineReset() {
        EngineHolder.setResetThreshold(100);
    }

    /**
     * Two identical requests must compile the Groovy mapping exactly once:
     * the second call is served from the compiled-mapping cache and the
     * engine's compilation counter must not advance.
     */
    @Test
    public void identicalMappingContentCompilesOnlyOnce() throws Exception {
        MappingServiceImpl service = new MappingServiceImpl("unused-base-path");
        SingleRecordRequest request = fixtureRequest("cache-test-1");

        CollectingObserver first = new CollectingObserver();
        service.mapRecord(request, first);
        assertMappedSuccessfully(first);
        int compilationsAfterFirst = EngineHolder.getCompilationCount();

        CollectingObserver second = new CollectingObserver();
        service.mapRecord(request, second);
        assertMappedSuccessfully(second);
        int compilationsAfterSecond = EngineHolder.getCompilationCount();

        assertEquals(compilationsAfterFirst, compilationsAfterSecond,
                "identical mapping content must reuse the compiled script");
    }

    /**
     * A request that adds an edit path to otherwise identical mapping content
     * must NOT be served from the cache: the edited Groovy alters the
     * generated code, so serving the cached script would silently return
     * stale, pre-edit results to the Mapper preview. The compilation counter
     * must advance for the edit-path call.
     */
    @Test
    public void editPathBustsTheCache() throws Exception {
        MappingServiceImpl service = new MappingServiceImpl("unused-base-path");
        SingleRecordRequest plain = fixtureRequest("cache-test-2");

        CollectingObserver first = new CollectingObserver();
        service.mapRecord(plain, first);
        assertMappedSuccessfully(first);
        int compilationsAfterPlain = EngineHolder.getCompilationCount();

        SingleRecordRequest edited = plain.toBuilder()
                .setEditPath(EditPath.newBuilder()
                        .setNodeMapping(titleNodePath)
                        .setGroovyCode("'Edited Title'")
                        .build())
                .build();

        CollectingObserver second = new CollectingObserver();
        service.mapRecord(edited, second);
        assertMappedSuccessfully(second);
        int compilationsAfterEdit = EngineHolder.getCompilationCount();

        assertEquals(compilationsAfterPlain + 1, compilationsAfterEdit,
                "an edit path changes the generated code and must trigger a fresh compilation");
    }

    /**
     * Builds a content-in-request mapping request, mirroring how Orchestra
     * sends mapping and record-definition content on every call.
     *
     * <p>The mapping is constructed programmatically against the
     * {@code rdf-root-recdef.xml} fixture (the same recdef the CodeGenerator
     * tests use) with a single constant node mapping, then serialized to XML —
     * guaranteeing the generated Groovy is current-production shape and
     * actually compiles, unlike the legacy codegen text-comparison fixtures.
     *
     * @param localRecordId the pocket id to wrap the record in
     * @return a fully populated request that needs no filesystem access
     */
    private SingleRecordRequest fixtureRequest(String localRecordId) throws Exception {
        String recDefXml = readFixture("rdf-root-recdef.xml");

        RecDefTree tree = RecDefTree.create(
                RecDef.read(new ByteArrayInputStream(recDefXml.getBytes(StandardCharsets.UTF_8))));
        RecDefNode title = tree.getRecDefNode(
                Path.create("/test:RDF/lrm:F3_Manifestation/crm:P102_has_title"));
        title.addNodeMapping(NodeMapping.forConstant("Cache Test Title"));
        titleNodePath = title.toString();

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "testorg");
        recMapping.setFact("spec", "cache-test");

        ByteArrayOutputStream mappingOut = new ByteArrayOutputStream();
        RecMapping.write(mappingOut, recMapping);
        String mappingXml = mappingOut.toString(StandardCharsets.UTF_8);

        return SingleRecordRequest.newBuilder()
                .setRecordXml("<record><title>anything</title></record>")
                .setLocalRecordId(localRecordId)
                .setMappingFile(mappingXml)
                .setRecordDefinition(recDefXml)
                .build();
    }

    /**
     * Asserts that the observer saw exactly one successful mapping result:
     * completed stream, no gRPC error, no application-level error payload,
     * and a non-empty mapped XML document.
     *
     * @param observer the observer that captured the service response
     */
    private void assertMappedSuccessfully(CollectingObserver observer) {
        assertTrue(observer.completed, "stream should be completed");
        assertEquals(1, observer.results.size(), "expected exactly one result");
        MappingResult result = observer.results.get(0);
        assertFalse(result.hasError(), "mapping must not return an error: "
                + (result.hasError() ? result.getError().getErrorMessage() : ""));
        assertNotNull(result.getMappedXml(), "mapped XML must be present");
        assertFalse(result.getMappedXml().isEmpty(), "mapped XML must not be empty");
    }

    /**
     * Reads a classpath fixture from {@code src/test/resources/grpc/} as UTF-8.
     *
     * @param name the fixture file name
     * @return the file content as a string
     */
    private String readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(FIXTURES + name)) {
            if (in == null) {
                throw new IOException("missing test fixture: " + FIXTURES + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Minimal synchronous {@link StreamObserver} that records everything the
     * service emits, so assertions can run after the call returns.
     */
    private static final class CollectingObserver implements StreamObserver<MappingResult> {
        final List<MappingResult> results = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(MappingResult value) {
            results.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
