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
import org.apache.commons.lang.StringUtils;

import javax.swing.AbstractListModel;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

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

    public Fetch prepareFetch() {
        Fetch fetch = new Fetch();
        int index = 0;
        for (Rec rec : recs) {
            if (rec.needsReading()) {
                fetch.recsToRead.add(rec);
                fetch.lastIndex = index;
            }
            index++;
        }
        return fetch;
    }

    public void maintainCache() {
        int count = 0;
        for (Rec rec : recs) if (rec.lines != null) count++;
        long tenSecondsAgo = System.currentTimeMillis() - 10 * 60 * 1000;
        if (count > MAX_CACHE) for (Rec rec : recs) if (rec.touch < tenSecondsAgo) {
            rec.lines = null;
        }
    }

    public class Fetch {
        private List<Rec> recsToRead = new ArrayList<Rec>();
        private int lastIndex;
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
                for (Rec rec : safeRecs) {
                    if (rec.getRecordNumber() >= 0) {
                        lastReadRec = rec;
                    }
                }
                try {
                    int from = 0;
                    if (lastReadRec == null) {
                        randomAccess.seek(0);
                    }
                    else {
                        lastReadRec.clear();
                        lastReadRec.readIn(true);
                        from = lastReadRec.getRecordNumber();
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
        private List<String> lines;
        private ReportWriter.ReportType reportType;

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
                    recordNumber = Integer.parseInt(startMatcher.group(1));
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

        public String toString() {
            if (lines == null) {
                return "loading...";
            }
            return String.format("%6d %s %s", recordNumber, reportType, StringUtils.join(lines, ' '));
        }

        public boolean needsReading() {
            return recordNumber < 0 && touch > 0;
        }

        public void clear() {
            lines = null;
        }

        public void activate() {
            touch = System.currentTimeMillis();
            if (lines != null) return;
        }
    }
}
