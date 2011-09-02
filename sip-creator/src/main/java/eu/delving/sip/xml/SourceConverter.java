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
import eu.delving.sip.ProgressListener;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.FileStore.ENVELOPE_TAG;
import static eu.delving.sip.files.FileStore.RECORD_TAG;

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
    private int recordCount;
    private ProgressListener progressListener;

    public SourceConverter(Path recordRootPath, int recordCount) {
        this.recordRootPath = recordRootPath;
        this.recordCount = recordCount;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void parse(InputStream inputStream, OutputStream outputStream) throws XMLStreamException, IOException {
        if (progressListener != null) progressListener.prepareFor(recordCount);
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        Path path = new Path();
        boolean withinRecord = false;
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
                        path.push(Tag.create(start.getName().getPrefix(), start.getName().getLocalPart()));
                        if (withinRecord) {
                            out.add(event);
                        }
                        else if (path.equals(recordRootPath)) {
                            withinRecord = true;
                            if (namespaceCollector != null) {
                                namespaceCollector.gatherFrom(start);
                                out.add(eventFactory.createStartElement("", "", ENVELOPE_TAG, null, namespaceCollector.iterator()));
                                out.add(eventFactory.createCharacters("\n"));
                                namespaceCollector = null;
                            }
                            out.add(eventFactory.createStartElement("", "", RECORD_TAG));
                            out.add(eventFactory.createCharacters("\n"));
                            if (progressListener != null) progressListener.setProgress(count);
                        }
                        else if (namespaceCollector != null) {
                            namespaceCollector.gatherFrom(start);
                        }
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (withinRecord) {
                            if (path.equals(recordRootPath)) {
                                withinRecord = false;
                                count++;
                                out.add(eventFactory.createEndElement("", "", RECORD_TAG));
                            }
                            else {
                                out.add(event);
                            }
                            out.add(eventFactory.createCharacters("\n"));
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
                        if (withinRecord) {
                            String string = ValueFilter.filter(event.asCharacters().getData());
                            if (!string.isEmpty()) {
                                out.add(eventFactory.createCharacters(string));
                            }
                        }
                        break;
                    default:
                        if (withinRecord) {
                            out.add(event);
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
