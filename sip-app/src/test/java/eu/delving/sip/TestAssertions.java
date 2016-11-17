/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.XMLToolFactory;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.Assertion;
import eu.delving.metadata.AssertionTest;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.StructureTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Try out the assertion mechanism
 *
 *
 */
public class TestAssertions {

    private GroovyCodeResource groovyCodeResource;
    private Document modsDoc, icnDoc;
    private DocContext modsContext;

    @Before
    public void prep() throws ParserConfigurationException, IOException, SAXException, XPathFactoryConfigurationException {
        groovyCodeResource = new GroovyCodeResource(ClassLoader.getSystemClassLoader());
        modsDoc = parseDoc("mods.xml");
        icnDoc = parseDoc("icn.xml");
        modsContext = new DocContext(modsDoc);

    }

    @Test
    public void testGeneratedStructureTestsICN() throws Exception {
        URI uri = TestAssertions.class.getClassLoader().getSystemResource("test/icn/icn_1.0.3_record.xml").toURI();
        java.nio.file.Path path = Paths.get(uri);
        byte[] bytes = Files.readAllBytes(path);
        String schemaText = new String(bytes, "UTF-8");
        RecDef icnRecDef = RecDef.read(new ByteArrayInputStream(schemaText.getBytes()));
        List<StructureTest> structureTests = StructureTest.listFrom(icnRecDef);
        for (StructureTest structureTest : structureTests) {
            switch (structureTest.getViolation(icnDoc.getDocumentElement())) {
                case NONE:
//                    System.out.println("OK: " + structureTest);
                    break;
                default:
                    Assert.fail();
                    break;
            }
        }
    }

    @Test
    public void testGeneratedStructureTests() throws Exception {
        URI uri = TestAssertions.class.getClassLoader().getSystemResource("test/mods/record_def_3.4.0.xml").toURI();
        java.nio.file.Path path = Paths.get(uri);
        byte[] bytes = Files.readAllBytes(path);
        String schemaText = new String(bytes, "UTF-8");
        RecDef modsRecDef = RecDef.read(new ByteArrayInputStream(schemaText.getBytes()));
        List<StructureTest> structureTests = StructureTest.listFrom(modsRecDef);
        for (StructureTest structureTest : structureTests) {
            switch (structureTest.getViolation(modsDoc)) {
                case NONE:
//                    System.out.println("OK: " + structureTest);
                    break;
                default:
                    Assert.fail();
                    break;
            }
        }
    }

    @Test
    public void testStructure() throws XPathExpressionException {
        StructureTest.Factory factory = new StructureTest.Factory(modsContext);
        StructureTest structureTest = factory.create(Path.create("/mods:mods/mods:name/mods:namePart"), true, true);
        Assert.assertEquals("Unexpected Violation", StructureTest.Violation.NONE, structureTest.getViolation(modsDoc));
//        System.out.println(structureTest + ": " + structureTest.getViolation(modsDoc));
        structureTest = factory.create(Path.create("/mods:mods/mods:subject/@authority"), true, false);
        Assert.assertEquals("Unexpected Violation", StructureTest.Violation.NONE, structureTest.getViolation(modsDoc));
//        System.out.println(structureTest + ": " + structureTest.getViolation(modsDoc));
    }

    @Test
    public void testAssertions() throws XPathExpressionException {
        URL assertionResource = getClass().getResource("/assertion/assertion-list.xml");
        File assertionFile = new File(assertionResource.getFile());
        Assertion.AssertionList assertionList = (Assertion.AssertionList) getStream().fromXML(assertionFile);
        AssertionTest.Factory factory = new AssertionTest.Factory(new DocContext(modsDoc), groovyCodeResource);
        List<AssertionTest> tests = new ArrayList<AssertionTest>();
        for (Assertion assertion : assertionList.assertions) tests.add(factory.create(assertion));
        // violation messages can be found in assertion-list.xml
        Assert.assertTrue(tests.get(0).getViolation(modsDoc).contains("No florida found in the string Agricultural"));
        Assert.assertTrue(tests.get(1).getViolation(modsDoc).contains("No florida found in Agricultural"));
        Assert.assertTrue(tests.get(2).getViolation(modsDoc).contains("Improper value: Collection"));
        Assert.assertTrue(tests.get(3).getViolation(modsDoc).contains("Authority 'dubious' is not divine!"));
        Assert.assertTrue(tests.get(4).getViolation(modsDoc).contains("empty!"));
    }

    private static XStream getStream() {
        XStream xstream = new XStream(new PureJavaReflectionProvider());
        xstream.setMode(XStream.NO_REFERENCES);
        xstream.processAnnotations(Assertion.AssertionList.class);
        return xstream;
    }

    public static class DocContext implements NamespaceContext {
        private Map<String, String> prefixUri = new TreeMap<String, String>();
        private Map<String, String> uriPrefix = new TreeMap<String, String>();

        public DocContext(Document doc) {
            this(doc.getDocumentElement());
        }

        public DocContext(Node node) {
            gatherNamespacesFrom(node);
        }

        private void gatherNamespacesFrom(Node node) {
            if (node.getNodeType() != Node.ATTRIBUTE_NODE && node.getNodeType() != Node.ELEMENT_NODE) return;
            if (!prefixUri.containsKey(node.getPrefix())) {
                prefixUri.put(node.getPrefix(), node.getNamespaceURI());
                uriPrefix.put(node.getNamespaceURI(), node.getPrefix());
            }
            NodeList kids = node.getChildNodes();
            for (int walk = 0; walk < kids.getLength(); walk++) {
                gatherNamespacesFrom(kids.item(walk));
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixUri.get(prefix);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return uriPrefix.get(namespaceURI);
        }

        @Override
        public Iterator getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            if (prefix == null) return null;
            List<String> list = new ArrayList<String>();
            list.add(prefix);
            return list.iterator();
        }
    }

    private Document parseDoc(String fileName) throws SAXException, IOException, ParserConfigurationException {
        URL modsResource = getClass().getResource("/assertion/" + fileName);
        File file = new File(modsResource.getFile());
        return XMLToolFactory.documentBuilderFactory().newDocumentBuilder().parse(file);
    }


}
