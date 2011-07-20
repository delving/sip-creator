package eu.europeana.sip.core;

import com.ctc.wstx.exc.WstxParsingException;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * Fabricate metadata records
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MetadataRecordFactory {
    private XMLInputFactory2 inputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
    private Map<String, String> namespaces;

    public MetadataRecordFactory(Map<String, String> namespaces) {
        this.namespaces = namespaces;
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        inputFactory.configureForSpeed();
    }

    public MetadataRecord fromGroovyNode(GroovyNode rootNode, int recordNumber, int recordCount) {
        return new MetadataRecord(rootNode, recordNumber, recordCount);
    }

    public MetadataRecord fromXml(String recordContents) throws XMLStreamException {
        String recordString = createCompleteRecordString(recordContents);
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
                                node.attributes().put(attributeName.getLocalPart(), input.getAttributeValue(walk));
                            }
                        }
                        value.setLength(0);
                        break;
                    case XMLEvent.CHARACTERS:
                    case XMLEvent.CDATA:
                        value.append(input.getText());
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (node == null) {
                            throw new RuntimeException("Node cannot be null");
                        }
                        String valueString = value.toString().replaceAll("\n", " ").replaceAll(" +", " ").trim();
                        value.setLength(0);
                        if (valueString.length() > 0) {
                            node.setValue(valueString);
                        }
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

    private String createCompleteRecordString(String xmlRecord) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\"?>\n");
        out.append("<record");
        for (Map.Entry<String, String> namespace : namespaces.entrySet()) {
            out.append(String.format(" xmlns:%s=\"%s\"", namespace.getKey(), namespace.getValue()));
        }
        out.append(">");
        out.append(xmlRecord);
        out.append("</record>");
        return out.toString();
    }
}
