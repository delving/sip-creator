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
import eu.delving.groovy.GroovyNode;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.MetadataRecordFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.Storage;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Iterate through the xml file, producing groovy node trees wrapped in MetadataRecord instances.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class MetadataParser {
    private InputStream inputStream;
    private XMLStreamReader input;
    private int recordIndex, recordCount;
    private Map<String, String> namespaces = new TreeMap<String, String>();
    private Path path = Path.create();
    private MetadataRecordFactory factory = new MetadataRecordFactory(namespaces);
    private ProgressListener progressListener;

    public MetadataParser(InputStream inputStream, int recordCount) throws XMLStreamException {
        this.inputStream = inputStream;
        this.recordCount = recordCount;
        this.input = XMLToolFactory.xmlInputFactory().createXMLStreamReader("Metadata", inputStream);
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.prepareFor(recordCount);
    }

    @SuppressWarnings("unchecked")
    public synchronized MetadataRecord nextRecord() throws XMLStreamException, IOException, CancelException {
        MetadataRecord metadataRecord = null;
        GroovyNode node = null;
        StringBuilder value = new StringBuilder();
        while (metadataRecord == null) {
            switch (input.getEventType()) {
                case XMLEvent.START_DOCUMENT:
                    break;
                case XMLEvent.START_ELEMENT:
                    path = path.child(Tag.element(input.getName()));
                    if (node == null && path.equals(Storage.RECORD_ROOT)) {
                        node = new GroovyNode(null, "input");
                        if (input.getAttributeCount() != 1 || !Storage.UNIQUE_ATTR.equals(input.getAttributeLocalName(0))) {
                            throw new IOException("Expected record root to have @id");
                        }
                        node.attributes().put(Storage.UNIQUE_ATTR, input.getAttributeValue(0));
                    }
                    else if (node != null) {
                        node = new GroovyNode(node, input.getNamespaceURI(), input.getLocalName(), input.getPrefix());
                        if (!input.getPrefix().isEmpty()) namespaces.put(input.getPrefix(), input.getNamespaceURI());
                        if (input.getAttributeCount() > 0) {
                            for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                                QName qName = input.getAttributeName(walk);
                                String attrName;
                                if (qName.getPrefix() == null || qName.getPrefix().isEmpty()) {
                                    attrName = qName.getLocalPart();
                                }
                                else {
                                    attrName = String.format("%s:%s", qName.getPrefix(), qName.getLocalPart());
                                }
                                node.attributes().put(attrName, input.getAttributeValue(walk));
                            }
                        }
                        value.setLength(0);
                    }
                    break;
                case XMLEvent.CHARACTERS:
                    if (node != null) value.append(input.getText());
                    break;
                case XMLEvent.CDATA:
                    if (node != null) value.append(String.format("<![CDATA[%s]]>", input.getText()));
                    break;
                case XMLEvent.END_ELEMENT:
                    if (node != null) {
                        String valueString = value.toString().trim();
                        value.setLength(0);
                        if (!valueString.isEmpty()) node.setNodeValue(valueString);
                        if (path.equals(Storage.RECORD_ROOT)) {
                            if (node.parent() != null) throw new RuntimeException("Expected to be at root node");
                            metadataRecord = factory.fromGroovyNode(node, recordIndex++, recordCount);
                            progressListener.setProgress(recordIndex);
                            node = null;
                        }
                        else {
                            node = node.parent();
                        }
                    }
                    path = path.parent();
                    break;
                case XMLEvent.END_DOCUMENT: {
                    break;
                }
            }
            if (!input.hasNext()) {
                inputStream.close();
                break;
            }
            input.next();
        }
        return metadataRecord;
    }

    public void close() {
        try {
            input.close();
        }
        catch (XMLStreamException e) {
            e.printStackTrace(); // should never happen
        }
    }
}