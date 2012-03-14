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
import eu.delving.metadata.*;
import eu.delving.sip.IndexDocument;
import eu.delving.sip.MappingEngine;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
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

    MappingEngine mappingEngine;

    @Before
    public void createMappingEngine() throws IOException, MetadataException {
        String mapping = string("/flat/mapping_icn.xml");
        mappingEngine = new MappingEngine(mapping, classLoader(), new MockRecDefModel());
    }

    @Ignore
    @Test
    public void validateNode() throws IOException, SAXException, MappingException, XMLStreamException {
        Node node = mappingEngine.toNode(string("/flat/test-input.xml"));
        Source source = new DOMSource(node);
        validator("/flat/icn-validation.xsd").validate(source);
    }

    @Test
    public void indexDocument() throws IOException, SAXException, MappingException, XMLStreamException {
        IndexDocument indexDocument = mappingEngine.toIndexDocument(string("/flat/test-input.xml"));
        System.out.println(indexDocument);
        Assert.assertFalse(indexDocument.getMap().isEmpty());
    }

    private class MockRecDefModel implements RecDefModel {

        @Override
        public List<FactDefinition> getFactDefinitions() {
            try {
                return FactDefinition.read(file("/flat/fact-definition-list.xml"));
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
            return RecDefTree.create(RecDef.read(stream("/flat/icn-record-definition.xml")));
        }
    }
    
    private Validator validator(String resourcePath) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            Schema schema = factory.newSchema(file(resourcePath));
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
