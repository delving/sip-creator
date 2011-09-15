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
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.ValueFilter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
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

public class Harvestor {
    private static final Path RECORD_ROOT = new Path("");
    private static final Path RESUMPTION_TOKEN = new Path("");
    private Logger log = Logger.getLogger(getClass());
    private XMLInputFactory inputFactory = WstxInputFactory.newInstance();
    private XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
    private XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    private HttpClient httpClient = new DefaultHttpClient();
    private Context context;

    public interface Context {

        void tellUser(String message);

    }

    public enum Code {
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

    public Harvestor(Context context) {
        this.context = context;
    }

    public void downloadDataSet(DataSet dataSet, ProgressListener progressListener) {
        Exec.work(new Fetcher(dataSet, progressListener));
    }

    private class Fetcher implements Runnable {
        private DataSet dataSet;
        private ProgressListener progressListener;
        private String harvestUrl;
        private String resumptionToken;
        private OutputStream outputStream;
        private XMLEventWriter out;
        private NamespaceCollector namespaceCollector = new NamespaceCollector();

        private Fetcher(DataSet dataSet, ProgressListener progressListener) {
            this.dataSet = dataSet;
            this.progressListener = progressListener;
            this.harvestUrl = getHarvestUrl();
        }

        @Override
        public void run() {
            if (harvestUrl == null) return;
            if (!prepareOutput()) return;
            try {
//                if (progressListener != null) progressListener.prepareFor(total);
                HttpEntity fetchedRecords = fetchFirstEntity();
                resumptionToken = saveRecords(fetchedRecords, out);
                fetchedRecords.consumeContent();
                while (resumptionToken != null && !resumptionToken.trim().isEmpty()) {
                    fetchedRecords = fetchNextEntity();
                    resumptionToken = saveRecords(fetchedRecords, out);
                    fetchedRecords.consumeContent();
                }
                finishOutput();
                if (progressListener != null) progressListener.finished(true);
            }
            catch (IOException e) {
                e.printStackTrace();  // todo: something
                if (progressListener != null) progressListener.finished(false);
            }
            catch (XMLStreamException e) {
                e.printStackTrace();  // todo: something
                if (progressListener != null) progressListener.finished(false);
            }
        }

        private String saveRecords(HttpEntity fetchedRecords, XMLEventWriter out) throws IOException, XMLStreamException {
            InputStream inputStream = fetchedRecords.getContent();
            XMLEventReader in = inputFactory.createXMLEventReader(new StreamSource(inputStream, "UTF-8"));
            Path path = new Path();
            StringBuilder tokenBuilder = null;
            String tokenValue = null;
            List<XMLEvent> recordEvents = new ArrayList<XMLEvent>();
            int count = 0;
            boolean finished = false;
            try {
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
                                if (progressListener != null) progressListener.setProgress(count);
                            }
                            else if (path.equals(RESUMPTION_TOKEN)) {
                                tokenBuilder = new StringBuilder();
                                tokenValue = null;
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
                                    count++;
                                }
                                else {
                                    recordEvents.add(event);
                                    recordEvents.add(eventFactory.createCharacters("\n"));
                                }
                            }
                            else if (path.equals(RESUMPTION_TOKEN)) {
                                tokenValue = tokenBuilder.toString();
                                tokenBuilder = null;
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
                                    recordEvents.add(eventFactory.createCharacters(string));
                                }
                            }
                            else if (tokenBuilder != null) {
                                tokenBuilder.append(event.asCharacters().getData());
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
            }
            return tokenValue;
        }

        private HttpEntity fetchFirstEntity() throws IOException {
            String metadataPrefix = "raw"; // todo
            String url = String.format(
                    "%s/oai-pmh?verb=ListRecords&metadataPrefix=%s&set=%s",
                    harvestUrl,
                    metadataPrefix,
                    dataSet.getSpec()
            );
            return doGet(url);
        }

        private HttpEntity fetchNextEntity() throws IOException {
            String url = String.format(
                    "%s/oai-pmh?verb=ListRecords&resumptionToken=%s",
                    harvestUrl,
                    resumptionToken
            );
            return doGet(url);
        }

        private HttpEntity doGet(String url) throws IOException {
            HttpGet get = new HttpGet(url);
            get.setHeader("Accept", "text/xml");
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
                outputStream = dataSet.importedOutput();
                out = outputFactory.createXMLEventWriter(new OutputStreamWriter(outputStream, "UTF-8"));
                out.add(eventFactory.createStartDocument());
                out.add(eventFactory.createCharacters("\n"));
                return true;
            }
            catch (StorageException e) {
                context.tellUser("Unable to create file to receive harvested data");
                return false;
            }
            catch (XMLStreamException e) {
                context.tellUser("Unable to stream to file to receive harvested data");
                return false;
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        private void finishOutput() throws XMLStreamException, IOException {
            out.add(eventFactory.createEndElement("", "", ENVELOPE_TAG));
            out.add(eventFactory.createCharacters("\n"));
            out.add(eventFactory.createEndDocument());
            out.flush();
            outputStream.close();
        }

        private String getHarvestUrl() {
            String harvestUrl = dataSet.getHints().get(Storage.HARVEST_URL);
            if (harvestUrl == null || harvestUrl.trim().isEmpty()) {
                context.tellUser("Cannot harvest unless a URL is given");
                return null;
            }
            try {
                new URL(harvestUrl);
            }
            catch (MalformedURLException e) {
                context.tellUser("Cannot harvest since the URL is malformed: " + harvestUrl);
                return null;
            }
            return harvestUrl;
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
