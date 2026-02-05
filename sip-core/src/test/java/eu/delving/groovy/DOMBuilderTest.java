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
import groovy.lang.Closure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DOMBuilder, particularly xml:lang validation.
 */
class DOMBuilderTest {

    private DOMBuilder builder;

    @BeforeEach
    void setUp() {
        InputStream recDefStream = getClass().getResourceAsStream("/codegen/TestCodeGeneration-recdef.xml");
        assertNotNull(recDefStream, "Test recdef file should exist");
        RecDef recDef = RecDef.read(recDefStream);
        builder = DOMBuilder.createFor(recDef);
    }

    /**
     * Helper to create a Groovy Closure that returns a constant value.
     */
    private Closure<String> closureReturning(final String value) {
        return new Closure<String>(this) {
            @Override
            public String call() {
                return value;
            }
        };
    }

    // ========== Valid BCP 47 language tags ==========

    @Test
    void testValidXmlLang_SimpleLanguage() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "en");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Hello")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_TwoCharLanguage() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "nl");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Hallo")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_LanguageWithRegion() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "en-US");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Hello")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_LanguageWithScript() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "zh-Hans");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_ComplexTag() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "de-AT");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_PortugueseBrazil() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "pt-BR");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        assertNotNull(result);
    }

    @Test
    void testValidXmlLang_ThreeCharLanguage() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "und");  // undetermined language
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        assertNotNull(result);
    }

    // ========== Invalid BCP 47 language tags ==========

    @Test
    void testInvalidXmlLang_TextContent() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "John Donne, Elegie XIX: To his Mistris going to Bed");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Some content")});
        });

        assertTrue(exception.getMessage().contains("Invalid xml:lang"),
            "Error message should mention 'Invalid xml:lang'");
        assertTrue(exception.getMessage().contains("BCP 47"),
            "Error message should mention BCP 47");
    }

    @Test
    void testInvalidXmlLang_LongTextTruncatedInError() {
        Map<String, Object> attrs = new HashMap<>();
        String longText = "This is a very long text that should be truncated in the error message for readability";
        attrs.put("xml:lang", longText);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Some content")});
        });

        assertTrue(exception.getMessage().contains("..."),
            "Error message should contain truncation indicator '...'");
    }

    @Test
    void testInvalidXmlLang_NumericOnly() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "123");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().contains("Invalid xml:lang"),
            "Error message should mention 'Invalid xml:lang'");
    }

    @Test
    void testInvalidXmlLang_SpecialCharacters() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "@#$%");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().contains("Invalid xml:lang"),
            "Error message should mention 'Invalid xml:lang'");
    }

    @Test
    void testInvalidXmlLang_SingleChar() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "e");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().contains("Invalid xml:lang"),
            "Error message should mention 'Invalid xml:lang'");
    }

    @Test
    void testInvalidXmlLang_TooLongPrimarySubtag() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "english");  // primary subtag should be 2-3 chars

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().contains("Invalid xml:lang"),
            "Error message should mention 'Invalid xml:lang'");
    }

    @Test
    void testInvalidXmlLang_Empty() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().toLowerCase().contains("empty"),
            "Error message should mention empty");
    }

    // ========== Empty content with xml:lang ==========

    @Test
    void testEmptyContentWithXmlLang() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "en");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("")});
        });

        assertTrue(exception.getMessage().contains("no content"),
            "Error message should mention 'no content'");
        assertTrue(exception.getMessage().contains("en"),
            "Error message should show the lang tag");
    }

    @Test
    void testWhitespaceOnlyContentWithXmlLang() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "nl");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("   ")});
        });

        assertTrue(exception.getMessage().contains("no content"),
            "Error message should mention 'no content'");
    }

    @Test
    void testNullContentWithXmlLang() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "de");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning(null)});
        });

        assertTrue(exception.getMessage().contains("no content"),
            "Error message should mention 'no content'");
    }

    // ========== Elements without xml:lang should work fine ==========

    @Test
    void testElementWithoutXmlLang_EmptyContent() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("test:a", "test");
        // No xml:lang, so empty content should be allowed
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("")});
        assertNotNull(result);
    }

    @Test
    void testElementWithoutXmlLang_NormalContent() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("test:a", "test");
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("content")});
        assertNotNull(result);
    }

    @Test
    void testElementNoAttributes() {
        Node result = (Node) builder.invokeMethod("test:element", new Object[]{closureReturning("content")});
        assertNotNull(result);
    }

    // ========== Error message quality ==========

    @Test
    void testErrorMessageContainsElementName() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "en");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:myElement", new Object[]{attrs, closureReturning("")});
        });

        assertTrue(exception.getMessage().contains("myElement"),
            "Error message should contain element name");
    }

    @Test
    void testErrorMessageSuggestsValidExamples() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("xml:lang", "invalid-lang-value-here");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            builder.invokeMethod("test:element", new Object[]{attrs, closureReturning("Content")});
        });

        assertTrue(exception.getMessage().contains("'en'") ||
            exception.getMessage().contains("'nl'") ||
            exception.getMessage().contains("en-US"),
            "Error message should suggest valid examples");
    }
}
