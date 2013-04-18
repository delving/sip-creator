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
import org.apache.log4j.Logger;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The file of checked links, get stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkFile {
    public static final String CSV_HEADER = "\"URL\",\"Date\",\"HTTP Status\",\"File Size\",\"MIME Type\"";
    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private Logger log = Logger.getLogger(getClass());
    private File file;
    private DataSet dataSet;
    private String prefix;

    public LinkFile(File file, DataSet dataSet, String prefix) {
        this.file = file;
        this.dataSet = dataSet;
        this.prefix = prefix;
    }

    public Work load(final Map<String, LinkCheck> map, final Feedback feedback, final Swing finished) {
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
                    log.info("Loaded "+map.size()+" links");
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

    public Work save(final Map<String, LinkCheck> map, final Feedback feedback, final Swing finished) {
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
                    int count = 0;
                    for (Map.Entry<String, LinkCheck> mapEntry : map.entrySet()) {
                        Entry entry = new Entry(mapEntry.getKey(), mapEntry.getValue());
                        out.write(entry.toLine());
                        out.write('\n');
                        count++;
                    }
                    out.close();
                    log.info("Saved "+count+" links");
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

}
