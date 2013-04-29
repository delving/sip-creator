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

import eu.delving.metadata.RecDef;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.Feedback;
import org.apache.log4j.Logger;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.delving.sip.files.LinkFile.FileSizeCategory.NO_INFO;
import static eu.delving.sip.files.LinkFile.FileSizeCategory.values;

/**
 * The file of checked links, get stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkFile {
    public static final String CSV_HEADER = "\"URL\", \"OK\", \"Check\", \"Spec\", \"Org ID\", \"Local ID\", \"Date\", \"HTTP Status\", \"File Size\", \"MIME Type\"";
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

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return prefix;
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
                    log.info("Loaded " + map.size() + " links");
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
                    log.info("Saved " + count + " links");
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

    public interface LinkStatsCallback {
        void linkStatistics(Map<RecDef.Check,LinkStats> linkStatsMap);
    }

    public Work gatherStats(final Feedback feedback, final LinkStatsCallback callback, final Swing finished) {
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
                return Job.GATHER_LINK_STATS;
            }

            @Override
            public void run() {
                try {
                    Map<RecDef.Check,LinkStats> linkStatsMap = new TreeMap<RecDef.Check, LinkStats>();
                    BufferedReader in = new BufferedReader(new FileReader(file));
                    String line = in.readLine();
                    if (!CSV_HEADER.equals(line)) throw new IOException("Expected CSV Header");
                    int count = 0;
                    while ((line = in.readLine()) != null) {
                        Entry entry = new Entry(line);
                        RecDef.Check check = entry.linkCheck.check;
                        LinkStats linkStats = linkStatsMap.get(check);
                        if (linkStats == null) {
                            linkStatsMap.put(check, linkStats = new LinkStats());
                        }
                        linkStats.consume(entry);
                        count++;
                    }
                    in.close();
                    log.info("Analyzed " + count + " links");
                    callback.linkStatistics(linkStatsMap);
                }
                catch (IOException e) {
                    feedback.alert("Unable to analyze link stats", e);
                }
                finally {
                    Swing.Exec.later(finished);
                }
            }
        };
    }

    public static class Entry {
        public static final Pattern LINK_CHECK_PATTERN = Pattern.compile("\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", (-?\\d+), (-?\\d+), \"([^\"]*)\"");
        public String url;
        public LinkCheck linkCheck;

        public Entry(String url, LinkCheck linkCheck) {
            this.url = url;
            this.linkCheck = linkCheck;
        }

        public Entry(String line) {
            Matcher matcher = LINK_CHECK_PATTERN.matcher(line);
            if (!matcher.matches()) throw new RuntimeException("Unreadable line: " + line);
            url = matcher.group(1);
            linkCheck = new LinkCheck();
            linkCheck.ok = Boolean.parseBoolean(matcher.group(2));
            linkCheck.check = RecDef.Check.valueOf(matcher.group(3));
            linkCheck.spec = matcher.group(4);
            linkCheck.orgId = matcher.group(5);
            linkCheck.localId = matcher.group(6);
            try {
                linkCheck.time = DATE_FORMAT.parse(matcher.group(7)).getTime();
            }
            catch (ParseException e) {
                throw new RuntimeException("Cannot parse date");
            }
            linkCheck.httpStatus = Integer.parseInt(matcher.group(8));
            linkCheck.fileSize = Integer.parseInt(matcher.group(9));
            linkCheck.mimeType = matcher.group(10);
        }

        public String toLine() {
            return String.format(
                    "\"%s\", \"%s\", \"%s\", \"%s\", \"%s\", \"%s\", \"%s\", %d, %d, \"%s\"",
                    url, linkCheck.ok, linkCheck.check, linkCheck.spec, linkCheck.orgId, linkCheck.localId,
                    DATE_FORMAT.format(new Date(linkCheck.time)), linkCheck.httpStatus, linkCheck.fileSize, linkCheck.mimeType
            );
        }
    }

    public enum FileSizeCategory {
        HUGE(1000000),
        MAX_10M(10000),
        MAX_1M(1000),
        MAX_500K(500),
        MAX_250K(250),
        MAX_100K(100),
        MAX_50K(50),
        NO_INFO(-1000000);

        public final int maxKb;

        private FileSizeCategory(int maxKb) {
            this.maxKb = maxKb;
        }
    }

    public static class LinkStats {
        public final Map<String, Counter> mimeTypes = new TreeMap<String, Counter>();
        public final Map<String, Counter> httpStatus = new TreeMap<String, Counter>();
        public final Map<FileSizeCategory, Counter> fileSize = new TreeMap<FileSizeCategory, Counter>();

        public void consume(Entry entry) {
            captureMimeType(entry.linkCheck);
            captureHTTPStatus(entry.linkCheck);
            captureFileSizes(entry.linkCheck);
        }

        private void captureMimeType(LinkCheck linkCheck) {
            Counter counter = mimeTypes.get(linkCheck.mimeType);
            if (counter == null) mimeTypes.put(linkCheck.mimeType, counter = new Counter());
            counter.count++;
        }

        private void captureHTTPStatus(LinkCheck linkCheck) {
            String status = String.valueOf(linkCheck.httpStatus);
            Counter counter = httpStatus.get(status);
            if (counter == null) httpStatus.put(status, counter = new Counter());
            counter.count++;
        }

        private void captureFileSizes(LinkCheck linkCheck) {
            if (!linkCheck.check.captureSize) return;
            int size = linkCheck.fileSize;
            FileSizeCategory foundCategory = NO_INFO;
            for (FileSizeCategory category : values()) {
                if (size > category.maxKb * 1024) break;
                foundCategory = category;
            }
            Counter counter = fileSize.get(foundCategory);
            if (counter == null) fileSize.put(foundCategory, counter = new Counter());
            counter.count++;
        }
    }

    public static class Counter {
        public int count;

        @Override
        public String toString() {
            return String.valueOf(count);
        }
    }
}
