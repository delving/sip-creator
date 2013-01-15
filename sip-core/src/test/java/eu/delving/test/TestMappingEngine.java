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
import eu.delving.groovy.MappingException;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.util.FileSystemFetcher;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.URL;
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
        MappingEngine mappingEngine = engine(namespaces, "lido");
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute("validateTreeNode", input("lido"));
//        System.out.println(result);
        Source source = new DOMSource(result.root());
        validator(new SchemaVersion("lido", "1.0.0")).validate(source);
    }

    @Test
    public void validateESENode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/"
        );
        MappingEngine mappingEngine = engine(namespaces, "ese");
        MappingResult result = mappingEngine.execute("validateESENode", input("ese"));
        System.out.println(result.toXml());
        System.out.println(result.toXmlAugmented());
        Source source = new DOMSource(result.root());
        validator(new SchemaVersion("ese", "3.4.0")).validate(source);
    }

    @Test
    public void validateFlatNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "delving", "http://schemas.delving.eu/",
                "icn", "http://www.icn.nl/schemas/icn/"
        );
        MappingEngine mappingEngine = engine(namespaces, "icn");
        MappingResult result = mappingEngine.execute("validateFlatNode", input("icn"));
        System.out.println(result.toXml());
        System.out.println(result.toXmlAugmented());
        Source source = new DOMSource(result.root());
        Validator validator = validator(new SchemaVersion("icn", "1.0.2"));
        validator.validate(source);
    }

    @Test
    public void rawNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "", "http://raw.org"
        );
        MappingEngine mappingEngine = MappingEngineFactory.newInstance(classLoader(), namespaces);
        MappingResult result = mappingEngine.execute("rawNode", input("raw"));
        System.out.println(result.toXmlAugmented());
    }

    @Test
    public void validateTIBNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "tib", "http://thuisinbrabant.nl"
        );
        MappingEngine mappingEngine = engine(namespaces, "tib");
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

    @Test
    public void mediaMatchingAFF() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "aff", "http://schemas.delving.eu/aff"
        );
        MappingEngine mappingEngine = engine(namespaces, "aff");
        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute("mediaMatchingAFF", input("aff"));
        String xml = result.toXml();
        System.out.println(result);
        Assert.assertTrue("media file not matched", xml.indexOf("4F8EF966FF32B363C1E611A9EAE3370A") > 0);
    }

    @Test
    public void indexDocumentFromAFF() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = engine(namespaces, "aff");
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute("indexDocumentAFF", input("aff"));
//        System.out.println(serializer.toXml(result.root()));
        Map<String, List<String>> allFields = result.fields();
//        System.out.println(allFields);
        Assert.assertFalse(allFields.isEmpty());
    }

    private MappingEngine engine(Map<String, String> namespaces, String prefix) throws FileNotFoundException, MetadataException {
        return new MappingEngineImpl(
                classLoader(),
                namespaces,
                new MockRecDefModel(prefix),
                mapping(prefix)
        );
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

    private String mediaFilesResource(String prefix) {
        return String.format("/%s/media-index.xml", prefix);
    }

    private class MockRecDefModel implements RecDefModel {
        private String prefix;

        private MockRecDefModel(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
            try {
                String recDefString = schemaRepo.getSchema(schemaVersion, SchemaType.RECORD_DEFINITION);
                if (recDefString == null) {
                    throw new RuntimeException("Unable to find record definition " + schemaVersion);
                }
                return RecDefTree.create(RecDef.read(new ByteArrayInputStream(recDefString.getBytes("UTF-8"))));
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to fetch record definition", e);
            }
        }

        @Override
        public MediaIndex readMediaIndex() throws MetadataException {
            URL resource = this.getClass().getResource(mediaFilesResource(prefix));
            try {
                return resource != null ? MediaIndex.read(resource.openStream()) : null;
            }
            catch (IOException e) {
                throw new MetadataException("Unable to read media index", e);
            }
        }
    }

    private Validator validator(SchemaVersion schemaVersion) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            factory.setResourceResolver(new CachedResourceResolver());
            String validationXsd = schemaRepo.getSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA);
            if (validationXsd == null) throw new RuntimeException("Unable to find validation schema " + schemaVersion);
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
            throw new RuntimeException("Resource fetch failed:" + resourcePath, e);
        }
    }

    private InputStream stream(String resourcePath) {
        return getClass().getResourceAsStream(resourcePath);
    }

}
