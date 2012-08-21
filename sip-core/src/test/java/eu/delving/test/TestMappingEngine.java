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

package eu.delving.test;

import eu.delving.MappingEngine;
import eu.delving.MappingResult;
import eu.delving.groovy.MappingException;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecDefTree;
import eu.delving.schema.SchemaVersion;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A unit test of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingEngine {

    @Test
    public void validateTreeNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namespaces, new MockRecDefModel("lido"), mapping("lido"));
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute(input("lido"));
//        System.out.println(result);
        Source source = new DOMSource(result.root());
        validator("lido").validate(source);
    }

    @Test
    public void validateFlatNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "icn", "http://www.icn.nl/schemas/icn/"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namespaces, new MockRecDefModel("icn"), mapping("icn"));
        MappingResult result = mappingEngine.execute(input("icn"));
        System.out.println(result.toXml());
        System.out.println(result.toXmlAugmented());
//        for (Map.Entry<String, List<String>> entry : result.fields().entrySet()) {
//            System.out.println(entry.getKey() + " -> "+entry.getValue());
//        }
        Source source = new DOMSource(result.root());
        validator("icn").validate(source);
    }

    @Test
    public void rawNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "", "http://raw.org",
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "icn", "http://www.icn.nl/schemas/icn/"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namespaces);
        MappingResult result = mappingEngine.execute(input("icn"));
        System.out.println(result.toXml());
    }

    @Test
    public void validateTIBNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namspaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "tib", "http://thuisinbrabant.nl"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namspaces, new MockRecDefModel("tib"), mapping("tib"));
        MappingResult result = mappingEngine.execute(input("tib"));
        String augmented = result.toXmlAugmented();
        Matcher matcher = Pattern.compile("<delving:thumbnail>").matcher(augmented);
        Assert.assertTrue("first one not found", matcher.find());
        Assert.assertFalse("second one should not be found", matcher.find());
//        System.out.println(matcher.find() + " - "+matcher.find());
//        System.out.println(augmented);
        Source source = new DOMSource(result.root());
        validator("tib").validate(source);
    }

    @Test
    public void tryAff() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namespaces, new MockRecDefModel("aff"), mapping("aff"));
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute(input("aff"));
//        System.out.println(serializer.toXml(result.root()));
    }

    @Test
    public void indexDocumentFromAFF() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = new MappingEngine(classLoader(), namespaces, new MockRecDefModel("aff"), mapping("aff"));
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute(input("aff"));
//        System.out.println(serializer.toXml(result.root()));
        Map<String, List<String>> allFields = result.fields();
//        System.out.println(allFields);
        Assert.assertFalse(allFields.isEmpty());
    }

    private Map<String, String> createNamespaces(String... arg) {
        Map<String, String> map = new TreeMap<String, String>();
        for (int walk = 0; walk < arg.length; walk += 2) map.put(arg[walk], arg[walk + 1]);
        return map;
    }

    private String input(String prefix) {
        return string(String.format("/%s/test-input.xml", prefix));
    }

    private String mapping(String prefix) {
        return string(String.format("/%s/mapping_%s.xml", prefix, prefix));
    }

    private class MockRecDefModel implements RecDefModel {
        private String prefix;

        private MockRecDefModel(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
            if (!this.prefix.equals(prefix)) throw new RuntimeException();
            return RecDefTree.create(RecDef.read(stream(String.format("/%s/%s-record-definition.xml", prefix, prefix))));
        }
    }

    private Validator validator(String prefix) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            Schema schema = factory.newSchema(file(String.format("/%s/%s-validation.xsd", prefix, prefix)));
            return schema.newValidator();
        }
        catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private ClassLoader classLoader() {
        return getClass().getClassLoader();
    }

    private String string(String resourcePath) {
        try {
            return IOUtils.toString(stream(resourcePath), "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream stream(String resourcePath) {
        return getClass().getResourceAsStream(resourcePath);
    }

    private File file(String resourcePath) {
        return new File(getClass().getResource(resourcePath).getFile());
    }
}
