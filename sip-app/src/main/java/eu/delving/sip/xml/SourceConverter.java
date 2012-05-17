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

import com.ctc.wstx.stax.WstxInputFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.Feedback;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.regex.Pattern;

import static eu.delving.sip.files.Storage.ENVELOPE_TAG;
import static eu.delving.sip.files.Storage.RECORD_TAG;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SourceConverter {
    public static final String CONVERTER_DELIMITER = ":::";
    private static final Pattern TO_UNDERSCORE = Pattern.compile("[:]");
    private Feedback feedback;
    private XMLInputFactory inputFactory = WstxInputFactory.newInstance();
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private Path recordRootPath;
    private Path uniqueElementPath;
    private Map<String, String> namespaces;
    private int recordCount, totalRecords;
    private ProgressListener progressListener;
    private String unique;
    private StartElement start;
    private boolean recordEvents;
    private List<XMLEvent> eventBuffer = new ArrayList<XMLEvent>();
    private List<String> lines = new ArrayList<String>();
    private boolean finished = false;
    private Set<String> uniqueness = new HashSet<String>();
    private Pattern converterPattern;
    private String converterReplacement;
    private int uniqueRepeatCount;
    private int maxUniqueValueLength;

    public SourceConverter(Feedback feedback, Path recordRootPath, int totalRecords, Path uniqueElementPath, int maxUniqueValueLength, String uniqueConverter, Map<String, String> namespaces) {
        this.feedback = feedback;
        this.recordRootPath = recordRootPath;
        this.totalRecords = totalRecords;
        this.uniqueElementPath = uniqueElementPath;
        this.maxUniqueValueLength = maxUniqueValueLength;
        this.namespaces = namespaces;
        if (uniqueConverter != null) {
            String delimiter = ":::";
            int divider = uniqueConverter.indexOf(delimiter);
            if (divider > 0) {
                converterPattern = Pattern.compile(uniqueConverter.substring(0, divider));
                converterReplacement = uniqueConverter.substring(divider + delimiter.length());
            }
        }
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void parse(InputStream inputStream, OutputStream outputStream) throws XMLStreamException, IOException {
        if (progressListener != null) progressListener.prepareFor(totalRecords);
        Path path = Path.create();
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        try {
            while (!finished) {
                XMLEvent event = in.nextEvent();
                switch (event.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        out.add(eventFactory.createStartDocument());
                        out.add(eventFactory.createCharacters("\n"));
                        List<Namespace> nslist = new ArrayList<Namespace>();
                        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                            nslist.add(eventFactory.createNamespace(entry.getKey(), entry.getValue()));
                        }
                        out.add(eventFactory.createStartElement("", "", ENVELOPE_TAG, null, nslist.iterator()));
                        out.add(eventFactory.createCharacters("\n"));
                        break;
                    case XMLEvent.START_ELEMENT:
                        boolean followsStart = start != null;
                        start = event.asStartElement();
                        path = path.child(Tag.element(start.getName()));
                        handleStartElement(path, followsStart);
                        if (progressListener != null) progressListener.setProgress(recordCount);
                        break;
                    case XMLEvent.END_ELEMENT:
                        EndElement end = event.asEndElement();
                        if (recordEvents) {
                            if (path.equals(recordRootPath)) {
                                if (unique != null) {
                                    outputRecord(out);
                                    recordCount++;
                                }
                                else {
                                    clearEvents();
                                }
                            }
                            else {
                                if (!uniqueElementPath.peek().isAttribute() && path.equals(uniqueElementPath)) {
                                    unique = StringUtils.join(lines,"");
                                }
                                switch (lines.size()) {
                                    case 0:
                                        break;
                                    case 1:
                                        eventBuffer.add(eventFactory.createCharacters(lines.get(0)));
                                        lines.clear();
                                        break;
                                    default:
                                        Iterator<String> walk = lines.iterator();
                                        while (walk.hasNext()) {
                                            eventBuffer.add(eventFactory.createCharacters(walk.next()));
                                            if (walk.hasNext()) {
                                                eventBuffer.add(end);
                                                eventBuffer.add(eventFactory.createCharacters("\n"));
                                                handleStartElement(path, false);
                                            }
                                        }
                                        lines.clear();
                                        break;
                                }
                                eventBuffer.add(end);
                                eventBuffer.add(eventFactory.createCharacters("\n"));
                            }
                        }
                        start = null;
                        path = path.parent();
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
                        if (recordEvents) extractLines(event.asCharacters().getData());
                        break;
                }
            }
        }
        finally {
            if (progressListener != null) progressListener.finished(finished);
            if (feedback != null) feedback.say(String.format("Uniqueness violations : " + uniqueRepeatCount));
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    private void outputRecord(XMLEventWriter out) throws XMLStreamException {
        String uniqueValue = getUniqueValue();
        if (uniqueness.contains(uniqueValue)) {
            uniqueRepeatCount++;
        }
        else {
            uniqueness.add(uniqueValue);
            Attribute id = eventFactory.createAttribute(Storage.UNIQUE_ATTR, uniqueValue);
            unique = null;
            List<Attribute> attrs = new ArrayList<Attribute>();
            attrs.add(id);
            out.add(eventFactory.createStartElement("", "", RECORD_TAG, attrs.iterator(), null));
            for (XMLEvent bufferedEvent : eventBuffer) out.add(bufferedEvent);
            out.add(eventFactory.createEndElement("", "", RECORD_TAG));
            out.add(eventFactory.createCharacters("\n"));
        }
        clearEvents();
    }

    private String getUniqueValue() {
        String trimmed = unique.trim();
        String modified = converterPattern != null ? converterPattern.matcher(trimmed).replaceFirst(converterReplacement) : trimmed;
        if (modified.length() > maxUniqueValueLength) throw new IllegalArgumentException("Unique value too large: "+unique);
        return TO_UNDERSCORE.matcher(modified).replaceAll("_");
    }

    private void handleStartElement(Path path, boolean followsStart) {
        if (recordEvents) {
            if (followsStart) eventBuffer.add(eventFactory.createCharacters("\n"));
            if (uniqueElementPath.peek().isAttribute()) handleAttributes(path, start);
            eventBuffer.add(eventFactory.createStartElement(start.getName(), start.getAttributes(), null)); // remove namespaces
        }
        else if (path.equals(recordRootPath)) {
            recordEvents = true;
            if (uniqueElementPath.peek().isAttribute()) handleAttributes(path, start);
        }
    }

    private void handleAttributes(Path path, StartElement start) {
        Iterator attrWalk = start.getAttributes();
        while (attrWalk.hasNext()) handleAttribute(path, (Attribute) attrWalk.next());
    }

    private void handleAttribute(Path path, Attribute attr) {
        Path extended = path.child(Tag.attribute(attr.getName()));
        if (extended.equals(uniqueElementPath)) {
            unique = attr.getValue();
        }
        else if (path.equals(recordRootPath)) {
            QName a = attr.getName();
            QName attrName = new QName(a.getNamespaceURI(), "_" + a.getLocalPart(), a.getPrefix());
            eventBuffer.add(eventFactory.createStartElement(attrName, null, null));
            eventBuffer.add(eventFactory.createCharacters(attr.getValue()));
            eventBuffer.add(eventFactory.createEndElement(attrName, null));
            eventBuffer.add(eventFactory.createCharacters("\n"));
        }
    }

    private void extractLines(String value) {
        for (String line : value.split(" *[\n\r]+ *")) {
            if (line.isEmpty()) continue;
            StringBuilder out = new StringBuilder(line.length());
            for (char c : line.toCharArray()) out.append(Character.isWhitespace(c) ? ' ' : c);
            String clean = out.toString().replaceAll(" +", " ").trim();
            if (!clean.isEmpty()) lines.add(clean);
        }
    }

    private void clearEvents() {
        recordEvents = false;
        eventBuffer.clear();
    }
}
