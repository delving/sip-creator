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
import eu.delving.metadata.Hasher;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.stats.Stats;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.security.DigestOutputStream;
import java.util.*;
import java.util.regex.Pattern;

import static eu.delving.sip.files.Storage.ENVELOPE_TAG;
import static eu.delving.sip.files.Storage.FileType.SOURCE;
import static eu.delving.sip.files.Storage.RECORD_TAG;
import static eu.delving.sip.files.StorageHelper.*;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.apache.commons.io.FileUtils.moveFile;

/**
 * Create an output file with our standard record wrapping from a file of otherwise wrapped records, given by
 * the recordRootPath.  There is a potential regex-based conversion of the ID values on the way.  The uniqueness
 * of the ID is checked.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SourceConverter implements Work.DataSetWork, Work.LongTermWork {
    public static final String CONVERTER_DELIMITER = ":::";
    //    public static final String ANONYMOUS_RECORDS_PROPERTY = "anonymousRecords";
    private static final String XSI_SCHEMA = "http://www.w3.org/2001/XMLSchema-instance";
    private static final Pattern TO_UNDERSCORE = Pattern.compile("[:]");
    private XMLInputFactory inputFactory = XMLToolFactory.xmlInputFactory();
    private XMLOutputFactory outputFactory = XMLToolFactory.xmlOutputFactory();
    private XMLEventFactory eventFactory = XMLToolFactory.xmlEventFactory();
    private Path recordRootPath;
    private Path uniqueElementPath;
    private int recordCount, totalRecords;//, anonymousRecords;
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
    private DataSet dataSet;
    private Runnable work;

    public SourceConverter(DataSet dataSet, Runnable finished) {
        this.dataSet = dataSet;
        this.work = finished;
    }

    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    @Override
    public Job getJob() {
        return Job.CONVERT_SOURCE;
    }

    @Override
    public void run() {
        try {
            interpretHints();
            if (!dataSet.isRecentlyImported()) {
                throw new StorageException("Import to source would be redundant, since source is newer");
            }
            Stats stats = dataSet.getStats(false, null);
            if (stats == null) {
                throw new StorageException("No analysis stats so conversion doesn't trust the record count");
            }
            Hasher hasher = new Hasher();
            DigestOutputStream digestOut = hasher.createDigestOutputStream(zipOut(dataSet.sourceOutput()));
            parse(dataSet.openImportedInputStream(), digestOut, stats.namespaces); // streams closed within parse()
            File hashedSource = new File(dataSet.sourceOutput().getParentFile(), hasher.prefixFileName(SOURCE.getName()));
            if (hashedSource.exists()) deleteQuietly(hashedSource);
            moveFile(dataSet.sourceOutput(), hashedSource);
            deleteQuietly(statsFile(dataSet.sourceOutput().getParentFile(), true, null));
        }
        catch (StorageException e) {
            deleteQuietly(dataSet.sourceOutput());
            progressListener.getFeedback().alert("Conversion failed: " + e.getMessage(), e);
        }
        catch (Exception e) {
            deleteQuietly(dataSet.sourceOutput());
            progressListener.getFeedback().alert("Conversion failed, unexpected: " + e.getMessage(), e);
        }
        finally {
            if (work != null) work.run();
        }
    }

    public void interpretHints() throws StorageException {
        Map<String, String> hints = dataSet.getHints();
        this.recordRootPath = getRecordRoot(hints);
        this.totalRecords = getRecordCount(hints);
        this.uniqueElementPath = getUniqueElement(hints);
        this.maxUniqueValueLength = getMaxUniqueValueLength(hints);
        String uniqueConverter = getUniqueValueConverter(hints);
        if (uniqueConverter != null) {
            int divider = uniqueConverter.indexOf(CONVERTER_DELIMITER);
            if (divider > 0) {
                converterPattern = Pattern.compile(uniqueConverter.substring(0, divider));
                converterReplacement = uniqueConverter.substring(divider + CONVERTER_DELIMITER.length());
            }
        }
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        this.progressListener.setProgressMessage("Converting to standard form");
    }

    public void parse(InputStream inputStream, OutputStream outputStream, Map<String, String> namespaces) throws XMLStreamException, IOException {
        progressListener.prepareFor(totalRecords);
//        anonymousRecords = Integer.parseInt(System.getProperty(ANONYMOUS_RECORDS_PROPERTY, "0"));
        Path path = Path.create();
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        try {
            processEvents:
            while (!finished) {
                XMLEvent event = in.nextEvent();
                switch (event.getEventType()) {
                    case XMLEvent.START_DOCUMENT:
                        out.add(eventFactory.createStartDocument());
                        out.add(eventFactory.createCharacters("\n"));
                        List<Namespace> nslist = new ArrayList<Namespace>();
                        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                            if (entry.getKey().isEmpty() || entry.getValue().trim().isEmpty()) continue;
                            if (XSI_SCHEMA.equals(entry.getValue())) continue;
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
                        progressListener.setProgress(recordCount);
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
                                    unique = StringUtils.join(lines, "");
                                }
                                Iterator<String> iterator = lines.iterator();
                                while (iterator.hasNext()) {
                                    String line = iterator.next();
                                    if (line.trim().isEmpty()) iterator.remove();
                                }
                                boolean addEndTag = true;
                                switch (lines.size()) {
                                    case 0:
                                        if (eventBuffer.get(eventBuffer.size() - 1).isStartElement()) {
                                            eventBuffer.remove(eventBuffer.size() - 1); // remove the start event
                                            addEndTag = false;
                                        }
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
                                if (addEndTag) {
                                    eventBuffer.add(end);
                                    eventBuffer.add(eventFactory.createCharacters("\n"));
                                }
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
                        break processEvents;
                    case XMLEvent.CHARACTERS:
                    case XMLEvent.CDATA:
                        if (recordEvents) extractLines(event.asCharacters().getData());
                        break;
                }
            }
        }
        catch (CancelException e) {
            progressListener.getFeedback().alert("Conversion cancelled");
        }
        catch (StorageException e) {
            progressListener.getFeedback().alert("Conversion failed: " + e.getMessage(), e);
        }
        finally {
            if (uniqueRepeatCount > 0) {
                progressListener.getFeedback().alert(String.format("Uniqueness violations : " + uniqueRepeatCount));
            }
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    private void outputRecord(XMLEventWriter out) throws XMLStreamException, StorageException {
//        if (anonymousRecords == 0 || recordCount < anonymousRecords) {
        String uniqueValue = getUniqueValue();
        if (!uniqueValue.isEmpty()) {
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
        }
//        }
        clearEvents();
    }

    private String getUniqueValue() throws StorageException {
        String trimmed = unique.trim().replaceAll(":", "-");
        String modified = converterPattern != null ? converterPattern.matcher(trimmed).replaceFirst(converterReplacement) : trimmed;
        if (modified.length() > maxUniqueValueLength) {
            throw new StorageException("Unique value too large: " + unique);
        }
        return TO_UNDERSCORE.matcher(modified).replaceAll("_");
    }

    private void handleStartElement(Path path, boolean followsStart) {
        if (recordEvents) {
            if (followsStart) eventBuffer.add(eventFactory.createCharacters("\n"));
            if (uniqueElementPath.peek().isAttribute()) handleAttributes(path, start);
            eventBuffer.add(eventFactory.createStartElement(start.getName(), filteredAttributes(), null)); // remove namespaces
        }
        else if (path.equals(recordRootPath)) {
            recordEvents = true;
            if (uniqueElementPath.peek().isAttribute()) handleAttributes(path, start);
        }
    }

    private Iterator<Attribute> filteredAttributes() {
        Iterator aw = start.getAttributes();
        List<Attribute> attributes = new ArrayList<Attribute>();
        while (aw.hasNext()) {
            Attribute attribute = (Attribute) aw.next();
            if (XSI_SCHEMA.equals(attribute.getName().getNamespaceURI())) continue;
            attributes.add(attribute);
        }
        return attributes.iterator();
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
//            if (anonymousRecords > 0) clean = anonymize(clean);
            if (!clean.isEmpty()) lines.add(clean);
        }
    }

    private String anonymize(String string) {
        if (string.startsWith("http")) { // preserve the beginning and the end
            int slashSlash = string.indexOf("//");
            int nextSlash = string.indexOf("/", slashSlash + 1);
            int finalSlash = string.lastIndexOf("/");
            if (slashSlash > 0 && nextSlash > 0 && finalSlash > 0) {
                return String.format(
                        "%s%s%s%s",
                        string.substring(0, slashSlash),
                        anonymizeString(string.substring(slashSlash, nextSlash)),
                        anonymizeString(string.substring(nextSlash, finalSlash)),
                        string.substring(finalSlash)
                );
            }
        }
        return anonymizeString(string);
    }

    private String anonymizeString(String string) {
        if (moreNumbersThanLetters(string)) return string;
        StringBuilder out = new StringBuilder(string.length());
        Random random = new Random(string.hashCode());
        for (char c : string.toCharArray()) {
            if (Character.isLowerCase(c)) {
                out.append((char) ('a' + (Math.abs(random.nextInt()) % 26)));
            }
            else if (Character.isUpperCase(c)) {
                out.append((char) ('A' + (Math.abs(random.nextInt()) % 26)));
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean moreNumbersThanLetters(String string) {
        int letters = 0, numbers = 0;
        for (char c : string.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
            }
            else if (Character.isDigit(c)) {
                numbers++;
            }
        }
        return numbers > letters;
    }

    private void clearEvents() {
        recordEvents = false;
        eventBuffer.clear();
    }

}
