/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.test;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaVersion;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * Make sure the right code is being generated
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestCodeGeneration {
    private RecDefModel recDefModel = recDefModel();
    private RecMapping recMapping;
    private String input, expect;

    @Before
    public void prep() throws IOException, MetadataException {
        recDefModel.createRecDefTree(new SchemaVersion("test", "0.0.0"));
        URL mappingResource = getClass().getResource("/codegen/TestCodeGeneration-mapping.xml");
        recMapping = RecMapping.read(new File(mappingResource.getFile()), recDefModel);
        recMapping.getRecDefTree().setListener(new ChattyListener());
        URL inputResource = getClass().getResource("/codegen/TestCodeGeneration-input.xml");
        input = FileUtils.readFileToString(new File(inputResource.getFile()));
        URL expectResource = getClass().getResource("/codegen/TestCodeGeneration-expect.xml");
        expect = FileUtils.readFileToString(new File(expectResource.getFile()));
    }

    @Test
    public void cornucopia() throws IOException, XMLStreamException, MappingException {
        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null, true);
        printWithLineNumbers(mappingRunner.getCode());
        MetadataRecord inputRecord = createInputRecord();
        Node node = mappingRunner.runMapping(inputRecord);
        String xml = new XmlSerializer().toXml(node, true);
        Assert.assertEquals("Unexpected xml", expect, xml);
    }

        private void printWithLineNumbers(String code) {
            int lineNumber = 1;
            for (String line : code.split("\n")) System.out.println(String.format("%3d: %s", lineNumber++, line));
        }

    private MetadataRecord createInputRecord() throws IOException, XMLStreamException {
        Map<String, String> ns = new TreeMap<String, String>();
        ns.put("test", "http://testicles.org");
        return new MetadataRecordFactory(ns).metadataRecordFrom("ideee", input);
    }

    private static RecDefModel recDefModel() {
        return new RecDefModel() {
            @Override
            public RecDefTree createRecDefTree(SchemaVersion schemaVersion) {
                if (!"test".equals(schemaVersion.getPrefix())) throw new RuntimeException();
                try {
                    URL url = TestCodeGeneration.class.getResource("/codegen/TestCodeGeneration-recdef.xml");
                    InputStream inputStream = url.openStream();
                    return RecDefTree.create(RecDef.read(inputStream));
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load recdef", e);
                }
            }
        };
    }

    private static class ChattyListener implements RecDefNodeListener {
        @Override
        public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
            System.out.println("Mapping changed: " + recDefNode);
        }

        @Override
        public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
            System.out.println("Mapping added: " + recDefNode);
        }

        @Override
        public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
            System.out.println("Mapping removed: " + recDefNode);
        }

        @Override
        public void populationChanged(RecDefNode recDefNode) {
            System.out.println("Population changed: " + recDefNode);
        }
    }
}
