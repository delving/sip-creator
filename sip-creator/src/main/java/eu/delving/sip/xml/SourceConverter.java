/*
 * Copyright 2010 DELVING BV
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
package eu.delving.sip.xml;

import com.ctc.wstx.stax.WstxInputFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.metadata.Uniqueness;
import eu.delving.metadata.UniquenessException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.Storage;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.Storage.ENVELOPE_TAG;
import static eu.delving.sip.files.Storage.RECORD_TAG;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceConverter {
    private XMLInputFactory inputFactory = WstxInputFactory.newInstance();
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private Path recordRootPath;
    private Path uniqueElementPath;
    private int recordCount;
    private ProgressListener progressListener;
    private final Uniqueness uniqueness = new Uniqueness();

    public SourceConverter(Path recordRootPath, int recordCount, Path uniqueElementPath) {
        this.recordRootPath = recordRootPath;
        this.recordCount = recordCount;
        this.uniqueElementPath = uniqueElementPath;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void parse(InputStream inputStream, OutputStream outputStream) throws XMLStreamException, IOException, UniquenessException {
        if (progressListener != null) progressListener.prepareFor(recordCount);
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        Path path = new Path();
        StringBuilder uniqueBuilder = null;
        String uniqueValue = null;
        List<XMLEvent> recordEvents = new ArrayList<XMLEvent>();
        int count = 0;
        boolean finished = false;
        NamespaceCollector namespaceCollector = new NamespaceCollector();
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
                        path.push(Tag.create(start.getName()));
                        if (!recordEvents.isEmpty()) {
                            if (path.equals(uniqueElementPath) && uniqueValue == null) {
                                uniqueBuilder = new StringBuilder();
                                uniqueValue = null;
                            }
                            recordEvents.add(eventFactory.createStartElement(start.getName(), null, null));
                            Iterator attrWalk = start.getAttributes();
                            while (attrWalk.hasNext()) {
                                Attribute attr = (Attribute) attrWalk.next();
                                path.push(Tag.create(attr.getName()));
                                addAttributeAsElement(recordEvents, attr);
                                if (path.equals(uniqueElementPath) && uniqueValue == null) {
                                    uniqueValue = attr.getValue();
                                }
                                path.pop();
                            }
                        }
                        else if (path.equals(recordRootPath)) {
                            if (namespaceCollector != null) {
                                namespaceCollector.gatherFrom(start);
                                out.add(eventFactory.createStartElement("", "", ENVELOPE_TAG, null, namespaceCollector.iterator()));
                                out.add(eventFactory.createCharacters("\n"));
                                namespaceCollector = null;
                            }
                            recordEvents.add(eventFactory.createCharacters("\n")); // flag that record has started
                            Iterator attrWalk = start.getAttributes();
                            while (attrWalk.hasNext()) {
                                Attribute attr = (Attribute) attrWalk.next();
                                path.push(Tag.create(attr.getName()));
                                addAttributeAsElement(recordEvents, attr);
                                if (path.equals(uniqueElementPath) && uniqueValue == null) {
                                    uniqueValue = attr.getValue();
                                }
                                path.pop();
                            }
                            if (progressListener != null) progressListener.setProgress(count);
                        }
                        else if (namespaceCollector != null) {
                            namespaceCollector.gatherFrom(start);
                        }
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (!recordEvents.isEmpty()) {
                            if (path.equals(recordRootPath)) {
                                if (uniqueValue == null) {
                                    throw new IOException("Record has no unique value"); // todo: maybe discard it, but then record the loss
                                }
                                else {
                                    out.add(eventFactory.createStartElement("", "", RECORD_TAG, null, null));
                                    recordEvents.add(eventFactory.createStartElement("", "", Storage.UNIQUE_TAG, null, null));
                                    recordEvents.add(eventFactory.createCharacters(uniqueValue));
                                    recordEvents.add(eventFactory.createEndElement("", "", Storage.UNIQUE_TAG, null));
                                    recordEvents.add(eventFactory.createCharacters("\n"));
                                    uniqueValue = null;
                                    for (XMLEvent saved : recordEvents) {
                                        out.add(saved);
                                    }
                                    out.add(eventFactory.createEndElement("", "", RECORD_TAG));
                                    out.add(eventFactory.createCharacters("\n"));
                                    recordEvents.clear();
                                    uniqueBuilder = null;
                                    count++;
                                }
                            }
                            else {
                                if (path.equals(uniqueElementPath) && uniqueBuilder != null) {
                                    uniqueValue = uniqueBuilder.toString();
                                    if (uniqueness.isRepeated(uniqueValue)) {
                                        throw new UniquenessException(uniqueElementPath, count);
                                    }
                                    uniqueBuilder = null;
                                }
                                recordEvents.add(event);
                                recordEvents.add(eventFactory.createCharacters("\n"));
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
                        if (!recordEvents.isEmpty()) {
                            String string = ValueFilter.filter(event.asCharacters().getData());
                            if (!string.isEmpty()) {
                                if (uniqueBuilder != null) {
                                    uniqueBuilder.append(string);
                                }
                                recordEvents.add(eventFactory.createCharacters(string));
                            }
                        }
                        break;
                    default:
                        if (!recordEvents.isEmpty()) {
                            recordEvents.add(event);
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

    private void addAttributeAsElement(List<XMLEvent> recordEvents, Attribute attr) {
        recordEvents.add(eventFactory.createStartElement(attr.getName(), null, null));
        recordEvents.add(eventFactory.createCharacters(attr.getValue()));
        recordEvents.add(eventFactory.createEndElement(attr.getName(), null));
        recordEvents.add(eventFactory.createCharacters("\n"));
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
