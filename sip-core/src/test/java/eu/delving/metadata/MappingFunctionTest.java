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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MappingFunction code generation.
 */
class MappingFunctionTest {

    @Test
    void simpleFunctionWrapsWithItParameter() {
        MappingFunction function = new MappingFunction("testFunc");
        function.setGroovyCode("it.toString().toUpperCase()");

        CodeOut codeOut = new CodeOut();
        function.toCode(codeOut);

        String code = codeOut.toString();
        assertTrue(code.contains("def testFunc = { it ->"), 
            "Simple function should be wrapped in closure with 'it' parameter");
        assertTrue(code.contains("it.toString().toUpperCase()"), 
            "Simple function should contain the user code");
    }

    @Test
    void fullFunctionDefinitionUsesAsIs() {
        MappingFunction function = new MappingFunction("byLang");
        function.setGroovyCode("def byLang = { node, langs ->\n" +
            "    if (node == null) return null\n" +
            "    return node.toString()\n" +
            "}\n" +
            "byLang(it, 'nl')");

        CodeOut codeOut = new CodeOut();
        function.toCode(codeOut);

        String code = codeOut.toString();
        assertTrue(code.contains("def byLang = { node, langs ->"), 
            "Full function definition should be used as-is");
        assertTrue(code.contains("byLang(it, 'nl')"), 
            "Function call should still be added");
    }

    @Test
    void fullFunctionDefinitionNoDuplicateDef() {
        MappingFunction function = new MappingFunction("utmOut");
        function.setGroovyCode("def utmOut = false\n" +
            "String string = it.toString()\n" +
            "utmOut");

        CodeOut codeOut = new CodeOut();
        function.toCode(codeOut);

        String code = codeOut.toString();
        
        // Should only have ONE def utmOut, not duplicated
        int defCount = countOccurrences(code, "def utmOut");
        assertEquals(1, defCount, 
            "Function definition should appear exactly once, not be duplicated");
    }

    private int countOccurrences(String str, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
