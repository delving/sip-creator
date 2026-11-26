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

package eu.delving.groovy;

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.script.ScriptException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a record sees of the script run in {@link BulkMappingRunner}: its
 * result, the exceptions callers branch on, and a binding of its own.
 */
class BulkMappingRunnerEvalTest {

    private static final String RETURN_NODE =
            "groovy.xml.DOMBuilder.newInstance().parseText('<foo><bar>x</bar></foo>')";

    private RecMapping recMapping;
    private MetadataRecord record;

    @BeforeEach
    void before() {
        recMapping = mock(RecMapping.class);
        RecDefTree recDefTree = mock(RecDefTree.class);
        RecDef recDef = mock(RecDef.class);
        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);
        when(recDef.getNamespaceMap()).thenReturn(new HashMap<>());
        record = mock(MetadataRecord.class);
        when(record.getRootNode()).thenReturn(new GroovyNode(null, "input"));
    }

    private Node run(String code) throws MappingException {
        return new BulkMappingRunner(recMapping, code).runMapping(record);
    }

    @Test
    void returnsTheScriptResult() throws MappingException {
        Node node = run(RETURN_NODE);
        assertEquals("x", node.getFirstChild().getTextContent());
    }

    @Test
    void readsWorldFromTheBinding() throws MappingException {
        Node node = run("assert WORLD.input.size() == 1 && WORLD._facts[0] != null\n" + RETURN_NODE);
        assertNotNull(node);
    }

    @Test
    void discardSurfacesAsDiscardRecordException() {
        assertThrows(DiscardRecordException.class,
                () -> run("throw new eu.delving.groovy.DiscardRecordException('no')"));
    }

    @Test
    void runtimeFailureIsWrappedAsJsr223Did() {
        MappingException e = assertThrows(MappingException.class, () -> run("null.foo()"));
        assertInstanceOf(ScriptException.class, e.getCause());
        assertInstanceOf(NullPointerException.class, e.getCause().getCause());
    }

    @Test
    void compileFailureIsACompilationError() {
        MappingException e = assertThrows(MappingException.class, () -> run("def x = "));
        assertEquals(MappingException.ErrorType.COMPILATION, e.getErrorType());
        assertInstanceOf(ScriptException.class, e.getCause());
    }

    @Test
    void everyRecordGetsAFreshBindingAndInstance() throws MappingException {
        BulkMappingRunner runner = new BulkMappingRunner(recMapping,
                "if (binding.hasVariable('seen')) throw new IllegalStateException('leaked')\n"
                        + "seen = true\n" + RETURN_NODE);
        runner.runMapping(record);
        assertNotNull(runner.runMapping(record));
    }

    @Test
    void categoryMethodsNeedTheScopeTheCallerOpens() throws MappingException {
        // List * Closure is MappingCategory's; the code here does not enter it itself
        BulkMappingRunner runner = new BulkMappingRunner(recMapping,
                "assert (['a', 'b'] * { it.toUpperCase() }) == ['A', 'B']\n" + RETURN_NODE);
        assertThrows(MappingException.class, () -> runner.runMapping(record));
        Node[] result = new Node[2];
        runner.runInMappingCategory(() -> {
            try {
                result[0] = runner.runMapping(record);
                result[1] = runner.runMapping(record);
            } catch (MappingException e) {
                throw new RuntimeException(e);
            }
        });
        assertNotNull(result[0]);
        assertNotNull(result[1]);
    }
}
