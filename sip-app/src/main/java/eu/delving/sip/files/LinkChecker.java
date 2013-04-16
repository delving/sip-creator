/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.files;

import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.Feedback;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check links and use MapDB to maintain link checking results
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkChecker {
    private static final String CSV_HEADER = "\"URL\",\"Date\",\"HTTP Status\",\"File Size\",\"MIME Type\"";
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static Logger log = Logger.getLogger(LinkChecker.class);
    private static EnglishReasonPhraseCatalog REASON = EnglishReasonPhraseCatalog.INSTANCE;
    private final DataSet dataSet;
    private final String prefix;
    private final HttpClient httpClient;
    private HTreeMap<String, LinkCheck> map;
    private File file;

    public LinkChecker(HttpClient httpClient, File file, DataSet dataSet, String prefix) {
        this.httpClient = httpClient;
        this.file = file;
        this.dataSet = dataSet;
        this.prefix = prefix;
        this.map = DBMaker.newTempHashMap();
    }

    public boolean contains(String url) {
        return map.containsKey(url);
    }

    public LinkCheck lookup(String url) {
        return map.get(url);
    }

    public LinkCheck request(String url) throws IOException {
        LinkCheck linkCheck = linkCheckRequest(url);
        log.info(String.format("Found %s by requesting: %s", url, linkCheck));
        map.put(url, linkCheck);
        return linkCheck;
    }

    public Work load(final Feedback feedback, final Swing finished) {
        if (!file.exists()) return null;
        return new Work.DataSetPrefixWork() {
            @Override
            public String getPrefix() {
                return prefix;
            }

            @Override
            public DataSet getDataSet() {
                return dataSet;
            }

            @Override
            public Job getJob() {
                return Job.LOAD_LINKS;
            }

            @Override
            public void run() {
                try {
                    BufferedReader in = new BufferedReader(new FileReader(file));
                    String line = in.readLine();
                    if (!CSV_HEADER.equals(line)) throw new IOException("Expected CSV Header");
                    while ((line = in.readLine()) != null) {
                        Entry entry = new Entry(line);
                        map.put(entry.url, entry.linkCheck);
                    }
                    in.close();
                    System.out.println("Loaded "+map.size()+" links");
                }
                catch (IOException e) {
                    feedback.alert("Unable to load links", e);
                }
                finally {
                    Swing.Exec.later(finished);
                }
            }
        };
    }

    public Work save(final Feedback feedback, final Swing finished) {
        return new Work.DataSetPrefixWork() {
            @Override
            public String getPrefix() {
                return prefix;
            }

            @Override
            public DataSet getDataSet() {
                return dataSet;
            }

            @Override
            public Job getJob() {
                return Job.SAVE_LINKS;
            }

            @Override
            public void run() {
                try {
                    FileWriter out = new FileWriter(file);
                    out.write(CSV_HEADER);
                    out.write('\n');
                    for (Map.Entry<String, LinkCheck> mapEntry : map.entrySet()) {
                        Entry entry = new Entry(mapEntry.getKey(), mapEntry.getValue());
                        out.write(entry.toLine());
                        out.write('\n');
                    }
                    out.close();
                }
                catch (IOException e) {
                    feedback.alert("Unable to save links", e);
                }
                finally {
                    Swing.Exec.later(finished);
                }
            }
        };
    }

    private LinkCheck linkCheckRequest(String url) throws IOException {
        HttpHead head = new HttpHead(url);
        HttpResponse response = httpClient.execute(head);
        StatusLine status = response.getStatusLine();
        LinkCheck linkCheck = new LinkCheck();
        linkCheck.httpStatus = status.getStatusCode();
        linkCheck.time = System.currentTimeMillis();
        Header contentType = response.getLastHeader("Content-Type");
        linkCheck.mimeType = contentType == null ? null : contentType.getValue();
        Header contentLength = response.getLastHeader("Content-Length");
        linkCheck.fileSize = contentLength == null ? -1 : Integer.parseInt(contentLength.getValue());
        EntityUtils.consume(response.getEntity());
        return linkCheck;
    }

    public static class Entry {
        public static final Pattern LINK_CHECK_PATTERN = Pattern.compile("\"([^\"]*)\", \"([^\"]*)\", (-?\\d+), (-?\\d+), \"([^\"]*)\"");
        public String url;
        public LinkCheck linkCheck;

        public Entry(String url, LinkCheck linkCheck) {
            this.url = url;
            this.linkCheck = linkCheck;
        }

        public Entry(String line) {
            Matcher matcher = LINK_CHECK_PATTERN.matcher(line);
            if (!matcher.matches()) throw new RuntimeException("Unreadable line: " + line);
            this.url = matcher.group(1);
            this.linkCheck = new LinkCheck();
            try {
                linkCheck.time = DATE_FORMAT.parse(matcher.group(2)).getTime();
            }
            catch (ParseException e) {
                throw new RuntimeException("Cannot parse date");
            }
            linkCheck.httpStatus = Integer.parseInt(matcher.group(3));
            linkCheck.fileSize = Integer.parseInt(matcher.group(4));
            linkCheck.mimeType = matcher.group(5);
        }

        public String toLine() {
            return String.format(
                    "\"%s\", \"%s\", %d, %d, \"%s\"",
                    url, DATE_FORMAT.format(new Date(linkCheck.time)), linkCheck.httpStatus, linkCheck.fileSize, linkCheck.mimeType
            );
        }
    }

    public static class LinkCheck implements Serializable {
        public int httpStatus;
        public long time;
        public int fileSize;
        public String mimeType;

        public String getStatusReason() {
            return String.format(
                    "%d: %s",
                    httpStatus, REASON.getReason(httpStatus, null)
            );
        }
    }
}
