/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.groovy;

import eu.delving.XMLToolFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Here we handle turning a DOM node into an XML Document
 *
 *
 */

public class XmlSerializer {

    private XMLOutputFactory outputFactory = XMLToolFactory.xmlOutputFactory();
    private XMLEventFactory eventFactory = XMLToolFactory.xmlEventFactory();
    private List<String> indentStrings = new ArrayList<String>();

    public String toXml(Node node, boolean fromMapping) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
            writeXml(writer, node, fromMapping);
            return outputStream.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeXml(OutputStreamWriter writer, Node node, boolean fromMapping) {
        if (node.getNodeType() != Node.ELEMENT_NODE)
            throw new IllegalArgumentException("toXml should only be called on an element");
        try {
            Map<String, String> namespaces = new TreeMap<String, String>();
            gatherNamespaces(node, namespaces);
            List<Namespace> nslist = new ArrayList<Namespace>();
            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                if (entry.getValue().trim().isEmpty()) continue;
                nslist.add(eventFactory.createNamespace(entry.getKey(), entry.getValue()));
            }
            //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            //OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
            XMLEventWriter out = outputFactory.createXMLEventWriter(writer);
            out.add(eventFactory.createStartDocument());
            out.add(eventFactory.createCharacters("\n"));
            List<Attribute> attributes = getAttributes(node);
            String prefix = node.getPrefix();
            if (prefix == null) prefix = "";
            String uri = node.getNamespaceURI();
            if (uri == null) uri = "";
            String localName = node.getLocalName();
            if (localName == null) {
                localName = node.getNodeName();
            }
            out.add(eventFactory.createStartElement(prefix, uri, localName, attributes.iterator(), nslist.iterator()));
            if (fromMapping) out.add(eventFactory.createCharacters("\n"));
            NodeList kids = node.getChildNodes();
            if (fromMapping) {
                for (int walk = 0; walk < kids.getLength(); walk++) {
                    Node kid = kids.item(walk);
                    switch (kid.getNodeType()) {
                        case Node.TEXT_NODE:
                        case Node.CDATA_SECTION_NODE:
                        case Node.COMMENT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            nodeToXml(out, kid, 1);
                            break;
                        default:
                            throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
                    }
                }
            }
            else {
                for (int walk = 0; walk < kids.getLength(); walk++) {
                    Node kid = kids.item(walk);
                    switch (kid.getNodeType()) {
                        case Node.TEXT_NODE:
                            out.add(eventFactory.createCharacters(Utils.stripNonPrinting(kid.getTextContent())));
                            break;
                        case Node.CDATA_SECTION_NODE:
                            out.add(eventFactory.createCData(Utils.stripNonPrinting(kid.getTextContent())));
                            break;
                        case Node.ATTRIBUTE_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            nodeToXml(out, kid, 0);
                            break;
                        default:
                            throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
                    }
                }
            }
            out.add(eventFactory.createEndElement(prefix, uri, localName));
            if (fromMapping) out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndDocument());
            out.flush();
        }
        catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private void nodeToXml(XMLEventWriter out, Node node, int level) throws XMLStreamException {
        boolean fromMapping = level > 0;
        if (node.getLocalName() == null) return;
        List<Attribute> attributes = getAttributes(node);
        String indentString = fromMapping ? indentString(level) : null;
        if (indentString != null) out.add(eventFactory.createCharacters(indentString));
        out.add(eventFactory.createStartElement(
                node.getPrefix() == null ? "" : node.getPrefix(),
                node.getNamespaceURI() == null ? "" : node.getNamespaceURI(),
                node.getLocalName(),
                fromMapping ? attributes.iterator() : null,
                null
        ));
        NodeList kids = node.getChildNodes();
        boolean nodeHasSubelement = false;
        for (int walk = 0; walk < kids.getLength(); walk++) {
            if (kids.item(walk).getNodeType() == Node.ELEMENT_NODE) {
                nodeHasSubelement = true;
                break;
            }
        }
        if (nodeHasSubelement && fromMapping) out.add(eventFactory.createCharacters("\n"));
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.TEXT_NODE:
                 out.add(eventFactory.createCharacters(Utils.stripNonPrinting(kid.getTextContent())));
                    break;
                case Node.CDATA_SECTION_NODE:
                   out.add(eventFactory.createCData(Utils.stripNonPrinting(kid.getTextContent())));
                    break;
                case Node.ATTRIBUTE_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    nodeToXml(out, kid, fromMapping ? level + 1 : 0);
                    break;
            }
        }
        if (nodeHasSubelement && indentString != null) out.add(eventFactory.createCharacters(indentString));
        out.add(eventFactory.createEndElement(
                node.getPrefix() == null ? "" : node.getPrefix(),
                node.getNamespaceURI() == null ? "" : node.getNamespaceURI(),
                node.getLocalName()
        ));
        if (fromMapping) out.add(eventFactory.createCharacters("\n"));
    }

    private List<Attribute> getAttributes(Node node) {
        NamedNodeMap nodeAttributes = node.getAttributes();
        List<Attribute> attributes = new ArrayList<Attribute>();
        for (int walk = 0; walk < nodeAttributes.getLength(); walk++) {
            Node attrItem = nodeAttributes.item(walk);
            if (attrItem.getPrefix() == null || attrItem.getPrefix().isEmpty()) {
                attributes.add(eventFactory.createAttribute(attrItem.getNodeName(), attrItem.getNodeValue()));
            }
            else {
                attributes.add(eventFactory.createAttribute(
                        attrItem.getPrefix(), attrItem.getNamespaceURI(), attrItem.getLocalName(),
                        attrItem.getNodeValue()
                ));
            }
        }
        return attributes;
    }

    private String indentString(int level) {
        if (level >= indentStrings.size()) {
            StringBuilder indentBuilder = new StringBuilder(level * 4);
            for (int walk = 0; walk < level + 1; walk++) {
                if (walk == indentStrings.size()) indentStrings.add(indentBuilder.toString());
                indentBuilder.append("    ");
            }
        }
        return indentStrings.get(level);
    }

    private void gatherNamespaces(Node node, Map<String, String> namespaces) {
        if (node.getPrefix() != null && node.getNamespaceURI() != null) {
            namespaces.put(node.getPrefix(), node.getNamespaceURI());
        }
        if (node instanceof Element) {
            Element element = (Element) node;
            NamedNodeMap attrs = element.getAttributes();
            for (int walk = 0; walk < attrs.getLength(); walk++) {
                if (attrs.item(walk).getPrefix() == null) continue;
                namespaces.put(attrs.item(walk).getPrefix(), attrs.item(walk).getNamespaceURI());
            }
        }
        NodeList list = node.getChildNodes();
        for (int walk = 0; walk < list.getLength(); walk++) {
            Node sub = list.item(walk);
            gatherNamespaces(sub, namespaces);
        }
    }

}
