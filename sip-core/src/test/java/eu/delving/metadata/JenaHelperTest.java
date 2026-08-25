/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import eu.delving.groovy.XmlSerializer;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JenaHelperTest {

    private static final String SAMPLE_RDF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                    <rdf:type rdf:resource="http://example.org/Thing"/>
                </rdf:Description>
            </rdf:RDF>
            """;

    private static Map<String, Object> createThingFrame() {
        Map<String, Object> frame = new HashMap<>();
        frame.put("@type", "Thing");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("@base", "http://example.org/");
        frame.put("@context", ctx);
        return frame;
    }

    @Test
    void shouldProduceFramedJsonLd() {
        Map<String, Object> frame = createThingFrame();

        String framed = JenaHelper.convertRDF("ex", SAMPLE_RDF, RDFFormat.JSONLD_FRAME_PRETTY, frame);

        assertNotNull(framed);
        assertTrue(framed.contains("@graph"));
        assertTrue(framed.contains("Test Item"));
        assertTrue(framed.contains("@type"));
        assertTrue(framed.contains("Thing"));
    }

    @Test
    void framedDiffersFromCompact() {
        Map<String, Object> frame = createThingFrame();

        String compact = JenaHelper.convertRDF("ex", SAMPLE_RDF, RDFFormat.JSONLD_COMPACT_PRETTY);
        String framed = JenaHelper.convertRDF("ex", SAMPLE_RDF, RDFFormat.JSONLD_FRAME_PRETTY, frame);

        assertNotEquals(compact, framed);
    }

    @Test
    public void compactWithGeneratedContextUsesShortTerms() {
        String context = """
            {"@context": {"ex": "http://example.org/",
                          "name": {"@id": "http://example.org/name"}}}
            """;
        String compact = JenaHelper.convertRDF("ex", SAMPLE_RDF,
            RDFFormat.JSONLD_COMPACT_PRETTY, context, null);
        assertTrue(compact.contains("\"name\""), compact);
        assertFalse(compact.contains("http://example.org/name"), compact);
    }

    @Test
    public void frameStringDrivesFraming() {
        String frame = """
            {"@context": {"ex": "http://example.org/"},
             "@type": "http://example.org/Thing"}
            """;
        String framed = JenaHelper.convertRDF("ex", SAMPLE_RDF,
            RDFFormat.JSONLD_FRAME_PRETTY, null, frame);
        assertTrue(framed.contains("item1"), framed);
    }

    @Test
    public void frameWithBothContextAndFrameStillFramesAndDoesNotThrow() {
        // This is the combination MappingCompileModel actually produces for
        // JSONLD_FRAME_PRETTY: it always generates a contextJson, and additionally
        // a frameJson. The frame branch ignores JSONLD_CONTEXT, so passing a
        // non-null contextJson alongside frameJson must remain harmless.
        String context = """
            {"@context": {"ex": "http://example.org/",
                          "name": {"@id": "http://example.org/name"}}}
            """;
        String frame = """
            {"@context": {"ex": "http://example.org/"},
             "@type": "http://example.org/Thing"}
            """;
        String framed = JenaHelper.convertRDF("ex", SAMPLE_RDF,
            RDFFormat.JSONLD_FRAME_PRETTY, context, frame);
        assertTrue(framed.contains("item1"), framed);
        assertTrue(framed.contains("@graph"), framed);
        assertTrue(framed.contains("Thing"), framed);
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

    @Nested
    class RdfXmlValidation {

        @Test
        void validTypedChildElementShouldParseWithoutError() {
            String rdf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:nkc="http://example.org/nkc/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <rdf:Description rdf:about="http://example.org/cho/1">
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Bruyn (2), N. De</nkc:creatorName>
                                <nkc:dateOfBirth>1570</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1656</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Vinckboons (I), David</nkc:creatorName>
                                <nkc:dateOfBirth>1576-08</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1633</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <dc:title xml:lang="nl">Some artwork</dc:title>
                    </rdf:Description>
                </rdf:RDF>
                """;

            String error = MappingResult.hasRDFError(rdf);

            assertEquals("", error, "Valid RDF/XML with typed child elements should parse without errors");
        }

        @Test
        void multipleTypedChildrenInOnePropertyShouldBeDetectedAsError() {
            String rdf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:nkc="http://example.org/nkc/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <rdf:Description rdf:about="http://example.org/cho/1">
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Bruyn (2), N. De</nkc:creatorName>
                                <nkc:dateOfBirth>1570</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1656</nkc:dateOfDeath>
                            </nkc:Creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Vinckboons (I), David</nkc:creatorName>
                                <nkc:dateOfBirth>1576-08</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1633</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <dc:title xml:lang="nl">Kermis</dc:title>
                    </rdf:Description>
                </rdf:RDF>
                """;

            String error = MappingResult.hasRDFError(rdf);

            assertFalse(error.isEmpty(),
                    "Multiple typed child elements in one property should be detected as invalid RDF/XML");
        }

        @Test
        void multipleTypedChildrenWithRdfRootShouldBeDetectedAsError() {
            // XmlSerializer already rewrites root to rdf:RDF (line 90-93 of XmlSerializer.java)
            // So the XML that toRDF() produces has rdf:RDF as root
            String rdf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:nkc="https://wo2.collectienederland.nl/nk/terms/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <nkc:CHO rdf:about="https://wo2.collectienederland.nl/id/cho/snk-12673">
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Bruyn (2), N. De</nkc:creatorName>
                                <nkc:dateOfBirth>1570</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1656</nkc:dateOfDeath>
                            </nkc:Creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Vinckboons (I), David</nkc:creatorName>
                                <nkc:dateOfBirth>1576-08</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1633</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <dc:title xml:lang="nl">Kermis</dc:title>
                    </nkc:CHO>
                </rdf:RDF>
                """;

            // XmlSerializer already rewrites root to rdf:RDF.
            // toJenaCompliantRDF then tries to re-add xmlns:rdf but may fail
            // depending on whitespace in the serialized output.
            // Test the direct case (without toJenaCompliantRDF):
            String directError = MappingResult.hasRDFError(rdf);

            assertFalse(directError.isEmpty(),
                    "Multiple typed children should be detected as invalid RDF/XML");
        }

        @Test
        void shouldShowContextInErrorMessage() {
            String rdf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:nkc="https://wo2.collectienederland.nl/nk/terms/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <nkc:CHO rdf:about="https://wo2.collectienederland.nl/id/cho/snk-12673">
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Bruyn (2), N. De</nkc:creatorName>
                            </nkc:Creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Vinckboons (I), David</nkc:creatorName>
                            </nkc:Creator>
                        </nkc:creator>
                    </nkc:CHO>
                </rdf:RDF>
                """;

            String error = MappingResult.hasRDFError(rdf);

            assertFalse(error.isEmpty(), "Should detect E201");
            assertTrue(error.contains(">>>"), "Should contain context marker");
            assertTrue(error.contains("nkc:Creator"), "Should show the offending element in context");
            assertTrue(error.contains("Context:"), "Should contain Context section");
        }

        @Test
        void emptyRdfRootShouldKeepRdfNamespaceAfterJenaRewrite() {
            String rdf = MappingResult.toJenaCompliantRDF("ace", """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF>
                </rdf:RDF>
                """);

            String error = MappingResult.hasRDFError(rdf);

            assertEquals("", error, "Empty rdf:RDF root should retain a bound rdf namespace");
            assertTrue(rdf.contains("xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""),
                "Rewritten RDF should contain the rdf namespace declaration");
        }

        @Test
        void multipleTypedChildrenViaDomSerializerShouldBeDetectedAsError() throws Exception {
            // Build a DOM that mimics what DOMBuilder produces
            String NKC = "https://wo2.collectienederland.nl/nk/terms/";
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element cho = doc.createElementNS(NKC, "nkc:CHO");
            cho.setAttributeNS(RDF, "rdf:about", "http://example.org/cho/1");
            root.appendChild(cho);

            Element creator = doc.createElementNS(NKC, "nkc:creator");
            cho.appendChild(creator);

            // First Creator child
            Element creator1 = doc.createElementNS(NKC, "nkc:Creator");
            creator.appendChild(creator1);
            Element name1 = doc.createElementNS(NKC, "nkc:creatorName");
            name1.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:lang", "nl");
            name1.setTextContent("Bruyn (2), N. De");
            creator1.appendChild(name1);

            // Second Creator child — this makes it invalid RDF/XML
            Element creator2 = doc.createElementNS(NKC, "nkc:Creator");
            creator.appendChild(creator2);
            Element name2 = doc.createElementNS(NKC, "nkc:creatorName");
            name2.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:lang", "nl");
            name2.setTextContent("Vinckboons (I), David");
            creator2.appendChild(name2);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Kermis");
            cho.appendChild(title);

            // Serialize via XmlSerializer (same as MappingResult.toXml())
            XmlSerializer serializer = new XmlSerializer();
            String xml = serializer.toXml(root, true);

            // This is what toRDF() does
            String rdf = MappingResult.toJenaCompliantRDF("nkc", xml);

            String error = MappingResult.hasRDFError(rdf);

            assertFalse(error.isEmpty(),
                    "Multiple typed children via DOM+XmlSerializer should be detected as invalid RDF/XML");
        }

        @Test
        void multipleTypedChildrenViaToJenaCompliantRDFShouldBeDetectedAsError() {
            // Simulate what toRDF() produces: root is nkc:RDF, then
            // toJenaCompliantRDF rewrites it to rdf:RDF
            String rawXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <nkc:RDF xmlns:nkc="https://wo2.collectienederland.nl/nk/terms/"
                         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:dc="http://purl.org/dc/elements/1.1/"
                         xmlns:edm="http://www.europeana.eu/schemas/edm/">
                    <nkc:CHO rdf:about="https://wo2.collectienederland.nl/id/cho/snk-12673">
                        <nkc:creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Bruyn (2), N. De</nkc:creatorName>
                                <nkc:dateOfBirth>1570</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1656</nkc:dateOfDeath>
                            </nkc:Creator>
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Vinckboons (I), David</nkc:creatorName>
                                <nkc:dateOfBirth>1576-08</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1633</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <dc:title xml:lang="nl">Kermis</dc:title>
                    </nkc:CHO>
                </nkc:RDF>
                """;

            // This is what toRDF() does
            String rdf = MappingResult.toJenaCompliantRDF("nkc", rawXml);

            String error = MappingResult.hasRDFError(rdf);

            assertFalse(error.isEmpty(),
                    "Multiple typed children should be detected even after toJenaCompliantRDF transformation");
        }

        @Test
        void relativeUriInRdfAboutShouldBeDetectedAsError() throws Exception {
            // Build a DOM with rdf:about="123" — a relative URI
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "123");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertFalse(errors.isEmpty(),
                "Relative URI '123' in rdf:about should be detected as an error");
            assertTrue(errors.get(0).contains("relative URI"),
                "Error message should mention 'relative URI', got: " + errors.get(0));
        }

        @Test
        void absoluteUriInRdfAboutShouldNotBeError() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "http://example.org/item/1");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertTrue(errors.isEmpty(),
                "Absolute URI should not produce errors, got: " + errors);
        }

        @Test
        void mappingResultShouldUseConstructorFactsForGraphComment() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "http://example.org/item/1");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            Map<String, String> facts = new HashMap<>();
            facts.put("orgId", "datahub");
            facts.put("spec", "koha-test");

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null, facts);

            assertTrue(result.toXml().contains("<urn:datahub_koha-test_test-1/graph__"),
                "Default serialization should use facts supplied to MappingResult");
        }

        @Test
        void nestedBlankNodeInRdfAboutShouldNotBeError() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "http://example.org/item/1");
            root.appendChild(desc);

            Element relation = doc.createElementNS(DC, "dc:relation");
            desc.appendChild(relation);

            Element nested = doc.createElementNS(RDF, "rdf:Description");
            nested.setAttributeNS(RDF, "rdf:about", "_:b0");
            relation.appendChild(nested);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            nested.appendChild(title);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertTrue(errors.isEmpty(),
                "Nested blank node URI should not produce errors, got: " + errors);
        }

        @Test
        void topLevelRdfResourceWithoutRdfAboutShouldBeError() throws Exception {
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(NKC, "nkc:CHO");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertFalse(errors.isEmpty(),
                "Top-level RDF resource without rdf:about should produce an error");
            assertTrue(errors.get(0).contains("no top-level resource with a non-blank rdf:about"),
                "Error message should mention missing top-level subject, got: " + errors.get(0));
        }

        @Test
        void topLevelBlankNodeSubjectShouldBeError() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "_:b0");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertFalse(errors.isEmpty(),
                "Top-level blank node subject should produce an error");
            assertTrue(errors.get(0).contains("no top-level resource with a non-blank rdf:about"),
                "Error message should mention missing top-level subject, got: " + errors.get(0));
        }

        @Test
        void anonymousTopLevelExtensionShouldBeAllowedWhenDocumentHasNamedSubject() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().newDocument();

            Element root = doc.createElementNS(NKC, "nkc:RDF");
            doc.appendChild(root);

            Element desc = doc.createElementNS(RDF, "rdf:Description");
            desc.setAttributeNS(RDF, "rdf:about", "http://example.org/item/1");
            root.appendChild(desc);

            Element title = doc.createElementNS(DC, "dc:title");
            title.setTextContent("Test");
            desc.appendChild(title);

            Element extension = doc.createElementNS(NKC, "nkc:Extension");
            root.appendChild(extension);

            Element note = doc.createElementNS(NKC, "nkc:note");
            note.setTextContent("Extra top-level metadata");
            extension.appendChild(note);

            XmlSerializer serializer = new XmlSerializer();
            MappingResult result = new MappingResult(serializer, "test-1", root, null);

            List<String> errors = result.getRDFErrors();

            assertTrue(errors.isEmpty(),
                "Anonymous top-level extension should be allowed when another top-level resource is named, got: "
                    + errors);
        }

        @Test
        void commonPidSchemesShouldNotBeErrors() throws Exception {
            String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
            String DC = "http://purl.org/dc/elements/1.1/";
            String NKC = "http://example.org/nkc/";

            String[] validUris = {
                "https://example.org/item/1",
                "http://example.org/item/1",
                "urn:isbn:0451450523",
                "urn:nbn:nl:ui:13-abc-123",
                "ark:/12345/bcd6789",
                "doi:10.1000/xyz123",
                "hdl:loc.music/mushrs.8000",
            };

            for (String uri : validUris) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document doc = dbf.newDocumentBuilder().newDocument();

                Element root = doc.createElementNS(NKC, "nkc:RDF");
                doc.appendChild(root);

                Element desc = doc.createElementNS(RDF, "rdf:Description");
                desc.setAttributeNS(RDF, "rdf:about", uri);
                root.appendChild(desc);

                Element title = doc.createElementNS(DC, "dc:title");
                title.setTextContent("Test");
                desc.appendChild(title);

                XmlSerializer serializer = new XmlSerializer();
                MappingResult result = new MappingResult(serializer, "test-1", root, null);

                List<String> errors = result.getRDFErrors();

                assertTrue(errors.isEmpty(),
                    "URI scheme '" + uri + "' should be accepted, got: " + errors);
            }
        }

        @Test
        void parseTypeResourceWithTypedChildShouldBeDetectedAsError() {
            String rdf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:nkc="http://example.org/nkc/"
                         xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <rdf:Description rdf:about="http://example.org/cho/1">
                        <nkc:creator rdf:parseType="Resource">
                            <nkc:Creator>
                                <nkc:creatorName xml:lang="nl">Achenbach, A.</nkc:creatorName>
                                <nkc:dateOfBirth>1815-09-29</nkc:dateOfBirth>
                                <nkc:dateOfDeath>1910</nkc:dateOfDeath>
                            </nkc:Creator>
                        </nkc:creator>
                        <dc:title xml:lang="nl">Zeegezicht</dc:title>
                    </rdf:Description>
                </rdf:RDF>
                """;

            String error = MappingResult.hasRDFError(rdf);

            assertFalse(error.isEmpty(),
                    "rdf:parseType='Resource' with typed child element should be detected as invalid RDF/XML");
            assertTrue(error.contains("E202") || error.contains("parseType") || error.contains("not allowed"),
                    "Error should mention the RDF parsing problem, got: " + error);
        }
    }
}
