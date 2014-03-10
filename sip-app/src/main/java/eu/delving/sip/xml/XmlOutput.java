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
import eu.delving.metadata.Hasher;
import eu.delving.metadata.RecDef;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static eu.delving.sip.files.Storage.TARGET_ROOT_TAG;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class XmlOutput {
    private File outputZipFile;
    private XMLEventFactory eventFactory = XMLToolFactory.xmlEventFactory();
    private GZIPOutputStream outputStream;
    private XMLEventWriter out;

    public XmlOutput(File outputZipFile, Map<String, RecDef.Namespace> namespaces) throws IOException, XMLStreamException {
        this.outputZipFile = outputZipFile;
        outputStream = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outputZipFile)));
        out = XMLToolFactory.xmlOutputFactory().createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        out.add(eventFactory.createStartDocument());
        out.add(eventFactory.createCharacters("\n"));
        StringBuilder schemaLocation = new StringBuilder();
        List<Namespace> namespaceList = new ArrayList<Namespace>();
        for (RecDef.Namespace namespace : namespaces.values()) {
            namespaceList.add(eventFactory.createNamespace(namespace.prefix, namespace.uri));
            if (namespace.schema != null) {
                schemaLocation.append(namespace.uri).append(' ').append(namespace.schema).append(' ');
            }
        }
        List<Attribute> attributes = new ArrayList<Attribute>();
        attributes.add(eventFactory.createAttribute("xsi:schemaLocation", schemaLocation.toString()));
        out.add(eventFactory.createStartElement("", "", TARGET_ROOT_TAG, attributes.iterator(), namespaceList.iterator()));
    }

    public void write(Node node, String identifier) {
        try {
            Element element = (Element) node;
            element.removeAttribute("xsi:schemaLocation");
            element.setAttribute("id", identifier);
            writeElement(element, 1);
        }
        catch (XMLStreamException e) {
            throw new RuntimeException("Trouble writing node to output", e);
        }
    }

    public void finish() {
        try {
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndElement("", "", TARGET_ROOT_TAG));
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndDocument());
            out.flush();
            outputStream.close();
            outputZipFile = Hasher.ensureFileHashed(outputZipFile);
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
                    out.add(eventFactory.createCharacters(text));
                    textKids = true;
                    break;
                case Node.CDATA_SECTION_NODE:
                    if (text.isEmpty()) break;
                    out.add(eventFactory.createCData(text));
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
