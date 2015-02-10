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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.REPORT_ERROR;

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
    private final List<String> reportConclusion;
    private RandomAccessFile reportAccess;
    private RandomAccessFile reportIndexAccess;
    private List<Rec> invalidRecs;
    private InvalidRecListModel invalidRecListModel = new InvalidRecListModel();

    public ReportFile(File reportFile, File reportIndexFile, File reportConclusionFile, DataSet dataSet, String prefix) throws IOException {
        this.reportAccess = new RandomAccessFile(reportFile, "r");
        this.reportIndexAccess = new RandomAccessFile(reportIndexFile, "r");
        if (reportConclusionFile.exists()) {
            this.reportConclusion = FileUtils.readLines(reportConclusionFile);
        }
        else {
            List<String> apology = new ArrayList<String>();
            apology.add("No conclusions. They should appear after next validation");
            this.reportConclusion = apology;
        }
        this.dataSet = dataSet;
        this.prefix = prefix;
        int recordCount = (int) (reportIndexAccess.length() / LONG_SIZE);
        invalidRecs = new ArrayList<Rec>(recordCount);
        for (int walk = 0; walk < Math.min(recordCount, MAX_CACHE); walk++) invalidRecs.add(new Rec(walk));
    }

    public List<String> getReportConclusion() {
        return reportConclusion;
    }

    public InvalidRecListModel getInvalidRecListModel() {
        return invalidRecListModel;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return prefix;
    }

    public void maintainCache() {
        int count = 0;
        for (Rec rec : invalidRecs) if (rec.lines != null) count++;
        long tenSecondsAgo = System.currentTimeMillis() - 10 * 60 * 1000;
        if (count > MAX_CACHE) {
            for (Rec rec : invalidRecs) {
                if (rec.lines == null) continue;
                if (rec.touch < tenSecondsAgo) {
                    rec.lines = null;
                }
            }
        }
    }

    public List<Rec> prepareFetch() {
        List<Rec> recsToRead = new ArrayList<Rec>();
        for (Rec rec : invalidRecs) {
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
                    invalidRecListModel.fireContentsChanged(recordNumber);
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
                string = "<" + rec.getRecordNumber() + ":" + rec.reportType + "> " + rec.error;
            }
            Component component = super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            if (!isSelected) component.setForeground(REPORT_ERROR);
            return component;
        }
    }

    public String toHtml(Rec rec) {
        if (rec.lines == null) return "??";
        StringBuilder out = new StringBuilder("<html><table cellpadding=6>\n");
        out.append("<tr><td>\n");
        out.append("<b>").append(rec.error).append("</b><br><br>\n");
        for (String line : rec.lines) {
            out.append(StringEscapeUtils.escapeHtml(line)).append("<br>\n");
        }
        out.append("</td></tr>\n");
        out.append("</table>");
        return out.toString();
    }

    public class InvalidRecListModel extends AbstractListModel<Rec> {
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
                    fireContentsChanged(InvalidRecListModel.this, index, index);
                    break;
                }
                index++;
            }
        }

    }
}
