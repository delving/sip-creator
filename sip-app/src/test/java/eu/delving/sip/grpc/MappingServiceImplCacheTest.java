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

    private int previousResetThreshold;

    /**
     * Raises the EngineHolder reset threshold so an automatic engine reset can
     * never zero the shared counter mid-test. The previous value is captured
     * so teardown restores whatever was configured, not a hardcoded guess.
     *
     * <p>These tests observe global static counters in {@link EngineHolder}
     * and therefore assume the sequential (default) surefire execution mode;
     * running test classes in parallel within one JVM would interleave the
     * counters.
     */
    @BeforeEach
    public void disableEngineReset() {
        previousResetThreshold = EngineHolder.getResetThreshold();
        EngineHolder.setResetThreshold(1_000_000);
    }

    /**
     * Restores the reset threshold captured before the test so other tests in
     * the same JVM keep the configured Metaspace-protection behavior.
     */
    @AfterEach
    public void restoreEngineReset() {
        EngineHolder.setResetThreshold(previousResetThreshold);
    }

    /**
     * Compiles that populate the bulk cache must run in their own isolated
     * engine: they must not advance the shared engine's reset counter, so
     * Mapper preview churn and bulk-cache compiles cannot interact. Also
     * guarantees an evicted cache entry releases its own classloader instead
     * of pinning a shared engine generation.
     */
    @Test
    public void cachedCompilesDoNotAdvanceSharedEngineResetCounter() throws Exception {
        MappingServiceImpl service = new MappingServiceImpl("unused-base-path");
        SingleRecordRequest request = fixtureRequest("cache-test-isolated");

        int sharedBefore = EngineHolder.getCompilationCount();
        CollectingObserver observer = new CollectingObserver();
        service.mapRecord(request, observer);
        assertMappedSuccessfully(observer);

        assertEquals(sharedBefore, EngineHolder.getCompilationCount(),
                "a cache-populating compile must use an isolated engine, not the shared one");
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
        long compilationsAfterFirst = EngineHolder.getTotalCompilationCount();

        CollectingObserver second = new CollectingObserver();
        service.mapRecord(request, second);
        assertMappedSuccessfully(second);
        long compilationsAfterSecond = EngineHolder.getTotalCompilationCount();

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
        long compilationsAfterPlain = EngineHolder.getTotalCompilationCount();

        SingleRecordRequest edited = plain.toBuilder()
                .setEditPath(EditPath.newBuilder()
                        .setNodeMapping(titleNodePath)
                        .setGroovyCode("'Edited Title'")
                        .build())
                .build();

        CollectingObserver second = new CollectingObserver();
        service.mapRecord(edited, second);
        assertMappedSuccessfully(second);
        long compilationsAfterEdit = EngineHolder.getTotalCompilationCount();

        assertEquals(compilationsAfterPlain + 1, compilationsAfterEdit,
                "an edit path changes the generated code and must trigger a fresh compilation");
    }

    /**
     * An edit-path request (Mapper live preview) must never populate the
     * compiled-mapping cache, and must not disturb entries already in it.
     * A preview compile is one-shot — every keystroke changes the Groovy —
     * so caching it would mint a new LRU entry per keystroke, evicting the
     * hot bulk-indexing runners and advancing {@code EngineHolder} toward
     * its periodic engine reset. Verified by observing that (a) the same
     * edit-path request sent twice compiles twice (nothing was cached), and
     * (b) a plain request cached before the edits is still served without
     * recompilation afterwards (nothing was evicted).
     */
    @Test
    public void editPathRequestsAreNeverCached() throws Exception {
        MappingServiceImpl service = new MappingServiceImpl("unused-base-path");
        SingleRecordRequest plain = fixtureRequest("cache-test-3");

        // Prime the cache with the plain (bulk-indexing shaped) request.
        CollectingObserver primed = new CollectingObserver();
        service.mapRecord(plain, primed);
        assertMappedSuccessfully(primed);
        long compilationsAfterPlain = EngineHolder.getTotalCompilationCount();

        SingleRecordRequest edited = plain.toBuilder()
                .setEditPath(EditPath.newBuilder()
                        .setNodeMapping(titleNodePath)
                        .setGroovyCode("'Edited Title'")
                        .build())
                .build();

        // The identical edit-path request twice: each call must compile
        // afresh, proving the first one put nothing into the cache.
        CollectingObserver firstEdit = new CollectingObserver();
        service.mapRecord(edited, firstEdit);
        assertMappedSuccessfully(firstEdit);
        CollectingObserver secondEdit = new CollectingObserver();
        service.mapRecord(edited, secondEdit);
        assertMappedSuccessfully(secondEdit);
        assertEquals(compilationsAfterPlain + 2, EngineHolder.getTotalCompilationCount(),
                "identical edit-path requests must each compile afresh: previews are never cached");

        // The plain request must still be a cache hit: the edit-path calls
        // must not have evicted it.
        CollectingObserver plainAgain = new CollectingObserver();
        service.mapRecord(plain, plainAgain);
        assertMappedSuccessfully(plainAgain);
        assertEquals(compilationsAfterPlain + 2, EngineHolder.getTotalCompilationCount(),
                "the previously cached plain mapping must survive edit-path traffic without recompilation");
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
