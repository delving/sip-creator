package eu.delving.sip;

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.MetadataRecordFactory;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.ValidationException;
import groovy.util.Node;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Wrapping the mapping mechanism for easy access from Scala
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingEngine {
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;
    private RecordValidator recordValidator;

    public MappingEngine(InputStream mappingFile, Map<String, String> namespaces) throws FileNotFoundException, MetadataException {
        MetadataModel metadataModel = loadMetadataModel();
        RecordMapping recordMapping = RecordMapping.read(mappingFile, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource();
        mappingRunner = new MappingRunner(groovyCodeResource, recordMapping.toCompileCode(metadataModel));
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        recordValidator = new RecordValidator(groovyCodeResource, metadataModel.getRecordDefinition(recordMapping.getPrefix()));
    }

    /**
     * Execute the mapping on the string format of the original record to turn it into the mapped record
     *
     * @param originalRecord a string of XML, without namespaces
     * @return the mapped record XML, or NULL if the record is invalid or discarded.
     * @throws MappingException something important went wrong with mapping, beyond validation problems
     * @throws ValidationException the record is invalid by the criteria in the record definition
     */

    public Node executeMapping(String originalRecord) throws MappingException, ValidationException {
        return executeMapping(originalRecord, -1);
    }

    /**
     * Execute the mapping on the string format of the original record to turn it into the mapped record
     *
     * @param originalRecord a string of XML, without namespaces
     * @param recordNumber which record was it
     * @return the mapped record XML, or NULL if the record is invalid or discarded.
     * @throws MappingException something important went wrong with mapping, beyond validation problems
     * @throws ValidationException the record is invalid by the criteria in the record definition
     */

    public Node executeMapping(String originalRecord, int recordNumber) throws MappingException, ValidationException {
        try {
            MetadataRecord metadataRecord = metadataRecordFactory.fromXml(originalRecord);
            Node record = mappingRunner.runMapping(metadataRecord);
            recordValidator.validateRecord(record, recordNumber);
            return record;
        }
        catch (DiscardRecordException e) {
            return null;
        }
        catch (XMLStreamException e) {
            throw new MappingException(null, "XML Streaming problem!", e);
        }
    }

    public static MetadataModel loadMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setRecordDefinitionResources(Arrays.asList(
                    "/ese-record-definition.xml",
                    "/icn-record-definition.xml",
                    "/abm-record-definition.xml"
            ));
            metadataModel.setDefaultPrefix("ese");
            return metadataModel;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

}
