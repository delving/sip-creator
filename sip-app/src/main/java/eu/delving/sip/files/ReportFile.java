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
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.xml.ResultLinkChecks;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;

import javax.swing.*;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.REPORT_ERROR;
import static eu.delving.sip.base.SwingHelper.REPORT_OK;
import static eu.delving.sip.files.ReportWriter.ReportType.VALID;

/**
 * Handles navigation in a report file, optimizing where possible
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFile {
    private static final long LONG_SIZE = 8;
    private static final int MAX_CACHE = 1000;
    private final DataSet dataSet;
    private final String prefix;
    private final File reportFile;
    private final LinkFile linkFile;
    private RandomAccessFile reportAccess;
    private RandomAccessFile reportIndexAccess;
    private List<Rec> recs;
    private List<Rec> invalidRecs;
    private LinkChecker linkChecker;
    private ResultLinkChecks resultLinkChecks;
    private boolean allVisible;
    private All all = new All();
    private OnlyInvalid onlyInvalid = new OnlyInvalid();

    public ReportFile(File reportFile, File reportIndexFile, File invalidFile, File linkFile, DataSet dataSet, String prefix) throws IOException {
        this.reportFile = reportFile;
        this.reportAccess = new RandomAccessFile(this.reportFile, "r");
        this.reportIndexAccess = new RandomAccessFile(reportIndexFile, "r");
        this.linkFile = new LinkFile(linkFile, dataSet, prefix);
        this.dataSet = dataSet;
        this.prefix = prefix;
        int recordCount = (int) (reportIndexAccess.length() / LONG_SIZE);
        recs = new ArrayList<Rec>(recordCount);
        for (int walk = 0; walk < recordCount; walk++) recs.add(new Rec(walk));
        DataInputStream invalidIn = new DataInputStream(new FileInputStream(invalidFile));
        int invalidCount = invalidIn.readInt();
        invalidRecs = new ArrayList<Rec>(invalidCount);
        for (int walk = 0; walk < invalidCount; walk++) {
            int recordNumber = invalidIn.readInt();
            invalidRecs.add(recs.get(recordNumber));
        }
        invalidIn.close();
    }

    public ListModel<Rec> getAll() {
        allVisible = true;
        return all;
    }

    public OnlyInvalid getOnlyInvalid() {
        allVisible = false;
        return onlyInvalid;
    }

    public LinkFile getLinkFile() {
        return linkFile;
    }

    public void setLinkChecker(LinkChecker linkChecker) {
        this.linkChecker = linkChecker;
        this.resultLinkChecks = new ResultLinkChecks(dataSet, prefix, linkChecker);
    }

    public LinkChecker getLinkChecker() {
        return linkChecker;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return prefix;
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

    public List<Rec> prepareFetch() {
        List<Rec> recsToRead = new ArrayList<Rec>();
        for (Rec rec : recs) {
            if (rec.needsReading()) recsToRead.add(rec);
        }
        return recsToRead;
    }

    public Work fetchRecords(final List<Rec> recsToRead, final Feedback feedback) {
        return new Work.DataSetPrefixWork() {
            @Override
            public Job getJob() {
                return Job.LOAD_REPORT;
            }

            @Override
            public void run() {
                try {
                    for (Rec rec : recsToRead) {
                        if (rec.seekPos < 0) {
                            int recordNumber = rec.getRecordNumber();
                            reportIndexAccess.seek(recordNumber * LONG_SIZE);
                            rec.seekPos = reportIndexAccess.readLong();
                        }
                        rec.readIn();
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
        IOUtils.closeQuietly(reportIndexAccess);
        IOUtils.closeQuietly(reportAccess);
    }

    public class Rec {
        private int recordNumber;
        private long seekPos = -1;
        private long touch;
        private ReportWriter.ReportType reportType = ReportWriter.ReportType.UNEXPECTED;
        private String localId;
        private String error;
        private List<String> lines;

        public Rec(int recordNumber) {
            this.recordNumber = recordNumber;
        }

        public int getRecordNumber() {
            return recordNumber;
        }

        public void readIn() throws IOException {
            reportAccess.seek(seekPos);
            final List<String> freshLines = new ArrayList<String>();
            boolean within = false;
            while (true) {
                String line = reportAccess.readLine();
                if (line == null) break;
                ReportStrings.Match startMatcher = ReportStrings.START.matcher(line);
                if (startMatcher.matches()) {
                    int startRecordNumber = Integer.parseInt(startMatcher.group(1));
                    if (recordNumber < 0) {
                        recordNumber = startRecordNumber;
                    }
                    else if (startRecordNumber != recordNumber) {
                        throw new RuntimeException("Record number discrepancy: " + startRecordNumber + "vs" + recordNumber);
                    }
                    reportType = ReportWriter.ReportType.valueOf(startMatcher.group(2));
                    switch (reportType) {
                        case VALID:
                            localId = startMatcher.group(3);
                            break;
                        case INVALID:
                        case DISCARDED:
                        case UNEXPECTED:
                            error = startMatcher.group(3);
                            break;
                    }
                    within = true;
                    continue;
                }
                if (within && ReportStrings.END.matcher(line).matches()) break;
                if (within) freshLines.add(line);
            }
            Swing.Exec.later(new Swing() {
                @Override
                public void run() {
                    lines = freshLines;
                    if (allVisible) {
                        all.fireContentsChanged(recordNumber);
                    }
                    else {
                        onlyInvalid.fireContentsChanged(recordNumber);
                    }
                }
            });
        }

        public Work.DataSetPrefixWork checkLinks(final Feedback feedback, final Swing after) {
            if (reportType != VALID) return null;
            if (lines == null) return null;
            return resultLinkChecks.checkLinks(localId, new ArrayList<String>(lines), feedback, new Swing() {
                @Override
                public void run() {
                    all.fireContentsChanged(recordNumber);
                    after.run();
                }
            });
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

        public Rec activate() {
            if (lines == null) {
                touch = System.currentTimeMillis();
            }
            return this;
        }
    }

    public ListCellRenderer getCellRenderer() {
        return new RecCellRenderer();
    }

    private class RecCellRenderer extends DefaultListCellRenderer {
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
                StringBuilder out = new StringBuilder();
                out.append("<");
                out.append(rec.getRecordNumber());
                out.append(":");
                out.append(rec.reportType);
                out.append("> ");
                switch (rec.reportType) {
                    case VALID:
                        out.append(rec.lines.size()).append(" links ");
                        for (String line : rec.lines) {
                            ReportStrings.Match matcher = ReportStrings.LINK.matcher(line);
                            if (!matcher.matches()) continue; // RuntimeException?
                            RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
                            out.append(check);
                            if (check.fetch) {
                                String url = matcher.group(2);
                                if (linkChecker == null) {
                                    out.append(" ");
                                }
                                else {
                                    LinkCheck linkCheck = linkChecker.lookup(url);
                                    if (linkCheck == null) {
                                        out.append("? ");
                                    }
                                    else {
                                        out.append(":").append(linkCheck.getTime());
                                        out.append(linkCheck.ok ? "\u2714 " : "\u2716 ");
                                    }
                                }
                            }
                            else {
                                out.append(" \u2714 "); // it's present
                            }
                        }
                        break;
                    default:
                        out.append(rec.error);
                        break;
                }
                string = out.toString();
            }
            Component component = super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            if (!isSelected) component.setForeground(rec.reportType == VALID ? REPORT_OK : REPORT_ERROR);
            return component;
        }
    }

    public String toHtml(Rec rec) {
        if (rec.lines == null) return "??";
        StringBuilder out = new StringBuilder("<html><table cellpadding=6>\n");
        switch (rec.reportType) {
            case VALID:
                ResultLinkChecks.validLinesToHTML(rec.lines, linkChecker, out);
                break;
            default:
                out.append("<tr><td>\n");
                out.append("<b>").append(rec.error).append("</b><br><br>\n");
                for (String line : rec.lines) {
                    out.append(StringEscapeUtils.escapeHtml(line)).append("<br>\n");
                }
                out.append("</td></tr>\n");
                break;
        }
        out.append("</table>");
        return out.toString();
    }

    public interface PresenceStatsCallback {
        void presenceCounts(int[] presence, int totalRecords);
    }

    public Work gatherStats(final PresenceStatsCallback callback, final Feedback feedback, final Swing finished) {
        return new StatisicsGatherer(callback, feedback, finished);
    }

    private class StatisicsGatherer implements Work.DataSetPrefixWork, Work.LongTermWork {
        private final PresenceStatsCallback callback;
        private final Feedback feedback;
        private final Swing finished;
        private ProgressListener progressListener;

        private StatisicsGatherer(PresenceStatsCallback callback, Feedback feedback, Swing finished) {
            this.callback = callback;
            this.feedback = feedback;
            this.finished = finished;
        }

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
            return Job.GATHER_PRESENCE_STATS;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            this.progressListener.prepareFor(recs.size());
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(reportFile), "UTF-8"));
                boolean within = false;
                int[] presence = new int[RecDef.Check.values().length];
                boolean[] contains = new boolean[RecDef.Check.values().length];
                while (true) {
                    String line = in.readLine();
                    if (line == null) break;
                    ReportStrings.Match startMatcher = ReportStrings.START.matcher(line);
                    if (startMatcher.matches()) {
                        int recordNumber = Integer.parseInt(startMatcher.group(1));
                        progressListener.setProgress(recordNumber);
                        ReportWriter.ReportType reportType = ReportWriter.ReportType.valueOf(startMatcher.group(2));
                        if (reportType != VALID) continue;
                        within = true;
                        Arrays.fill(contains, false);
                        continue;
                    }
                    if (within) {
                        if (ReportStrings.END.matcher(line).matches()) {
                            for (int walk = 0; walk < contains.length; walk++) {
                                if (contains[walk]) presence[walk]++;
                            }
                            continue;
                        }
                        ReportStrings.Match matcher = ReportStrings.LINK.matcher(line);
                        if (!matcher.matches()) continue; // RuntimeException?
                        RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
                        contains[check.ordinal()] = true;
                    }
                }
                callback.presenceCounts(presence, recs.size());
            }
            catch (IOException e) {
                feedback.alert("Unable to gather output statistics", e);
            }
            catch (CancelException e) {
                feedback.alert("Cancelled output statistics gathering");
            }
            finally {
                Swing.Exec.later(finished);
            }
        }
    }

    public class All extends AbstractListModel<Rec> {
        @Override
        public int getSize() {
            return recs.size();
        }

        @Override
        public Rec getElementAt(int index) {
            return recs.get(index).activate();
        }

        public void fireContentsChanged(int recordNumber) {
            fireContentsChanged(this, recordNumber, recordNumber);
        }
    }

    public class OnlyInvalid extends AbstractListModel<Rec> {
        @Override
        public int getSize() {
            return invalidRecs.size();
        }

        @Override
        public Rec getElementAt(int index) {
            return invalidRecs.get(index).activate();
        }

        public void fireContentsChanged(int recordNumber) {
            int index = 0;
            for (Rec invalidRec : invalidRecs) {
                if (invalidRec.recordNumber == recordNumber) {
                    fireContentsChanged(OnlyInvalid.this, index, index);
                    break;
                }
                index++;
            }
        }

    }
}
