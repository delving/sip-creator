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
import eu.delving.MappingEngineFactory;
import eu.delving.MappingResult;
import eu.delving.XMLToolFactory;
import eu.delving.groovy.MappingException;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.util.FileSystemFetcher;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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

    private SchemaRepository schemaRepo;

    @Before
    public void initRepo() throws IOException {
        schemaRepo = new SchemaRepository(new FileSystemFetcher(true));
    }

    @Test
    public void validateTreeNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namespaces, new MockRecDefModel(), mapping("lido"));
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute("validateTreeNode", input("lido"));
//        System.out.println(result);
        System.out.println("Fields:");
        for (Map.Entry<String, List<String>> entry : result.copyFields().entrySet()) {
            System.out.println(entry.getKey()+":"+entry.getValue());
        }
        Assert.assertEquals("Should be two copy fields", 3, result.copyFields().size());
        Source source = new DOMSource(result.root());
        validator(new SchemaVersion("lido", "1.0.2")).validate(source);
    }

    @Test
    public void validateESENode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namespaces, new MockRecDefModel(), mapping("ese"));
        MappingResult result = mappingEngine.execute("validateESENode", input("ese"));
//        System.out.println(result.toXml());
//        System.out.println(result.toXmlAugmented());
        Source source = new DOMSource(result.root());
        validator(new SchemaVersion("ese", "3.4.0")).validate(source);
    }

    @Test
    public void validateABMNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "delving", "http://schemas.delving.eu/",
                "abm", "http://abmu.org/abm"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namespaces, new MockRecDefModel(), mapping("abm"));
        MappingResult result = mappingEngine.execute("validateABMNode", input("abm"));
//        System.out.println(result.toXml());
        Source source = new DOMSource(result.root());
        Validator validator = validator(new SchemaVersion("abm", "1.0.7"));
        validator.validate(source);
//        System.out.println("Fields:");
//        for (Map.Entry<String, List<String>> entry : result.fields().entrySet()) {
//            System.out.println(entry.getKey()+":"+entry.getValue());
//        }
        Assert.assertEquals("Number of fields unexpected", 24, result.fields().size());
//        System.out.println("SystemFields:");
//        System.out.println(result.copyFields());
    }

    @Ignore
    @Test
    public void rawNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "", "http://raw.org"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namespaces, null, null);
        MappingResult result = mappingEngine.execute("rawNode", input("raw"));
        System.out.println(result.toXmlAugmented());
    }

    @Test
    public void validateTIBNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namspaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "tib", "http://thuisinbrabant.nl"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namspaces, new MockRecDefModel(), mapping("tib"));
        MappingResult result = mappingEngine.execute("validateTIBNode", input("tib"));
        String augmented = result.toXmlAugmented();
        Matcher matcher = Pattern.compile("<delving:thumbnail>").matcher(augmented);
        Assert.assertTrue("first one not found", matcher.find());
        Assert.assertFalse("second one should not be found", matcher.find());
//        System.out.println(matcher.find() + " - "+matcher.find());
//        System.out.println(augmented);
        Source source = new DOMSource(result.root());
        validator(new SchemaVersion("tib", "1.0.0")).validate(source);
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
        @Override
        public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
            try {
                String recDefString = schemaRepo.getSchema(schemaVersion, SchemaType.RECORD_DEFINITION).getSchemaText();
                if (recDefString == null) throw new RuntimeException("Unable to find record definition "+schemaVersion);
                return RecDefTree.create(RecDef.read(new ByteArrayInputStream(recDefString.getBytes("UTF-8"))));
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to fetch record definition", e);
            }
        }
    }

    private Validator validator(SchemaVersion schemaVersion) {
        try {
            SchemaFactory factory = XMLToolFactory.schemaFactory(schemaVersion.getPrefix());
            factory.setResourceResolver(new CachedResourceResolver());
            String validationXsd = schemaRepo.getSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA).getSchemaText();
            if (validationXsd == null) throw new RuntimeException("Unable to find validation schema "+schemaVersion);
            Schema schema = factory.newSchema(new StreamSource(new StringReader(validationXsd)));
            return schema.newValidator();
        }
        catch (Exception e) {
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
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream stream(String resourcePath) {
        InputStream resource = getClass().getResourceAsStream(resourcePath);
        if (resource == null) throw new RuntimeException("Resource not found: "+resourcePath);
        return resource;
    }

}
