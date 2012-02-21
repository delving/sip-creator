/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import groovy.util.Node;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * Wrap the mapping runner and associated code so that mappings can be easily carried out
 * within the CultureHub.  It takes a string corresponding to the record and produces
 * an IndexDocument as output, which is a multi-map containing a list of values for every key.
 *
 * todo: The IndexDocument will need to change for dealing with hierarchical records.
 * todo: Only indexing requires flattening so probably output will have to be a tree eventually.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingEngine {
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;
    private RecordValidator recordValidator;
    private long compileTime, parseTime, mapTime, validateTime, outputTime;

    public MappingEngine(String mapping, Map<String, String> namespaces, ClassLoader classLoader, RecDefModel recDefModel) throws FileNotFoundException, MetadataException {
        Reader reader = new StringReader(mapping);
        RecMapping recMapping = RecMapping.read(reader, recDefModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        long now = System.currentTimeMillis();
        mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
        compileTime += System.currentTimeMillis() - now;
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        recordValidator = new RecordValidator(groovyCodeResource, recMapping);
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
            IndexDocument indexDocument = IndexDocument.fromNode(record, mappingRunner.getRecDefTree());
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
