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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static eu.delving.sip.base.SwingHelper.REPORT_ERROR;
import static eu.delving.sip.base.SwingHelper.REPORT_OK;
import static eu.delving.sip.files.ReportWriter.ReportType.VALID;

/**
 * Handles navigation in a report file, optimizing where possible
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFile extends AbstractListModel {
    private static final long BACKTRACK = 10000;
    private static final int MAX_CACHE = 1000;
    private final String prefix;
    private RandomAccessFile randomAccess;
    private List<Rec> recs = new ArrayList<Rec>();
    private Rec lastRec;
    private DataSet dataSet;
    private LinkChecker linkChecker;

    public ReportFile(File file, DataSet dataSet, String prefix) throws IOException {
        this.randomAccess = new RandomAccessFile(file, "r");
        this.dataSet = dataSet;
        this.prefix = prefix;
        long seekEnd = randomAccess.length() - BACKTRACK;
        if (seekEnd < 0) seekEnd = 0;
        randomAccess.seek(seekEnd);
        while (true) {
            Rec rec = new Rec();
            rec.readIn(false);
            if (rec.getRecordNumber() < 0) break;
            lastRec = rec;
        }
    }

    public void setLinkChecker(LinkChecker linkChecker) {
        this.linkChecker = linkChecker;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean needsWork() {
        for (Rec rec : recs) if (rec.needsReading()) return true;
        return false;
    }

    @Override
    public int getSize() {
        return lastRec == null ? 0 : lastRec.getRecordNumber() + 1;
    }

    @Override
    public Object getElementAt(int index) {
        while (index >= recs.size()) {
            recs.add(new Rec());
        }
        Rec rec = recs.get(index);
        rec.activate();
        return rec;
    }

    public void maintainCache() {
        int count = 0;
        for (Rec rec : recs) if (rec.lines != null) count++;
        long tenSecondsAgo = System.currentTimeMillis() - 10 * 60 * 1000;
        if (count > MAX_CACHE) {
            for (Rec rec : recs) {
                if (rec.lines == null) continue;
                if (rec.touch < tenSecondsAgo) {
                    rec.lines = null;
                }
            }
        }
    }

    public Fetch prepareFetch() {
        Fetch fetch = new Fetch();
        int index = 0;
        for (Rec rec : recs) {
            if (rec.needsReading()) {
                fetch.recsToRead.add(rec);
                if (fetch.firstIndex < 0) fetch.firstIndex = index;
                fetch.lastIndex = index;
            }
            index++;
        }
        return fetch;
    }

    public class Fetch {
        private List<Rec> recsToRead = new ArrayList<Rec>();
        private int firstIndex = -1, lastIndex;
    }

    public Work fetchRecords(final Fetch fetch, final Feedback feedback) {
        return new Work.DataSetPrefixWork() {
            @Override
            public Job getJob() {
                return Job.LOAD_REPORT;
            }

            @Override
            public void run() {
                if (fetch.recsToRead.isEmpty()) return;
                List<Rec> safeRecs = new ArrayList<Rec>(recs);
                Rec lastReadRec = null;
                boolean anyUnread = false;
                for (Rec rec : safeRecs) {
                    if (rec.getRecordNumber() >= 0) {
                        lastReadRec = rec;
                    }
                    else {
                        anyUnread = true;
                    }
                }
                try {
                    int from = 0;
                    if (lastReadRec == null) {
                        randomAccess.seek(0);
                    }
                    else if (anyUnread) {
                        lastReadRec.lines = null;
                        lastReadRec.readIn(true); // re-read
                        from = lastReadRec.getRecordNumber();
                    }
                    else {
                        from = fetch.firstIndex;
                    }
                    for (int walk = from; walk <= fetch.lastIndex; walk++) {
                        Rec rec = safeRecs.get(walk);
                        rec.readIn(fetch.recsToRead.contains(rec));
                    }
                }
                catch (IOException e) {
                    feedback.alert("Unable to fetch records", e);
                }
            }

            @Override
            public String getPrefix() {
                return prefix;
            }

            @Override
            public DataSet getDataSet() {
                return dataSet;
            }
        };
    }

    public void close() {
        IOUtils.closeQuietly(randomAccess);
        if (linkChecker != null) linkChecker.close();
    }

    public class Rec {
        private int recordNumber = -1;
        private long seekPos = -1;
        private long touch;
        private ReportWriter.ReportType reportType;
        private List<String> lines;
        private List<LinkChecker.LinkCheck> linkChecks;

        public int getRecordNumber() {
            return recordNumber;
        }

        public void readIn(boolean keep) throws IOException {
            if (seekPos < 0) {
                seekPos = randomAccess.getFilePointer();
            }
            else if (randomAccess.getFilePointer() != seekPos) {
                randomAccess.seek(seekPos);
            }
            final List<String> freshLines = keep ? new ArrayList<String>() : null;
            boolean startFound = false;
            while (true) {
                String line = randomAccess.readLine();
                if (line == null) break;
                Matcher startMatcher = ReportWriter.START.matcher(line);
                if (startMatcher.matches()) {
                    int startRecordNumber = Integer.parseInt(startMatcher.group(1));
                    if (recordNumber < 0) {
                        recordNumber = startRecordNumber;
                    }
                    else if (recordNumber != startRecordNumber) {
                        throw new RuntimeException("What??" + recordNumber + "," + startRecordNumber);
                    }
                    reportType = ReportWriter.ReportType.valueOf(startMatcher.group(2));
                    startFound = true;
                    continue;
                }
                if (startFound && ReportWriter.END.matcher(line).matches()) break;
                if (startFound && freshLines != null) freshLines.add(line);
            }
            if (freshLines != null) Swing.Exec.later(new Swing() {
                @Override
                public void run() {
                    lines = freshLines;
                    fireContentsChanged(ReportFile.this, recordNumber, recordNumber);
                }
            });
        }

        public Work checkLinks(final Feedback feedback) {
            if (reportType != VALID || linkChecks != null) return null;
            final List<String> safeLines = new ArrayList<String>(lines);
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
                    return Job.CHECK_LINK;
                }

                @Override
                public void run() {
                    try {
                        final List<LinkChecker.LinkCheck> checks = new ArrayList<LinkChecker.LinkCheck>();
                        for (String line : safeLines) {
                            Matcher matcher = ReportWriter.LINK.matcher(line);
                            if (!matcher.matches()) continue; // RuntimeException?
                            String url = matcher.group(2);
                            checks.add(linkChecker.get(url));
                        }
                        Swing.Exec.later(new Swing() {
                            @Override
                            public void run() {
                                linkChecks = checks;
                                fireContentsChanged(ReportFile.this, recordNumber, recordNumber);
                            }
                        });
                    }
                    catch (IOException e) {
                        feedback.alert("Unable to check link", e);
                    }
                }
            };
        }

        public String toString() {
            if (lines == null) {
                activate();
                return "loading..." + recordNumber;
            }
            return String.format("%6d %s", recordNumber, reportType);
        }

        public boolean needsReading() {
            return lines == null && touch > 0;
        }

        public void activate() {
            touch = System.currentTimeMillis();
        }
    }

    public static class RecCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (!(value instanceof Rec)) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
            Rec rec = (Rec) value;
            String string;
            if (rec.lines == null) {
                string = "Loading...";
            }
            else {
                StringBuilder out = new StringBuilder(rec.reportType.toString());
                out.append(": ");
                switch (rec.reportType) {
                    case VALID:
                        if (rec.linkChecks == null) {
                            out.append(rec.lines.size()).append(" links unchecked");
                        }
                        else {
                            out.append(rec.lines.size()).append(" links ");
                            int lineNumber = 0;
                            for (String line: rec.lines) {
                                Matcher matcher = ReportWriter.LINK.matcher(line);
                                if (!matcher.matches()) continue; // RuntimeException?
                                out.append(matcher.group(1));
                                LinkChecker.LinkCheck linkCheck = rec.linkChecks.get(lineNumber);
                                if (linkCheck.httpStatus == HttpStatus.SC_OK) {
                                    out.append("\u2714 ");
                                }
                                else {
                                    out.append("\u2716 ");
                                }
                                lineNumber++;
                            }
                        }
                        break;
                    default:
                        out.append(rec.lines.size()).append(" lines");
                        break;
                }
                string = out.toString();
            }
            Component component = super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            if (!isSelected) component.setForeground(rec.reportType == VALID ? REPORT_OK : REPORT_ERROR);
            return component;
        }
    }

    public JList createJList() {
        JList list = new JList(this) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int index = locationToIndex(evt.getPoint());
                if (index < 0) return "?";
                Rec rec = (Rec) getModel().getElementAt(index);
                if (rec.lines == null) return "??";
                switch (rec.reportType) {
                    case VALID:
                        StringBuilder out = new StringBuilder("<html>\n");
                        int lineNumber = 0;
                        for (String line : rec.lines) {
                            out.append(StringEscapeUtils.escapeHtml(line));
                            if (rec.linkChecks != null && lineNumber < rec.linkChecks.size()) {
                                out.append(": checked ").append(rec.linkChecks.get(lineNumber));
                            }
                            else {
                                out.append(": unchecked");
                            }
                            out.append("<br>\n");
                            lineNumber++;
                        }
                        return out.toString();
                    default:
                        return "<html>\n" + StringUtils.join(rec.lines, "<br>\n");
                }
            }
        };
        list.setPrototypeCellValue("One single line of something or other");
        list.setCellRenderer(new RecCellRenderer());
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return list;
    }
}
