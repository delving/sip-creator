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

package eu.delving.sip.base;

import com.ctc.wstx.stax.WstxInputFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.xml.ValueFilter;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.Storage.ENVELOPE_TAG;
import static eu.delving.sip.files.Storage.RECORD_TAG;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Harvest data files from OAI-PMH targets
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Harvestor implements Runnable {
    private static final int CONNECTION_TIMEOUT = 1000*60*5;
    private static final Path RECORD_ROOT = new Path("/OAI-PMH/ListRecords/record");
    private static final Path ERROR = new Path("/OAI-PMH/error");
    private static final Path RESUMPTION_TOKEN = new Path("/OAI-PMH/ListRecords/resumptionToken");
    private Logger log = Logger.getLogger(getClass());
    private XMLInputFactory inputFactory = WstxInputFactory.newInstance();
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private HttpClient httpClient;
    private OutputStream outputStream;
    private XMLEventWriter out;
    private NamespaceCollector namespaceCollector = new NamespaceCollector();
    private Context context;
    private int recordCount;
    private boolean cancelled;
    private Listener listener;
    private String dataSetSpec;
    private File tempFile;

    /**
     * Subscribe to the progress of the harvestor.
     */
    public interface Listener {

        /**
         * The harvesting process is finished.
         *
         * @param cancelled True if cancelled.
         */
        void finished(boolean cancelled);

        /**
         * Inform about the number of processed records.
         *
         * @param count The record count.
         */
        void progress(int count);

        /**
         * Notify the user.
         *
         * @param message The message.
         */
        void tellUser(String message);

        /**
         * Error while harvesting.
         *
         * @param message   The description of the error.
         * @param exception The thrown exception.
         */
        void failed(String message, Exception exception);

        /**
         * Error while harvesting.
         *
         * @param message The description of the error.
         */
        void failed(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Context {

        File outputFile();

        String harvestUrl();

        String harvestPrefix();

        String harvestSpec();
    }

    public Harvestor(String dataSetSpec, Context context) {
        this.dataSetSpec = dataSetSpec;
        this.context = context;
        HttpParams timeoutParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(timeoutParams, CONNECTION_TIMEOUT);
        HttpConnectionParams.setConnectionTimeout(timeoutParams, CONNECTION_TIMEOUT);
        httpClient = new DefaultHttpClient(timeoutParams);
    }

    public int getRecordCount() {
        return recordCount;
    }

    @Override
    public void run() {
        if (!okURL(context.harvestUrl(), "Harvest Base URL")) return;
        if (!okValue(context.harvestPrefix(), "Harvest Metadata Prefix")) return;
        if (!prepareOutput()) return;
        try {
            listener.progress(recordCount);
            HttpEntity fetchedRecords = fetchFirstEntity();
            String resumptionToken = saveRecords(fetchedRecords, out);
            while (isValidResumptionToken(resumptionToken) && recordCount > 0 && !cancelled) {
                EntityUtils.consume(fetchedRecords);
                listener.progress(recordCount);
                fetchedRecords = fetchNextEntity(resumptionToken);
                resumptionToken = saveRecords(fetchedRecords, out);
                if (!isValidResumptionToken(resumptionToken) && recordCount > 0) {
                    EntityUtils.consume(fetchedRecords);
                }
            }
            if (recordCount > 0) {
                finishOutput(cancelled);
                listener.tellUser(cancelled ? "Cancelled!" : String.format("Harvest complete, %d records", recordCount));
            }
            else {
                outputStream.close();
                if (!tempFile.delete()) {
                    listener.tellUser("Unable to delete output file");
                }
                listener.finished(cancelled);
            }
        }
        catch (UnknownHostException e) {
            listener.failed(String.format("Error opening '%s' : %s", context.harvestUrl(), e.getMessage()), e);
        }
        catch (IOException e) {
            log.error(String.format("Unable to complete harvest of %s because of a streaming problem", context.harvestUrl()), e);
            listener.tellUser("Unable to complete harvest");
            listener.failed("Streaming problem", e);
        }
        catch (XMLStreamException e) {
            log.error(String.format("Unable to complete harvest of %s because of an xml problem", context.harvestUrl()), e);
            listener.failed("Invalid data received", e);
        }
    }

    private boolean isValidResumptionToken(String resumptionToken) {
        return resumptionToken != null && !resumptionToken.trim().isEmpty();
    }

    private String saveRecords(HttpEntity fetchedRecords, XMLEventWriter out) throws IOException, XMLStreamException {
        InputStream inputStream = fetchedRecords.getContent();
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        Path path = new Path();
        StringBuilder tokenBuilder = null;
        StringBuilder errorBuilder = null;
        String tokenValue = null;
        List<XMLEvent> recordEvents = new ArrayList<XMLEvent>();
        boolean finished = false;
        while (!finished) {
            XMLEvent event = in.nextEvent();
            switch (event.getEventType()) {
                case XMLEvent.START_ELEMENT:
                    StartElement start = event.asStartElement();
                    path.push(Tag.create(start.getName().getPrefix(), start.getName().getLocalPart()));
                    if (!recordEvents.isEmpty()) {
                        recordEvents.add(event);
                    }
                    else if (path.equals(RECORD_ROOT)) {
                        if (namespaceCollector != null) {
                            namespaceCollector.gatherFrom(start);
                            out.add(eventFactory.createStartElement("", "", ENVELOPE_TAG, null, namespaceCollector.iterator()));
                            out.add(eventFactory.createCharacters("\n"));
                            namespaceCollector = null;
                        }
                        recordEvents.add(eventFactory.createCharacters("\n")); // flag that record has started
                    }
                    else if (path.equals(RESUMPTION_TOKEN)) {
                        tokenBuilder = new StringBuilder();
                        tokenValue = null;
                    }
                    else if (path.equals(ERROR)) {
                        errorBuilder = new StringBuilder();
                    }
                    else if (namespaceCollector != null) {
                        namespaceCollector.gatherFrom(start);
                    }
                    break;
                case XMLEvent.END_ELEMENT:
                    if (!recordEvents.isEmpty()) {
                        if (path.equals(RECORD_ROOT)) {
                            out.add(eventFactory.createStartElement("", "", RECORD_TAG, null, null));
                            for (XMLEvent saved : recordEvents) {
                                out.add(saved);
                            }
                            out.add(eventFactory.createEndElement("", "", RECORD_TAG));
                            out.add(eventFactory.createCharacters("\n"));
                            recordEvents.clear();
                            tokenBuilder = null;
                            recordCount++;
                        }
                        else {
                            recordEvents.add(event);
                            recordEvents.add(eventFactory.createCharacters("\n"));
                        }
                    }
                    else if (path.equals(RESUMPTION_TOKEN) && tokenBuilder != null) {
                        tokenValue = tokenBuilder.toString();
                        tokenBuilder = null;
                    }
                    else if (path.equals(ERROR) && errorBuilder != null) {
                        listener.failed(String.format("OAI-PMH Error: %s", errorBuilder), null);
                    }
                    path.pop();
                    break;
                case XMLEvent.END_DOCUMENT:
                    finished = true;
                    break;
                case XMLEvent.CHARACTERS:
                case XMLEvent.CDATA:
                    if (!recordEvents.isEmpty()) {
                        String string = ValueFilter.filter(event.asCharacters().getData());
                        if (!string.isEmpty()) {
                            recordEvents.add(eventFactory.createCharacters(string));
                        }
                    }
                    else if (tokenBuilder != null) {
                        tokenBuilder.append(event.asCharacters().getData());
                    }
                    else if (errorBuilder != null) {
                        errorBuilder.append(event.asCharacters().getData());
                    }
                    break;
                default:
                    if (!recordEvents.isEmpty()) {
                        recordEvents.add(event);
                    }
                    break;
            }
        }
        return tokenValue;
    }

    private HttpEntity fetchFirstEntity() throws IOException {
        String url = String.format(
                "%s?verb=ListRecords&metadataPrefix=%s",
                context.harvestUrl(),
                context.harvestPrefix()
        );
        if (context.harvestSpec() != null && !context.harvestSpec().isEmpty()) {
            url += String.format("&set=%s", context.harvestSpec());
        }
        log.info(String.format("Harvesting from '%s'", url));
        return doGet(url);
    }

    private HttpEntity fetchNextEntity(String resumptionToken) throws IOException {
        String url = String.format(
                "%s?verb=ListRecords&resumptionToken=%s",
                context.harvestUrl(),
                URLEncoder.encode(resumptionToken, "UTF-8")
        );
        return doGet(url);
    }

    private HttpEntity doGet(String url) throws IOException {
        HttpGet get = new HttpGet(url);
//        get.setHeader("Accept", "text/xml");
        HttpResponse response = httpClient.execute(get);
        switch (Code.from(response)) {
            case OK:
                break;
            case UNAUTHORIZED:
                break;
            case SYSTEM_ERROR:
            case UNKNOWN_RESPONSE:
//                    log.warn("Unable to download source. HTTP response " + response.getStatusLine().getReasonPhrase());
//                    context.tellUser("Unable to download data set"); // todo: tell them why
                break;
        }
        return response.getEntity();
    }

    private boolean prepareOutput() {
        try {
            tempFile = File.createTempFile(context.outputFile().getName(), ".tmp");
            log.info(String.format("Opening temporary output file '%s'", tempFile));
            outputStream = new FileOutputStream(tempFile);
            out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            out.add(eventFactory.createStartDocument());
            out.add(eventFactory.createCharacters("\n"));
            return true;
        }
        catch (FileNotFoundException e) {
            listener.tellUser("Unable to create file to receive harvested data");
            listener.failed("Can't create temp file", e);
            return false;
        }
        catch (XMLStreamException e) {
            listener.tellUser("Unable to stream to file to receive harvested data");
            listener.failed("XML Stream error", e);
            return false;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            log.error(String.format("Error creating temp file '%s'%n", tempFile));
            listener.failed("Error creating temp file", e);
            return false;
        }
    }

    private void finishOutput(boolean cancelled) throws XMLStreamException, IOException {
        tempFile.deleteOnExit();
        out.add(eventFactory.createEndElement("", "", ENVELOPE_TAG));
        out.add(eventFactory.createCharacters("\n"));
        out.add(eventFactory.createEndDocument());
        out.flush();
        outputStream.close();
        if (cancelled) {
            listener.finished(true);
            return;
        }
        String message = String.format("Copying temp file %s to %s", tempFile, context.outputFile());
        log.info(message);
        FileUtils.moveFile(tempFile, context.outputFile());
        listener.finished(false);
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

    private enum Code {
        OK(SC_OK),
        UNAUTHORIZED(SC_UNAUTHORIZED),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR),
        UNKNOWN_RESPONSE(-1);

        private int httpCode;

        Code(int httpCode) {
            this.httpCode = httpCode;
        }

        static Code from(HttpResponse httpResponse) {
            int httpCode = httpResponse.getStatusLine().getStatusCode();
            for (Code code : values()) {
                if (code.httpCode == httpCode) {
                    return code;
                }
            }
            return UNKNOWN_RESPONSE;
        }
    }

    private boolean okValue(String string, String description) {
        boolean ok = string != null && !string.trim().isEmpty();
        if (!ok) {
            listener.tellUser("Missing value for " + description);
        }
        return ok;
    }

    private boolean okURL(String url, String description) {
        if (!okValue(url, description)) {
            return false;
        }
        try {
            new URL(url);
            return true;
        }
        catch (MalformedURLException e) {
            listener.tellUser("Malformed URL: " + url);
            return false;
        }
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        listener.progress(recordCount);
    }

    public String getDataSetSpec() {
        return dataSetSpec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Harvestor harvestor = (Harvestor) o;
        return dataSetSpec.equals(harvestor.dataSetSpec);
    }

    @Override
    public int hashCode() {
        return dataSetSpec.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s %s - %s:%s (%d records)", cancelled ? "CANCELLED!" : "", dataSetSpec, context.harvestUrl(), context.harvestPrefix(), getRecordCount());
    }
}
