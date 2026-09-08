/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdGeneratorTest {

    private static final String RECDEF = """
            <?xml version="1.0"?>
            <record-definition prefix="tst" version="1.0.0" flat="false">
                <namespaces>
                    <namespace prefix="tst" uri="http://example.org/tst#" schema="s"/>
                    <namespace prefix="xsd" uri="http://www.w3.org/2001/XMLSchema#" schema="s"/>
                </namespaces>
                <root tag="rdf:RDF">
                    <elem tag="tst:Thing" label="Thing">
                        <elem tag="tst:name" xsdDataType="xsd:string" required="true"/>
                        <elem tag="tst:code" xsdDataType="xsd:string" xsdPattern="[A-Z]+"/>
                    </elem>
                </root>
            </record-definition>
            """;

    @Test
    void rdfStyleXsdPrefixIsRewrittenToTheSchemaPrefix() throws Exception {
        RecDef recDef = RecDef.read(new ByteArrayInputStream(RECDEF.getBytes(StandardCharsets.UTF_8)));

        String xsd = XsdGenerator.generate(recDef);

        assertTrue(xsd.contains("type=\"xs:string\""), "xsd:string must become xs:string, got:\n" + xsd);
        assertTrue(xsd.contains("base=\"xs:string\""), "pattern base must become xs:string, got:\n" + xsd);
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        assertDoesNotThrow(() -> factory.newSchema(new StreamSource(new StringReader(xsd))),
                "generated XSD must be loadable by the JDK schema factory");
    }
}
