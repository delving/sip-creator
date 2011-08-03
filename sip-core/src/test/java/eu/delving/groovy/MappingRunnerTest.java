package eu.delving.groovy;

import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.MappingEngine;
import groovy.util.Node;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class MappingRunnerTest {

    private static final String MAPPING_FILE = "/sample_mapping_ese.xml";
    private static final String ORIGINAL_XML = "<record>" +
            "<europeana:uri>test</europeana:uri>" +
            "<europeana:type>IMAGE</europeana:type>" +
            "</record>";
    private MappingRunner mappingRunner;
    private Map<String, String> namespaces;

    @Before
    public void setUp() throws Exception {
        namespaces = new TreeMap<String, String>();
        namespaces.put("europeana", "http://www.europeana.eu/ese");
        MetadataModel metadataModel = MappingEngine.loadMetadataModel();
        RecordMapping recordMapping = RecordMapping.read(getClass().getResourceAsStream(MAPPING_FILE), metadataModel);
        mappingRunner = new MappingRunner(new GroovyCodeResource(), recordMapping.toCompileCode(metadataModel));
    }

    @Test
    public void testRunMapping() throws Exception {
        MetadataRecord record = new MetadataRecordFactory(namespaces).fromXml(ORIGINAL_XML);
        Node node = mappingRunner.runMapping(record);
        Assert.assertEquals(2, node.attributes().size());
    }

}
