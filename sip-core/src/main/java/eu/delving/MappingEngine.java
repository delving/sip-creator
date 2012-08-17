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

package eu.delving;

import eu.delving.groovy.*;
import eu.delving.metadata.MappingResultImpl;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
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

public class MappingEngine {
    private XmlSerializer serializer = new XmlSerializer();
    private MetadataRecordFactory metadataRecordFactory;
    private MappingRunner mappingRunner;

    public MappingEngine(ClassLoader classLoader, Map<String, String> namespaces) throws FileNotFoundException, MetadataException {
        this(classLoader, namespaces, null, null, null);
    }

    public MappingEngine(ClassLoader classLoader, Map<String, String> namespaces, RecDefModel recDefModel, PluginBinding pluginBinding, String mapping) throws FileNotFoundException, MetadataException {
        metadataRecordFactory = new MetadataRecordFactory(namespaces);
        if (mapping != null) {
            RecMapping recMapping = RecMapping.read(new StringReader(mapping), recDefModel);
            GroovyCodeResource groovyCodeResource = new GroovyCodeResource(classLoader);
            mappingRunner = new MappingRunner(groovyCodeResource, recMapping, pluginBinding, null);
        }
    }

    public MappingResult execute(String recordXML) throws XMLStreamException, MappingException, IOException, SAXException {
        if (mappingRunner != null) {
            MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(recordXML);
            return new MappingResultImpl(serializer, mappingRunner.runMapping(metadataRecord), mappingRunner.getRecDefTree()).resolve();
        }
        else {
            Node root = metadataRecordFactory.nodeFromXml(recordXML);
            return new MappingResultImpl(serializer, root, null);
        }
    }

    public String toString() {
        return mappingRunner.getCode();
    }
}
