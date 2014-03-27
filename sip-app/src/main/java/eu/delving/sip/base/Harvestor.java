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

package eu.delving.sip.base;

import eu.delving.XMLToolFactory;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.DataSet;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.xml.stream.*;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static eu.delving.sip.files.Storage.*;
import static org.apache.http.HttpStatus.*;

/**
 * Harvest a data file from an OAI-PMH target through a series of requests, each with its own
 * resumption token.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Harvestor implements Work.DataSetWork, Work.LongTermWork {
    private static final int CONNECTION_TIMEOUT = 1000 * 60 * 5;
    private static final Path RECORD_ROOT = Path.create("/OAI-PMH/ListRecords/record");
    private static final Path ERROR = Path.create("/OAI-PMH/error");
    private static final Path RESUMPTION_TOKEN = Path.create("/OAI-PMH/ListRecords/resumptionToken");
    private XMLInputFactory inputFactory = XMLToolFactory.xmlInputFactory();
    private XMLOutputFactory outputFactory = XMLToolFactory.xmlOutputFactory();
    private XMLEventFactory eventFactory = XMLToolFactory.xmlEventFactory();
    private HttpClient httpClient;
    private NamespaceCollector namespaceCollector = new NamespaceCollector();
    private Context context;
    private int recordCount;
    private DataSet dataSet;
    private ProgressListener progressListener;

    public interface Context {

        String harvestUrl();

        String harvestPrefix();

        String harvestSpec();
    }

    public Harvestor(DataSet dataSet, Context context) {
        this.dataSet = dataSet;
        this.context = context;
        this.httpClient = HttpClientBuilder.create().disableAutomaticRetries().build();
    }

    @Override
    public Job getJob() {
        return Job.HARVEST;
    }

    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.setProgressMessage(String.format("Harvesting %s", dataSet.getSpec()));
    }

    @Override
    public void run() {
        try {
            if (context.harvestPrefix() == null || context.harvestPrefix().trim().isEmpty()) {
                throw new IllegalArgumentException("Harvest prefix missing");
            }
            new URL(context.harvestUrl()); // throws MalformedUrlException if it is
            OutputStream outputStream = new GZIPOutputStream(new FileOutputStream(dataSet.importedOutput()));
            XMLEventWriter out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            out.add(eventFactory.createStartDocument());
            out.add(eventFactory.createCharacters("\n"));
            progressListener.setProgress(recordCount);
            HttpEntity fetchedRecords = fetchFirstEntity();
            String resumptionToken = saveRecords(fetchedRecords, out);
            while (isValidResumptionToken(resumptionToken) && recordCount > 0) {
                EntityUtils.consume(fetchedRecords);
                progressListener.setProgress(recordCount);
                fetchedRecords = fetchNextEntity(resumptionToken);
                resumptionToken = saveRecords(fetchedRecords, out);
                if (!isValidResumptionToken(resumptionToken) && recordCount > 0) EntityUtils.consume(fetchedRecords);
            }
            out.add(eventFactory.createEndElement("", "", ENVELOPE_TAG));
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndDocument());
            out.flush();
            outputStream.close();
        }
        catch (CancelException e) {
            progressListener.getFeedback().alert("Cancelled harvest of " + context.harvestUrl(), e);
            recordCount = 0;
        }
        catch (Exception e) {
            progressListener.getFeedback().alert(String.format(
                    "Unable to complete harvest of %s because of: %s", context.harvestUrl(), e.getMessage()
            ), e);
            recordCount = 0;
        }
        finally {
            if (recordCount > 0) {
                progressListener.getFeedback().alert(String.format(
                        "Harvest of %s successfully fetched %d records",
                        context.harvestUrl(), recordCount
                ));
            }
            else {
                FileUtils.deleteQuietly(dataSet.importedOutput());
            }
        }
    }

    private boolean isValidResumptionToken(String resumptionToken) {
        return resumptionToken != null && !resumptionToken.trim().isEmpty();
    }

    private String saveRecords(HttpEntity fetchedRecords, XMLEventWriter out) throws IOException, XMLStreamException {
        InputStream inputStream = fetchedRecords.getContent();
        XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
        Path path = Path.create();
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
                    path = path.child(Tag.element(start.getName().getLocalPart()));
                    if (!recordEvents.isEmpty()) {
                        recordEvents.add(event);
                    }
                    else if (path.equals(RECORD_ROOT)) {
                        if (namespaceCollector != null) {
                            namespaceCollector.gatherFrom(start);
                            out.add(eventFactory.createStartElement("", "", HARVEST_TAG, null, namespaceCollector.iterator()));
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
                        throw new IOException("OAI-PMH Error: " + errorBuilder);
                    }
                    path = path.parent();
                    break;
                case XMLEvent.END_DOCUMENT:
                    finished = true;
                    break;
                case XMLEvent.CHARACTERS:
                case XMLEvent.CDATA:
                    if (!recordEvents.isEmpty()) {
                        String string = event.asCharacters().getData().trim();
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
        String divider = context.harvestUrl().contains("?") ? "&" : "?";
        String url = String.format(
                "%s%sverb=ListRecords&metadataPrefix=%s",
                context.harvestUrl(),
                divider,
                context.harvestPrefix()
        );
        if (context.harvestSpec() != null && !context.harvestSpec().isEmpty()) {
            url += String.format("&set=%s", context.harvestSpec());
        }
        return doGet(url);
    }

    private HttpEntity fetchNextEntity(String resumptionToken) throws IOException {
        String divider = context.harvestUrl().contains("?") ? "&" : "?";
        String url = String.format(
                "%s%sverb=ListRecords&resumptionToken=%s",
                context.harvestUrl(),
                divider,
                URLEncoder.encode(resumptionToken, "UTF-8")
        );
        return doGet(url);
    }

    private HttpEntity doGet(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "text/xml");
        HttpResponse response = httpClient.execute(get);
        Code code = Code.from(response);
        if (code != Code.OK) {
            throw new IOException(code.getMessage());
        }
        return response.getEntity();
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
        OK(SC_OK, "Ok"),
        UNAUTHORIZED(SC_UNAUTHORIZED, "Access unauthorized"),
        FORBIDDEN(SC_FORBIDDEN, "Access forbidden"),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR, "Internal server error"),
        UNKNOWN_RESPONSE(-1, "Unknown");

        private int httpCode;
        private String message;

        Code(int httpCode, String message) {
            this.httpCode = httpCode;
            this.message = message;
        }

        public String getMessage() {
            return message;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Harvestor.class != o.getClass()) return false;
        Harvestor harvestor = (Harvestor) o;
        return dataSet.getSpec().equals(harvestor.dataSet.getSpec());
    }

    @Override
    public int hashCode() {
        return dataSet.getSpec().hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s - %s:%s (%d records)", dataSet.getSpec(), context.harvestUrl(), context.harvestPrefix(), recordCount);
    }
}
