package eu.delving.sip;

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.MetadataRecordFactory;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.ValidationException;
import groovy.util.Node;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
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
    private long compileTime, parseTime, mapTime, validateTime, outputTime;

    public MappingEngine(String mapping, Map<String, String> namespaces, ClassLoader classLoader, MetadataModel metadataModel) throws FileNotFoundException, MetadataException {
        RecordMapping recordMapping = RecordMapping.read(mapping, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        long now = System.currentTimeMillis();
        mappingRunner = new MappingRunner(groovyCodeResource, recordMapping.toCompileCode(metadataModel));
        compileTime += System.currentTimeMillis() - now;
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        recordValidator = new RecordValidator(groovyCodeResource, metadataModel.getRecordDefinition(recordMapping.getPrefix()));
    }

    /**
     * Execute the mapping on the string format of the original record to turn it into the mapped record
     *
     * @param originalRecord a string of XML, without namespaces
     * @return the mapped record XML, or NULL if the record is invalid or discarded.
     * @throws MappingException    something important went wrong with mapping, beyond validation problems
     * @throws ValidationException the record is invalid by the criteria in the record definition
     */

    public IndexDocument executeMapping(String originalRecord) throws MappingException, ValidationException {
        return executeMapping(originalRecord, -1);
    }

    /**
     * Execute the mapping on the string format of the original record to turn it into the mapped record
     *
     * @param originalRecord a string of XML, without namespaces
     * @param recordNumber   which record was it
     * @return the mapped record XML, or NULL if the record is invalid or discarded.
     * @throws MappingException    something important went wrong with mapping, beyond validation problems
     * @throws ValidationException the record is invalid by the criteria in the record definition
     */

    public IndexDocument executeMapping(String originalRecord, int recordNumber) throws MappingException, ValidationException {
        try {
            long now = System.currentTimeMillis();
            MetadataRecord metadataRecord = metadataRecordFactory.fromXml(originalRecord);
            parseTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            Node record = mappingRunner.runMapping(metadataRecord);
            mapTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            recordValidator.validateRecord(record, recordNumber);
            validateTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            IndexDocument indexDocument = IndexDocument.fromNode(record);
            outputTime += System.currentTimeMillis() - now;
            return indexDocument;
        }
        catch (DiscardRecordException e) {
            return null;
        }
        catch (XMLStreamException e) {
            throw new MappingException(null, "XML Streaming problem!", e);
        }
    }

    public String toString() {
        return "MappingEngine {\n" +
                "\tCompile Time  : " + compileTime + "\n" +
                "\tParse Time    : " + parseTime + "\n" +
                "\tMap Time      : " + mapTime + "\n" +
                "\tValidate Time : " + validateTime + "\n" +
                "\tOutput Time   : " + outputTime + "\n" +
                "}\n";
    }

}
