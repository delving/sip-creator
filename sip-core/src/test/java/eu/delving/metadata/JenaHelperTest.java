/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JenaHelperTest {

    @Test
    void shouldProduceFramedJsonLd() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                    <rdf:type rdf:resource="http://example.org/Thing"/>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        Map<String, Object> frame = new HashMap<>();
        frame.put("@type", "Thing");
        
        String framed = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_FRAME_PRETTY, frame);
        
        assertNotNull(framed);
        assertTrue(framed.contains("@graph"));
        assertTrue(framed.contains("Test Item"));
    }

    @Test
    void framedDiffersFromCompact() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                    <rdf:type rdf:resource="http://example.org/Thing"/>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        Map<String, Object> frame = new HashMap<>();
        frame.put("@type", "Thing");
        
        String compact = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_COMPACT_PRETTY);
        String framed = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_FRAME_PRETTY, frame);
        
        assertNotEquals(compact, framed);
    }

    @Test
    void shouldReturnCompactJsonLdWhenNoFrame() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        String result = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_COMPACT_PRETTY);
        
        assertNotNull(result);
        assertTrue(result.contains("Test Item"));
    }
}
