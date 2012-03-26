/*
 * Copyright 2011 DELVING BV
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

import eu.delving.groovy.MappingException;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.*;
import eu.delving.sip.IndexDocument;
import eu.delving.sip.MappingEngine;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Node;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A unit test of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingEngine {

    @Test
    public void validateTreeNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        MappingEngine mappingEngine = new MappingEngine(mapping("lido"), classLoader(), new MockRecDefModel("lido"));
        Node node = mappingEngine.toNode(input("lido"));
        System.out.println(XmlSerializer.toXml(node));
        Source source = new DOMSource(node);
        validator("lido").validate(source);
    }

    @Test
    public void validateFlatNode() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        MappingEngine mappingEngine = new MappingEngine(mapping("icn"), classLoader(), new MockRecDefModel("icn"));
        Node node = mappingEngine.toNode(input("icn"));
        Source source = new DOMSource(node);
        validator("icn").validate(source);
    }

    @Test
    public void indexDocument() throws IOException, SAXException, MappingException, XMLStreamException, MetadataException {
        MappingEngine mappingEngine = new MappingEngine(mapping("icn"), classLoader(), new MockRecDefModel("icn"));
        Node node = mappingEngine.toNode(input("icn"));
        System.out.println(XmlSerializer.toXml(node));
        IndexDocument indexDocument = mappingEngine.toIndexDocument(input("icn"));
        System.out.println(indexDocument);
        Assert.assertFalse(indexDocument.getMap().isEmpty());
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
        public List<FactDefinition> getFactDefinitions() {
            try {
                return FactDefinition.read(file(String.format("/%s/fact-definition-list.xml", prefix)));
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<String> getPrefixes() throws MetadataException {
            Set<String> prefixes = new TreeSet<String>();
            prefixes.add("icn");
            return prefixes;
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
