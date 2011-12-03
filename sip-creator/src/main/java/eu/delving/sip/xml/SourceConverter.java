/*
 * Copyright 2011 DELVING BV
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

import com.ctc.wstx.stax.WstxInputFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.metadata.Uniqueness;
import eu.delving.metadata.UniquenessException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.Storage;

import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;

import static eu.delving.sip.files.Storage.ENVELOPE_TAG;
import static eu.delving.sip.files.Storage.RECORD_TAG;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SourceConverter {
    private XMLInputFactory inputFactory = WstxInputFactory.newInstance();
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private Path recordRootPath;
    private Path uniqueElementPath;
    private int recordCount, totalRecords;
    private ProgressListener progressListener;
    private Path path = new Path();
    private StringBuilder uniqueBuilder;
    private String unique;
    private List<XMLEvent> eventBuffer = new ArrayList<XMLEvent>();
    private boolean finished = false;
    private NamespaceCollector namespaceCollector = new NamespaceCollector();
    private final Uniqueness uniqueness = new Uniqueness();

    public SourceConverter(Path recordRootPath, int totalRecords, Path uniqueElementPath) {
        this.recordRootPath = recordRootPath;
        this.totalRecords = totalRecords;
        this.uniqueElementPath = uniqueElementPath;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void parse(InputStream inputStream, OutputStream outputStream) throws XMLStreamException, IOException, UniquenessException {
        if (progressListener != null) progressListener.prepareFor(totalRecords);
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        try {
            while (!finished) {
                XMLEvent event = in.nextEvent();
                switch (event.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        out.add(eventFactory.createStartDocument());
                        out.add(eventFactory.createCharacters("\n"));
                        break;
                    case XMLEvent.START_ELEMENT:
                        StartElement start = event.asStartElement();
                        path.push(Tag.element(start.getName()));
                        if (!eventBuffer.isEmpty()) {
                            if (unique == null && path.equals(uniqueElementPath)) uniqueBuilder = new StringBuilder();
                            eventBuffer.add(start); // includes attributes
                        }
                        else if (path.equals(recordRootPath)) {
                            if (namespaceCollector != null) {
                                namespaceCollector.gatherFrom(start);
                                out.add(eventFactory.createStartElement("", "", ENVELOPE_TAG, null, namespaceCollector.iterator()));
                                out.add(eventFactory.createCharacters("\n"));
                                namespaceCollector = null;
                            }
                            eventBuffer.add(eventFactory.createCharacters("\n")); // nonempty: flag that record has started
                            handleRecordAttributes(start);
                            if (progressListener != null) progressListener.setProgress(recordCount);
                        }
                        else if (namespaceCollector != null) {
                            namespaceCollector.gatherFrom(start);
                        }
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (!eventBuffer.isEmpty()) {
                            if (path.equals(recordRootPath)) {
                                if (unique == null) {
                                    throw new IOException(String.format("Record %d has no unique value at %s", recordCount, uniqueElementPath));
                                }
                                outputRecord(out);
                                recordCount++;
                            }
                            else {
                                if (uniqueBuilder != null && path.equals(uniqueElementPath)) {
                                    unique = uniqueBuilder.toString();
                                    if (uniqueness.isRepeated(unique)) {
                                        throw new UniquenessException(uniqueElementPath, recordCount);
                                    }
                                    uniqueBuilder = null;
                                }
                                eventBuffer.add(event);
                                eventBuffer.add(eventFactory.createCharacters("\n"));
                            }
                        }
                        path.pop();
                        break;
                    case XMLEvent.END_DOCUMENT:
                        out.add(eventFactory.createEndElement("", "", ENVELOPE_TAG));
                        out.add(eventFactory.createCharacters("\n"));
                        out.add(eventFactory.createEndDocument());
                        out.flush();
                        finished = true;
                        break;
                    case XMLEvent.CHARACTERS:
                    case XMLEvent.CDATA:
                        if (!eventBuffer.isEmpty()) {
                            String string = ValueFilter.filter(event.asCharacters().getData());
                            if (!string.isEmpty()) {
                                if (uniqueBuilder != null) uniqueBuilder.append(string);
                                eventBuffer.add(eventFactory.createCharacters(string));
                            }
                        }
                        break;
                }
            }
        }
        finally {
            if (progressListener != null) progressListener.finished(finished);
            inputStream.close();
            outputStream.close();
        }
    }

    private void outputRecord(XMLEventWriter out) throws XMLStreamException {
        out.add(eventFactory.createStartElement("", "", RECORD_TAG, null, null));
        out.add(eventFactory.createCharacters("\n"));
        out.add(eventFactory.createStartElement("", "", Storage.UNIQUE_TAG, null, null));
        out.add(eventFactory.createCharacters(unique));
        out.add(eventFactory.createEndElement("", "", Storage.UNIQUE_TAG, null));
        unique = null;
        for (XMLEvent bufferedEvent : eventBuffer) out.add(bufferedEvent);
        out.add(eventFactory.createEndElement("", "", RECORD_TAG));
        out.add(eventFactory.createCharacters("\n"));
        eventBuffer.clear();
        uniqueBuilder = null;
    }

    private void handleRecordAttributes(StartElement start) {
        Iterator attrWalk = start.getAttributes();
        if (attrWalk.hasNext()) eventBuffer.add(eventFactory.createCharacters("\n"));
        while (attrWalk.hasNext()) handleRecordAttribute((Attribute) attrWalk.next());
    }

    private void handleRecordAttribute(Attribute attr) {
        path.push(Tag.element(attr.getName()));
        addAttributeAsElement(attr);
        if (path.equals(uniqueElementPath) && unique == null) unique = attr.getValue();
        path.pop();
    }

    private void addAttributeAsElement(Attribute attr) {
        eventBuffer.add(eventFactory.createStartElement(attr.getName(), null, null));
        eventBuffer.add(eventFactory.createCharacters(attr.getValue()));
        eventBuffer.add(eventFactory.createEndElement(attr.getName(), null));
        eventBuffer.add(eventFactory.createCharacters("\n"));
    }

    private class NamespaceCollector {
        private Map<String, Namespace> map = new TreeMap<String, Namespace>();

        public void gatherFrom(StartElement start) {
            Iterator walk = start.getNamespaces();
            while (walk.hasNext()) {
                Namespace ns = (Namespace) walk.next();
                map.put(ns.getPrefix(), ns);
            }
        }

        public Iterator iterator() {
            return map.values().iterator();
        }
    }
}
