/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.delving.groovy;

import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.MappingEngine;
import groovy.util.Node;
import groovy.xml.QName;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verify if the MappingRunner is behaving as expected. This is done by providing a sample record and a sample
 * mapping file to the runMapping() method.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class MappingRunnerTest {

    private static final String MAPPING_FILE = "/sample_mapping_icn.xml";
    private static final String METADATA_FILE = "/sample_source_record_adlib.xml";
    private static final Logger LOG = Logger.getRootLogger();
    private Map<String, String> namespaces;
    private MappingRunner mappingRunner;

    @Before
    public void setUp() throws Exception {
        namespaces = new TreeMap<String, String>();
        MetadataModel metadataModel = MappingEngine.loadMetadataModel();
        RecordMapping recordMapping = RecordMapping.read(getClass().getResourceAsStream(MAPPING_FILE), metadataModel);
        LOG.info(String.format("RecordMapping has %d facts and %d fieldMappings",
                recordMapping.getFacts().size(),
                recordMapping.getFieldMappings().size()));
        mappingRunner = new MappingRunner(new GroovyCodeResource(), recordMapping.toCompileCode(metadataModel));
    }

    @Test
    public void testRunMapping() throws Exception {
        URL url = getClass().getResource(METADATA_FILE);
        String xmlRecord = FileUtils.readFileToString(new File(url.toURI()));
        LOG.info("Original record : " + xmlRecord);
        MetadataRecord record = new MetadataRecordFactory(namespaces).fromXml(xmlRecord);
        Node rootNode = mappingRunner.runMapping(record);
        LOG.info("Mapping result :");
        if (rootNode.value() instanceof ArrayList) {
            for (Object nodeObject : (ArrayList) rootNode.value()) {
                Node node = (Node) nodeObject;
                LOG.info(String.format("%20s : %s", ((QName) node.name()).getLocalPart(), node.value()));
            }
        }
        Assert.assertNotNull(rootNode.value());
    }

}
