/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.sip.xml;

import eu.delving.XMLToolFactory;
import eu.delving.groovy.Utils;
import eu.delving.metadata.RecDef;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath
 *
 *
 */

public class XmlOutput {
    private static final String RDF_ROOT_TAG = "RDF";
    private static final String RDF_RECORD_TAG = "Description";
    private static final String RDF_ID_ATTRIBUTE = "about";
    private static final String RDF_PREFIX = "rdf";
    private static final String RDF_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String[][] NAMESPACES = {
            {RDF_PREFIX, RDF_URI},
            {"geo", "http://www.w3.org/2003/01/geo/wgs84_pos#"},
            {"skos", "http://www.w3.org/2004/02/skos/core#"},
            {"rdfs", "http://www.w3.org/2000/01/rdf-schema#"},
            {"cc", "http://creativecommons.org/ns#"},
            {"owl", "http://www.w3.org/2002/07/owl#"},
            {"foaf", "http://xmlns.com/foaf/0.1/"},
            {"dbpedia-owl", "http://dbpedia.org/ontology/"},
            {"dbprop", "http://dbpedia.org/property/"}
    };
    private File outputXmlFile;
    private File tempOutputFile;
    private XMLEventFactory eventFactory = XMLToolFactory.xmlEventFactory();
    private OutputStream outputStream;
    private XMLEventWriter out;

    public XmlOutput(File outputXmlFile, Map<String, RecDef.Namespace> namespaces) throws IOException, XMLStreamException {
        this.outputXmlFile = outputXmlFile;
        FileUtils.deleteQuietly(outputXmlFile);
        this.tempOutputFile = new File(outputXmlFile.getParentFile(), outputXmlFile.getName() + ".temp");
        outputStream = new BufferedOutputStream(new FileOutputStream(tempOutputFile));
        out = XMLToolFactory.xmlOutputFactory().createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        out.add(eventFactory.createStartDocument());
        out.add(eventFactory.createCharacters("\n"));
        StringBuilder schemaLocation = new StringBuilder();
        List<Namespace> namespaceList = new ArrayList<Namespace>();
        Set<String> prefixes = new TreeSet<String>();
        for (RecDef.Namespace namespace : namespaces.values()) {
            prefixes.add(namespace.prefix);
            namespaceList.add(eventFactory.createNamespace(namespace.prefix, namespace.uri));
            if (namespace.schema != null) {
                schemaLocation.append(namespace.uri).append(' ').append(namespace.schema).append(' ');
            }
        }
        for (String[] pair : NAMESPACES) {
            if (prefixes.contains(pair[0])) continue;
            namespaceList.add(eventFactory.createNamespace(pair[0], pair[1]));
        }
        List<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(eventFactory.createAttribute("xsi:schemaLocation", schemaLocation.toString()));
        out.add(eventFactory.createStartElement(RDF_PREFIX, RDF_URI, RDF_ROOT_TAG, attributes.iterator(), namespaceList.iterator()));
    }

    public void write(String identifier, Node node) throws XMLStreamException {
        List<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(eventFactory.createAttribute(RDF_PREFIX, RDF_URI, RDF_ID_ATTRIBUTE, identifier));
        out.add(eventFactory.createCharacters("\n"));
        out.add(eventFactory.createStartElement(RDF_PREFIX, RDF_URI, RDF_RECORD_TAG, attributes.iterator(), null));
        Element element = (Element) node;
        element.removeAttribute("xsi:schemaLocation");
        // skip the root tag, which would have been this: writeElement(element, 1);
        NodeList kids = element.getChildNodes();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            if (kid.getNodeType() != Node.ELEMENT_NODE) continue;
            writeElement((Element) kid, 1);
        }
        out.add(eventFactory.createCharacters("\n"));
        out.add(eventFactory.createEndElement(RDF_PREFIX, RDF_URI, RDF_RECORD_TAG));
    }

    public void finish(boolean aborted) {
        try {
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndElement(RDF_PREFIX, RDF_URI, RDF_ROOT_TAG));
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndDocument());
            out.flush();
            outputStream.close();
            if (aborted) {
                FileUtils.deleteQuietly(tempOutputFile);
            }
            else {
                FileUtils.moveFile(tempOutputFile, outputXmlFile);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Trouble closing xml output", e);
        }
    }

    private void writeElement(Element element, int depth) throws XMLStreamException {
        List<Attribute> attributes = new ArrayList<Attribute>();
        NamedNodeMap namedNodeMap = element.getAttributes();
        for (int walk = 0; walk < namedNodeMap.getLength(); walk++) {
            Node node = namedNodeMap.item(walk);
            final String prefix = node.getPrefix() == null ? "" : node.getPrefix();
            final String namespaceURI = node.getNamespaceURI() == null ? "" : node.getNamespaceURI();
            final String localName = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
            final String nodeValue = node.getNodeValue();
            attributes.add(eventFactory.createAttribute(prefix, namespaceURI, localName, nodeValue));
        }
        out.add(eventFactory.createCharacters("\n"));
        indent(depth);
        out.add(eventFactory.createStartElement(element.getPrefix(), element.getNamespaceURI(), element.getLocalName(), attributes.iterator(), null));
        NodeList kids = element.getChildNodes();
        boolean textKids = false;
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            String text = kid.getTextContent().trim();
            switch (kid.getNodeType()) {
                case Node.TEXT_NODE:
                    if (text.isEmpty()) break;
                    out.add(eventFactory.createCharacters(Utils.stripNonPrinting(text)));
                    textKids = true;
                    break;
                case Node.CDATA_SECTION_NODE:
                    if (text.isEmpty()) break;
                    out.add(eventFactory.createCData(Utils.stripNonPrinting(text)));
                    textKids = true;
                    break;
                case Node.ATTRIBUTE_NODE:
                    // handled in create start element
                    break;
                case Node.ELEMENT_NODE:
                    writeElement((Element) kid, depth + 1);
                    break;
                default:
                    throw new RuntimeException("Node type not implemented: " + kid.getNodeType());

            }
        }
        if (!textKids) {
            out.add(eventFactory.createCharacters("\n"));
            indent(depth);
        }
        out.add(eventFactory.createEndElement(element.getPrefix(), element.getNamespaceURI(), element.getLocalName()));
    }

    private void indent(int depth) throws XMLStreamException {
        for (int walk = 0; walk < depth; walk++) out.add(eventFactory.createCharacters("   "));
    }

}
