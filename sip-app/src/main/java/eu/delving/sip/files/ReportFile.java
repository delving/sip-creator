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
import org.apache.http.HttpStatus;

import javax.swing.*;
import java.awt.Component;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
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

public class ReportFile {
    private static final long BACKTRACK = 10000;
    private static final int MAX_CACHE = 1000;
    private final DataSet dataSet;
    private final String prefix;
    private final File reportFile;
    private RandomAccessFile randomAccess;
    private List<Rec> recs;
    private Rec lastRec;
    private LinkFile linkFile;
    private LinkChecker linkChecker;
    private ResultLinkChecks resultLinkChecks;
    private boolean allVisible;
    private All all = new All();
    private OnlyInvalid onlyInvalid = new OnlyInvalid();

    public ReportFile(File reportFile, File linkFile, DataSet dataSet, String prefix) throws IOException {
        this.reportFile = reportFile;
        this.randomAccess = new RandomAccessFile(this.reportFile, "r");
        this.linkFile = new LinkFile(linkFile, dataSet, prefix);
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
        int recordCount = lastRec.getRecordNumber() + 1;
        recs = new ArrayList<Rec>(recordCount);
        while (recs.size() < recordCount) recs.add(new Rec());
    }

    public ListModel getAll() {
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

    public boolean needsWork() {
        for (Rec rec : recs) if (rec.needsReading()) return true;
        return false;
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
            if (rec.needsReading()) fetch.add(index, rec);
            index++;
        }
        return fetch;
    }

    public class Fetch {
        private List<Rec> recsToRead = new ArrayList<Rec>();
        private int firstIndex = -1, lastIndex;

        public void add(int index, Rec rec) {
            recsToRead.add(rec);
            if (firstIndex < 0) firstIndex = index;
            lastIndex = index;
        }
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
    }

    public class Rec {
        private int recordNumber = -1;
        private long seekPos = -1;
        private long touch;
        private ReportWriter.ReportType reportType;
        private String localId;
        private String error;
        private List<String> lines;

        public int getRecordNumber() {
            return recordNumber;
        }

        public boolean isInvalid() {
            return ReportWriter.ReportType.VALID != reportType;
        }

        public void readIn(boolean keep) throws IOException {
            if (seekPos < 0) {
                seekPos = randomAccess.getFilePointer();
            }
            else if (randomAccess.getFilePointer() != seekPos) {
                randomAccess.seek(seekPos);
            }
            final List<String> freshLines = keep ? new ArrayList<String>() : null;
            boolean within = false;
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
                if (within && ReportWriter.END.matcher(line).matches()) break;
                if (within && freshLines != null) freshLines.add(line);
            }
            if (freshLines != null) Swing.Exec.later(new Swing() {
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
            touch = System.currentTimeMillis();
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
                            Matcher matcher = ReportWriter.LINK.matcher(line);
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
                                        linkCheck.ok = linkCheck.httpStatus == HttpStatus.SC_OK;
                                        switch (check) {
                                            case DEEP_ZOOM:
                                                if (linkCheck.ok) {
                                                    linkCheck.ok = "application/xml".equals(linkCheck.mimeType);
                                                }
                                                break;
                                        }
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
            this.progressListener.prepareFor(lastRec.getRecordNumber());
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
                    Matcher startMatcher = ReportWriter.START.matcher(line);
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
                        if (ReportWriter.END.matcher(line).matches()) {
                            for (int walk = 0; walk < contains.length; walk++) {
                                if (contains[walk]) presence[walk]++;
                            }
                            continue;
                        }
                        Matcher matcher = ReportWriter.LINK.matcher(line);
                        if (!matcher.matches()) continue; // RuntimeException?
                        RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
                        contains[check.ordinal()] = true;
                    }
                }
                callback.presenceCounts(presence, lastRec.getRecordNumber() + 1);
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

    public class All extends AbstractListModel {
        @Override
        public int getSize() {
            return lastRec == null ? 0 : lastRec.getRecordNumber() + 1;
        }

        @Override
        public Object getElementAt(int index) {
            return recs.get(index).activate();
        }

        public void fireContentsChanged(int recordNumber) {
            fireContentsChanged(this, recordNumber, recordNumber);
        }
    }

    public class OnlyInvalid extends AbstractListModel {
        private List<Rec> invalidRecs = new ArrayList<Rec>();

        public Work refresh(final Feedback feedback, final Swing after) {
            return new Work.DataSetPrefixWork() {
                @Override
                public void run() {
                    try {
                    boolean missing = false;
                    for (Rec rec : recs) if (rec.getRecordNumber() < 0) missing = true;
                    if (missing) {
                        try {
                            randomAccess.seek(0);
                            for (Rec rec : recs) rec.readIn(false);
                        }
                        catch (IOException e) {
                            feedback.alert("Problem scanning report file", e);
                        }
                    }
                    Swing.Exec.later(new Swing() {
                        @Override
                        public void run() {
                            final int wasSize = invalidRecs.size();
                            invalidRecs.clear();
                            if (wasSize > 0) fireIntervalRemoved(OnlyInvalid.this, 0, wasSize - 1);
                            for (Rec rec : recs) if (rec.isInvalid()) invalidRecs.add(rec.activate());
                            if (!invalidRecs.isEmpty()) fireIntervalAdded(OnlyInvalid.this, 0, getSize() - 1);
                        }
                    });
                    }
                    finally {
                        after.run();;
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

                @Override
                public Job getJob() {
                    return Job.LOAD_REPORT;
                }
            };
        }

        @Override
        public int getSize() {
            return invalidRecs.size();
        }

        @Override
        public Object getElementAt(int index) {
            return invalidRecs.get(index);
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
