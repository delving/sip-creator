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

package eu.delving.groovy;

import com.ctc.wstx.exc.WstxParsingException;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * When the MetadataRecord instances are not coming from the parse of an input file
 * using the MetadataParser, they can be produced one by one using the metadataRecordFrom method, which
 * first cleverly wraps the record and then parses it.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MetadataRecordFactory {
    private XMLInputFactory2 inputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
    private DocumentBuilder documentBuilder;
    private Map<String, String> namespaces;

    public MetadataRecordFactory(Map<String, String> namespaces) {
        this.namespaces = namespaces;
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        inputFactory.configureForSpeed();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            documentBuilder = factory.newDocumentBuilder();
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException("Parser config?",e);
        }
    }

    public MetadataRecord fromGroovyNode(GroovyNode rootNode, int recordNumber, int recordCount) {
        return new MetadataRecord(rootNode, recordNumber, recordCount);
    }

    public Node nodeFromXml(String id, String recordContents) throws IOException, SAXException {
        String recordString = createCompleteRecordString(id, recordContents);
        Document document = documentBuilder.parse(new InputSource(new StringReader(recordString)));
        return document.getDocumentElement();
    }

    public MetadataRecord metadataRecordFrom(String id, String recordContents) throws XMLStreamException {
        String recordString = createCompleteRecordString(id, recordContents);
        try {
            Reader reader = new StringReader(recordString);
            XMLStreamReader2 input = (XMLStreamReader2) inputFactory.createXMLStreamReader(reader);
            GroovyNode rootNode = null, node = null;
            StringBuilder value = new StringBuilder();
            while (true) {
                switch (input.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        break;
                    case XMLEvent.START_ELEMENT:
                        if (node == null) {
                            rootNode = node = new GroovyNode(null, "input");
                        }
                        else {
                            node = new GroovyNode(node, input.getNamespaceURI(), input.getLocalName(), input.getPrefix());
                        }
                        if (input.getAttributeCount() > 0) {
                            for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                                QName attributeName = input.getAttributeName(walk);
                                if (attributeName.getPrefix() == null || attributeName.getPrefix().isEmpty()) {
                                    node.attributes().put(attributeName.getLocalPart(), input.getAttributeValue(walk));
                                }
                                else {
                                    node.attributes().put(String.format("%s:%s", attributeName.getPrefix(), attributeName.getLocalPart()), input.getAttributeValue(walk));
                                }
                            }
                        }
                        value.setLength(0);
                        break;
                    case XMLEvent.CHARACTERS:
                        value.append(input.getText());
                        break;
                    case XMLEvent.CDATA:
                        value.append(String.format("<![CDATA[%s]]>", input.getText()));
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (node == null) throw new RuntimeException("Node cannot be null");
                        String valueString = value.toString().trim();
                        value.setLength(0);
                        if (valueString.length() > 0) node.setNodeValue(valueString);
                        node = node.parent();
                        break;
                    case XMLEvent.END_DOCUMENT: {
                        break;
                    }
                }
                if (!input.hasNext()) {
                    break;
                }
                input.next();
            }
            return new MetadataRecord(rootNode, -1, -1);
        }
        catch (WstxParsingException e) {
            throw new XMLStreamException("Problem parsing record:\n" + recordString, e);
        }
    }

    private String createCompleteRecordString(String id, String xmlRecord) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\"?>\n");
        out.append("<record");
        for (Map.Entry<String, String> namespace : namespaces.entrySet()) {
            if (namespace.getKey().isEmpty()) {
                out.append(String.format(" xmlns=\"%s\"", namespace.getValue()));
            }
            else {
                out.append(String.format(" xmlns:%s=\"%s\"", namespace.getKey(), namespace.getValue()));
            }
        }
        out.append(String.format(" id=\"%s\"", id));
        out.append(">\n");
        out.append(xmlRecord);
        out.append("\n</record>");
        return out.toString();
    }
}
