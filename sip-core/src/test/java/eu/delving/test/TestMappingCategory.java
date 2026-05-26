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

package eu.delving.test;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMappingCategory {

    private static Class<?> mappingCategoryClass;

    @BeforeAll
    static void loadMappingCategory() throws Exception {
        // Mirror how EngineHolder loads the category: parse via GroovyClassLoader
        try (InputStream in = TestMappingCategory.class.getResourceAsStream("/MappingCategory.groovy")) {
            String code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            GroovyClassLoader gcl = new GroovyClassLoader(TestMappingCategory.class.getClassLoader());
            mappingCategoryClass = gcl.parseClass(code);
        }
    }

    @Test
    public void sanitizeURNReplacesColonsAndCollapsesDashes() throws Exception {
        assertEquals("c-lvd-94", evalSanitizeURN("c:lvd:94"));
        assertEquals("a-b", evalSanitizeURN("a::b"));
        assertEquals("path-with-spaces", evalSanitizeURN("path with spaces"));
        assertEquals("under-score", evalSanitizeURN("under_score"));
        assertEquals("a-b-c", evalSanitizeURN("a/b/c"));
        assertEquals("clean-id", evalSanitizeURN("clean-id"));
    }

    @Test
    public void sanitizeURIIsUnchangedByAdditionOfSanitizeURN() throws Exception {
        // Existing percent-encoding behavior must still work after adding sanitizeURN
        assertEquals("a%20b", evalSanitizeURI("a b"));
        assertEquals("c:lvd:94", evalSanitizeURI("c:lvd:94"));
    }

    private String evalSanitizeURN(String input) throws Exception {
        return (String) runUnderCategory("input.sanitizeURN()", input);
    }

    private String evalSanitizeURI(String input) throws Exception {
        return (String) runUnderCategory("input.sanitizeURI()", input);
    }

    private Object runUnderCategory(String expression, String input) throws Exception {
        // Build a shell whose class-loader already knows MappingCategory
        GroovyClassLoader gcl = new GroovyClassLoader(mappingCategoryClass.getClassLoader());
        GroovyShell shell = new GroovyShell(gcl);
        shell.setVariable("input", input);
        return shell.evaluate("use(MappingCategory) { " + expression + " }");
    }
}
