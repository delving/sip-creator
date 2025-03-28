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

import com.ctc.wstx.exc.WstxParsingException;
import eu.delving.XMLToolFactory;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * When the MetadataRecord instances are not coming from the parse of an input file
 * using the MetadataParser, they can be produced one by one using the metadataRecordFrom method, which
 * first cleverly wraps the record and then parses it.
 *
 *
 */

public class MetadataRecordFactory {
    private XMLInputFactory inputFactory = XMLToolFactory.xmlInputFactory();
    private DocumentBuilder documentBuilder;
    private Map<String, String> namespaces;

    public MetadataRecordFactory(Map<String, String> namespaces) {
        this.namespaces = namespaces;
        try {
            documentBuilder = XMLToolFactory.documentBuilderFactory().newDocumentBuilder();
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException("Parser config?", e);
        }
    }

    public MetadataRecord fromGroovyNode(GroovyNode rootNode, int recordNumber, int recordCount) {
        // TODO record count is never used
        return MetadataRecord.create(rootNode, recordNumber, recordCount);
    }

    public MetadataRecord metadataRecordFrom(String recordContents) throws XMLStreamException {
        try {
            Reader reader = new StringReader(recordContents);
            XMLStreamReader2 input = (XMLStreamReader2) inputFactory.createXMLStreamReader(reader);
            GroovyNode rootNode = null, node = null;
            StringBuilder value = new StringBuilder();
            while (true) {
                switch (input.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        break;
                    case XMLEvent.START_ELEMENT:
                        node = new GroovyNode(node, input.getNamespaceURI(), input.getLocalName(), input.getPrefix());
                        if (rootNode == null) {
                            rootNode = node;
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
            return MetadataRecord.create(rootNode, -1, -1);
        }
        catch (WstxParsingException e) {
            throw new XMLStreamException("Problem parsing record:\n" + recordContents, e);
        }
    }

}
