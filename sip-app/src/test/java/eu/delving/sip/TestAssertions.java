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
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.util.FileSystemFetcher;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Try out the assertion mechanism
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestAssertions {

    private final XmlSerializer SERIAL = new XmlSerializer();
    private GroovyCodeResource groovyCodeResource;
    private XPathFactory pathFactory;
    private Document modsDoc, icnDoc;
    private DocContext modsContext, icnContext;
    private SchemaRepository schemaRepository;

    @Before
    public void prep() throws ParserConfigurationException, IOException, SAXException, XPathFactoryConfigurationException {
        groovyCodeResource = new GroovyCodeResource(ClassLoader.getSystemClassLoader());
        modsDoc = parseDoc("mods.xml");
        icnDoc = parseDoc("icn.xml");
        modsContext = new DocContext(modsDoc);
        icnContext = new DocContext(icnDoc);
        pathFactory = XMLToolFactory.xpathFactory();
        schemaRepository = new SchemaRepository(new FileSystemFetcher(true));
    }

    @Test
    public void testGeneratedStructureTestsICN() throws XPathExpressionException, IOException, XPathFactoryConfigurationException {
        RecDef icnRecDef = RecDef.read(new ByteArrayInputStream(schemaRepository.getSchema(new SchemaVersion("icn", "1.0.3"), SchemaType.RECORD_DEFINITION).getBytes()));
        List<StructureTest> structureTests = StructureTest.listFrom(icnRecDef);
        for (StructureTest structureTest : structureTests) {
            switch (structureTest.getViolation(icnDoc.getDocumentElement())) {
                case NONE:
                    System.out.println("OK: "+structureTest);
                    break;
                case REQUIRED:
                    System.out.println("Required: "+structureTest);
                    break;
                case SINGULAR:
                    System.out.println("Singular: "+structureTest);
                    break;
            }
        }
    }

    @Test
    public void testGeneratedStructureTests() throws XPathExpressionException, IOException, XPathFactoryConfigurationException {
        RecDef modsRecDef = RecDef.read(new ByteArrayInputStream(schemaRepository.getSchema(new SchemaVersion("mods", "3.4.0"), SchemaType.RECORD_DEFINITION).getBytes()));
        List<StructureTest> structureTests = StructureTest.listFrom(modsRecDef);
        for (StructureTest structureTest : structureTests) {
            switch (structureTest.getViolation(modsDoc)) {
                case NONE:
                    System.out.println("OK: "+structureTest);
                    break;
                case REQUIRED:
                    System.out.println("Required: "+structureTest);
                    break;
                case SINGULAR:
                    System.out.println("Singular: "+structureTest);
                    break;
            }
        }
    }

    @Test
    public void testStructure() throws XPathExpressionException {
        StructureTest.Factory factory = new StructureTest.Factory(pathFactory, modsContext);
        StructureTest structureTest = factory.create(Path.create("/mods:mods/mods:name/mods:namePart"), true, true);
        System.out.println(structureTest + ": " + structureTest.getViolation(modsDoc));
        structureTest = factory.create(Path.create("/mods:mods/mods:subject/@authority"), true, false);
        System.out.println(structureTest + ": " + structureTest.getViolation(modsDoc));
    }

    @Test
    public void testAssertions() throws XPathExpressionException {
        URL assertionResource = getClass().getResource("/assertion/assertion-list.xml");
        File assertionFile = new File(assertionResource.getFile());
        Assertion.AssertionList assertionList = (Assertion.AssertionList) getStream().fromXML(assertionFile);
        AssertionTest.Factory factory = new AssertionTest.Factory(pathFactory, new DocContext(modsDoc), groovyCodeResource);
        List<AssertionTest> tests = new ArrayList<AssertionTest>();
        for (Assertion assertion : assertionList.assertions) tests.add(factory.create(assertion));
        for (AssertionTest test : tests) {
            String violation = test.getViolation(modsDoc);
            if (violation != null) {
                System.out.printf("%s: %s\n", test, violation);
            }
        }
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
        URL modsResource = getClass().getResource("/assertion/"+fileName);
        File file = new File(modsResource.getFile());
        return XMLToolFactory.documentBuilderFactory().newDocumentBuilder().parse(file);
    }
}
