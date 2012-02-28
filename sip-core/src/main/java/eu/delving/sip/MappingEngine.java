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
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

/**
 * Wrapping the mapping mechanism for easy access from Scala
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingEngine {
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;
    private Validator validator;
    private long compileTime, parseTime, mapTime, validateTime, outputTime;

    public MappingEngine(String mapping, Map<String, String> namespaces, ClassLoader classLoader, RecDefModel recDefModel, Validator validator) throws FileNotFoundException, MetadataException {
        RecMapping recordMapping = RecMapping.read(new StringReader(mapping), recDefModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        long now = System.currentTimeMillis();
        mappingRunner = new MappingRunner(groovyCodeResource, recordMapping, null);
        compileTime += System.currentTimeMillis() - now;
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        this.validator = validator;
    }

    public IndexDocument executeMapping(String originalRecord) throws MappingException, SAXException {
        return executeMapping(originalRecord, -1);
    }

    public IndexDocument executeMapping(String originalRecord, int recordNumber) throws MappingException, SAXException {
        try {
            long now = System.currentTimeMillis();
            MetadataRecord metadataRecord = metadataRecordFactory.fromXml(originalRecord);
            parseTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            Node outputRecord = mappingRunner.runMapping(metadataRecord);
            mapTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            Source source = new DOMSource(outputRecord);
            validator.validate(source);
            validateTime += System.currentTimeMillis() - now;
            now = System.currentTimeMillis();
            IndexDocument indexDocument = IndexDocument.fromNode(outputRecord, mappingRunner.getRecDefTree());
            outputTime += System.currentTimeMillis() - now;
            return indexDocument;
        }
        catch (DiscardRecordException e) {
            return null;
        }
        catch (XMLStreamException e) {
            throw new MappingException(null, "XML Streaming problem!", e);
        }
        catch (IOException e) {
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
