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
import eu.delving.groovy.Utils;
import eu.delving.metadata.Path;
import eu.delving.metadata.StringUtil;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.Storage;
import org.codehaus.stax2.ri.evt.Stax2EventAllocatorImpl;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.Storage.RECORD_CONTAINER;

/**
 * Iterate through the xml file, producing groovy node trees wrapped in MetadataRecord instances.
 *
 *

 */

public class MetadataParser {
    private InputStream inputStream;
    private XMLStreamReader input;
    private int recordIndex, recordCount;
    private Map<String, String> namespaces = new TreeMap<String, String>();
    private Path path = Path.create();
    private MetadataRecordFactory factory = new MetadataRecordFactory(namespaces);
    private ProgressListener progressListener;
    private boolean isSourceExhausted;
    private final boolean isAttachingSource;
    private final XMLEventAllocator eventAllocator;

    public MetadataParser(InputStream inputStream, int recordCount) throws XMLStreamException {
        this(inputStream, recordCount, false);
    }

    public MetadataParser(InputStream inputStream, int recordCount, boolean attachSource) throws XMLStreamException {
        this.inputStream = inputStream;
        XMLInputFactory inputFactory = XMLToolFactory.xmlInputFactory();
        this.input = inputFactory.createXMLStreamReader("Metadata", inputStream);

        this.isAttachingSource = attachSource;
        // getEventAllocator() returns null: this.eventAllocator = XMLToolFactory.xmlInputFactory().getEventAllocator();
        this.eventAllocator = attachSource ? new Stax2EventAllocatorImpl() : null;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.prepareFor(recordCount);
    }

    @SuppressWarnings("unchecked")
    public MetadataRecord nextRecord() throws XMLStreamException, IOException, CancelException {
        if (isSourceExhausted) {
            return null;
        }
        MetadataRecord metadataRecord = null;
        GroovyNode node = null;
        StringBuilder value = new StringBuilder();

        StringWriter sourceWriter = null;
        XMLEventWriter eventWriter = null;
        boolean skipInitialWhitespace = true;
        if (isAttachingSource) {
            sourceWriter = new StringWriter(1024);
            eventWriter = XMLToolFactory.xmlOutputFactory().createXMLEventWriter(sourceWriter);
        }

        while (metadataRecord == null) {
            if (isAttachingSource && path.parent() != null) {
                if (!(skipInitialWhitespace && input.getEventType() == XMLEvent.CHARACTERS
                    && input.getText().trim().length() == 0)) {
                    if (!(input.getEventType() == XMLStreamConstants.END_ELEMENT && skipInitialWhitespace)) {
                        eventWriter.add(eventAllocator.allocate(input));
                        skipInitialWhitespace = false;
                    }
                }
            }

            switch (input.getEventType()) {
                case XMLEvent.START_DOCUMENT:
                    break;
                case XMLEvent.START_ELEMENT:
                    path = path.child(Tag.element(input.getName()));
                    boolean recordContainer = path.equals(RECORD_CONTAINER);
                    if (node == null && recordContainer) {
                        node = new GroovyNode(null, "input");
                        int idIndex = -1;
                        for (int walk = 0; walk < input.getAttributeCount(); walk++) {
                            if (Storage.POCKET_ID.equals(input.getAttributeLocalName(walk))) idIndex = walk;
                        }
                        if (idIndex < 0) {
                            throw new IOException("Expected record root to have @id");
                        }
                        node.attributes().put(Storage.POCKET_ID, StringUtil.sanitizeId(input.getAttributeValue(idIndex)));
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
                    if (node != null) value.append(Utils.stripNonPrinting(input.getText()));
                    break;
                case XMLEvent.CDATA:
                    if (node != null) value.append(String.format("<![CDATA[%s]]>", Utils.stripNonPrinting(input.getText())));
                    break;
                case XMLEvent.END_ELEMENT:
                    if (node != null) {
                        if (path.equals(RECORD_CONTAINER)) {
                            // TODO record count is never used
                            metadataRecord = factory.fromGroovyNode(node, recordIndex++, recordCount);
                            if (progressListener != null) {
                                progressListener.setProgress(recordIndex);
                            }
                            node = null;
                        }
                        else {
                            String valueString = value.toString().trim();
                            value.setLength(0);
                            if (!valueString.isEmpty()) node.setNodeValue(valueString);
                            node = node.parent();
                        }
                    }
                    path = path.parent();
                    break;
                case XMLEvent.END_DOCUMENT: {
                    isSourceExhausted = true;
                    break;
                }
            }
            if (!input.hasNext()) {
                inputStream.close();
                break;
            }
            input.next();
        }

        if (isAttachingSource && metadataRecord != null) {
            if (!skipInitialWhitespace) {
                eventWriter.close();
            }
            return MetadataRecord.create(metadataRecord, sourceWriter.toString());
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

    public void setNotExhausted() {
        isSourceExhausted = false;
    }
}