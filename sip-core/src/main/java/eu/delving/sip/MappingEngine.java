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
import java.io.FileNotFoundException;
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

    public MappingEngine(String mapping, ClassLoader classLoader, RecDefModel recDefModel, Map<String,String> namespaces) throws FileNotFoundException, MetadataException {
        RecMapping recMapping = RecMapping.read(new StringReader(mapping), recDefModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
        mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
    }

    public Node toNode(String originalRecord) throws XMLStreamException, MappingException {
        MetadataRecord metadataRecord = metadataRecordFactory.fromXml(originalRecord);
        return mappingRunner.runMapping(metadataRecord);
    }

    public IndexDocument toIndexDocument(String originalRecord) throws MappingException, SAXException {
        try {
            MetadataRecord metadataRecord = metadataRecordFactory.fromXml(originalRecord);
            Node outputRecord = mappingRunner.runMapping(metadataRecord);
            return IndexDocument.fromNode(outputRecord, mappingRunner.getRecDefTree());
        }
        catch (AssertionError e) {
            throw new MappingException(null, "Discarded record should have been marked before upload!", e);
        }
        catch (XMLStreamException e) {
            throw new MappingException(null, "XML Streaming problem!", e);
        }
    }
}
