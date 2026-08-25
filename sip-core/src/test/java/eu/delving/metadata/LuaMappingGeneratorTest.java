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

package eu.delving.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Task 8 of the Lua mapping-engine feasibility spike: mapping XML -&gt;
 * {@code mapping.lua}.
 *
 * <p>Runs against the committed golden cases in {@code lua-poc/golden/mappings}
 * so the generator is pinned against real corpus mappings, not synthetic ones.
 */
public class LuaMappingGeneratorTest {

    private static java.nio.file.Path caseDir(String name) {
        // Surefire's working directory is the module directory (sip-core/).
        java.nio.file.Path fromModule = Paths.get("..", "lua-poc", "golden", "mappings", name);
        if (Files.isDirectory(fromModule)) return fromModule;
        return Paths.get("lua-poc", "golden", "mappings", name);
    }

    private static String generate(String name) throws Exception {
        return LuaMappingGenerator.fromCaseDirectory(caseDir(name));
    }

    @Test
    public void emitsABuilderBackedRecordFunction() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        assertTrue(lua.contains("require(\"builder\")"), lua);
        assertTrue(lua.contains("require(\"stdlib\")"), lua);
        assertTrue(lua.contains("require(\"node\")"), lua);
        assertTrue(lua.contains("builder.new(NAMESPACES)"), lua);
        assertTrue(lua.contains("local _input = node.parse(record_xml)"), lua);
        assertTrue(lua.contains("return b:to_rdfxml()"), lua);
    }

    @Test
    public void emitsOneElemCallPerMappedOutputNode() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        // rdf:RDF, ore:Aggregation, edm:aggregatedCHO, edm:isShownBy, edm:object,
        // edm:rights, edm:ProvidedCHO, dc:creator, dc:description, dc:identifier,
        // dc:title, dcterms:medium, nave:DcnResource, nave:material
        assertEquals(14, countOccurrences(lua, "b:elem("), lua);
        for (String qname : new String[] {
                "rdf:RDF", "ore:Aggregation", "edm:aggregatedCHO", "edm:isShownBy", "edm:object",
                "edm:rights", "edm:ProvidedCHO", "dc:creator", "dc:description", "dc:identifier",
                "dc:title", "dcterms:medium", "nave:DcnResource", "nave:material" }) {
            assertTrue(lua.contains("b:elem(\"" + qname + "\""), "missing b:elem for " + qname + "\n" + lua);
        }
    }

    @Test
    public void inlinesConvertedSnippetsAtTheirBuilderCall() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        // "${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}"
        assertTrue(lua.contains("stdlib.to_text(stdlib.sanitizeURI(_uniqueIdentifier))"), lua);
        assertTrue(lua.contains("/resource/aggregation/"), lua);
        // attribute closures take (b) only; content closures take (b, el)
        assertTrue(lua.contains("[\"rdf:about\"] = function(b)"), lua);
        assertTrue(lua.contains("function(b, el)"), lua);
    }

    @Test
    public void emitsNavigationLoopsForNestedInputPaths() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        assertTrue(lua.contains("for _, _csvrecord in ipairs(_input:get(\"csvrecord\")) do"), lua);
        assertTrue(lua.contains("for _, _maker in ipairs(_csvrecord:get(\"maker\")) do"), lua);
    }

    @Test
    public void constantMappingsBecomeLuaStringLiterals() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        assertTrue(lua.contains("\"http://www.europeana.eu/portal/nl/rights/rr-f\""), lua);
    }

    @Test
    public void declaresRecDefNamespacesForTheRootElement() throws Exception {
        String lua = generate("nationaal-gevangenismuseum");
        assertTrue(lua.contains("[\"edm\"] = \"http://www.europeana.eu/schemas/edm/\""), lua);
    }

    @Test
    public void tupleMergeMappingsBecomeTupleHelperLoops() throws Exception {
        String lua = generate("universiteitsmuseum-groningen");
        assertTrue(lua.contains("_tuple("), lua);
        assertTrue(lua.contains("_tget("), lua);
    }

    // --------------------------------------------------------- hard failures

    @Test
    public void userFunctionCallHardFailsNamingTheFunction() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> generate("coll-schraven"));
        assertEquals("MethodCall:reverseNames", e.getConstructName());
    }

    @Test
    public void closureSnippetHardFailsNamingTheClosure() {
        UnsupportedConstructException e = assertThrows(UnsupportedConstructException.class,
                () -> generate("register-kerkelijke-ensembles"));
        assertEquals("ClosureExpression", e.getConstructName());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
