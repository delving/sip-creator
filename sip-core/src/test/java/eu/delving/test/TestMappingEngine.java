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
import eu.delving.PluginBinding;
import eu.delving.groovy.MappingException;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecDefTree;
import eu.delving.plugin.MediaFiles;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A unit test of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingEngine {

    private MappingEngine engine(Map<String, String> namespaces, String prefix) throws FileNotFoundException, MetadataException {
        return new MappingEngine(
                classLoader(),
                namespaces,
                new MockRecDefModel(prefix),
                new MockPluginBinding(prefix),
                mapping(prefix)
        );
    }

    @Test
    public void validateTreeNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = engine(namespaces, "lido");
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
        MappingEngine mappingEngine = engine(namespaces, "icn");
        MappingResult result = mappingEngine.execute(input("icn"));
//        System.out.println(result.toXml());
//        System.out.println(result.toXmlAugmented());
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
//        System.out.println(result.toXml());
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
        MappingResult result = mappingEngine.execute(input("tib"));
//        System.out.println(result);
        Source source = new DOMSource(result.root());
        validator("tib").validate(source);
    }

    @Test
    public void mediaMatchingAFF() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = engine(namespaces, "aff");
//        System.out.println(mappingEngine);
        MappingResult result = mappingEngine.execute(input("aff"));
        String xml = result.toXml();
//        System.out.println(result);
        Assert.assertTrue("media file not matched", xml.indexOf("4F8EF966FF32B363C1E611A9EAE3370A") > 0);
    }

    @Test
    public void indexDocumentFromAFF() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        Map<String, String> namespaces = createNamespaces(
                "lido", "http://www.lido-schema.org"
        );
        MappingEngine mappingEngine = engine(namespaces, "aff");
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

    private String mediaFilesResource(String prefix) {
        return String.format("/%s/media-files.xml", prefix);
    }

    private class MockPluginBinding implements PluginBinding {
        private MediaFiles mediaFiles;

        private MockPluginBinding(String prefix) {
            URL resource = this.getClass().getResource(mediaFilesResource(prefix));
            if (resource != null) {
                try {
                    this.mediaFiles = MediaFiles.read(resource.openStream());
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public Object getFunctionBinding(String functionName) throws MetadataException {
            if (!"matchMediaFile".equals(functionName) || mediaFiles == null) return null;
            return mediaFiles;
        }
    }

    private class MockRecDefModel implements RecDefModel {
        private String prefix;

        private MockRecDefModel(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public RecDefTree createRecDef(String prefix) throws MetadataException {
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
