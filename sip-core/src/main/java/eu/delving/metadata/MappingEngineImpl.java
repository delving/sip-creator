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

package eu.delving.metadata;

import eu.delving.MappingEngine;
import eu.delving.MappingResult;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.MetadataRecordFactory;
import eu.delving.groovy.XmlSerializer;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

/**
 * Wrapping the mapping mechanism so that mappings can be executed independent of the rest of the SIP-Creator.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingEngineImpl implements MappingEngine {
    private XmlSerializer serializer = new XmlSerializer();
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;

    public MappingEngineImpl(ClassLoader classLoader, Map<String, String> namespaces, RecDefModel recDefModel, String mapping) throws FileNotFoundException, MetadataException {
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        if (mapping != null) {
            RecMapping recMapping = RecMapping.read(new StringReader(mapping), recDefModel);
            GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
            mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null, false);
        }
    }

    public MappingResult execute(String id, String recordXML) throws XMLStreamException, MappingException, IOException, SAXException {
        if (mappingRunner != null) {
            MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(id, recordXML, true);
            return new MappingResultImpl(serializer, id, mappingRunner.runMapping(metadataRecord), mappingRunner.getRecDefTree()).resolve();
        }
        else {
            Node root = metadataRecordFactory.nodeFromXml(id, recordXML);
            return new MappingResultImpl(serializer, id, root, null).resolve();
        }
    }

    public String toString() {
        return mappingRunner.getCode();
    }
}
