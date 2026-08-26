/*
 * Copyright 2026 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 */

package eu.delving.sip.files;

import org.apache.jena.graph.Graph;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decision 2 of the recdef-semantics-generators follow-ups: when a schema
 * version ships no shacl.ttl, {@code StorageImpl}'s shape-loading path must
 * fall back to generating shapes on the fly from the dataset's own record
 * definition via {@link eu.delving.metadata.ShaclGenerator}, instead of
 * silently skipping SHACL validation.
 */
class StorageImplShaclFallbackTest {

    private static final String RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="1.0.0" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="tst:Thing" label="Thing">
                        <elem tag="tst:name" xsdDataType="xsd:string" required="true"/>
                    </elem>
                </root>
            </record-definition>
            """;

    @Test
    void newShapeFallsBackToGeneratedShapesWhenShaclFileMissing() throws Exception {
        File home = Files.createTempDirectory("sip-storage-test").toFile();
        StorageImpl storage = new StorageImpl(home, new Properties(), null, null);
        DataSet dataSet = storage.createDataSet("testset");

        // Minimal dataset facts: just enough for getSchemaVersion() to resolve.
        writeFile(new File(dataSet.getSipFile(), "narthex_facts.txt"), "schemaVersions=tst_1.0.0");
        // The record definition IS present, but tst_1.0.0_shacl.ttl deliberately is not.
        writeFile(new File(dataSet.getSipFile(), "tst_1.0.0_record-definition.xml"), RECDEF);

        Graph shape = dataSet.newShape();

        assertNotNull(shape, "expected shapes generated on the fly from the recdef, not null");
        assertFalse(shape.isEmpty(), "generated shape graph should contain at least the NodeShape/targetClass triples");
        assertTrue(shape.contains(
                org.apache.jena.graph.Node.ANY,
                org.apache.jena.graph.Node.ANY,
                org.apache.jena.graph.NodeFactory.createURI("http://example.org/tst#Thing")),
            "expected a triple pointing at the sh:targetClass of the recdef's own entity");
    }

    private static void writeFile(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
}
