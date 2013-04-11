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

import org.apache.commons.lang.StringUtils;

import javax.swing.AbstractListModel;
import java.io.File;
import java.io.FileNotFoundException;
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
    private static final long BACKTRACK = 5000;
    private RandomAccessFile randomAccess;
    private List<Rec> recs = new ArrayList<Rec>();
    private Rec lastRec;

    public ReportFile(File file) throws FileNotFoundException {
        this.randomAccess = new RandomAccessFile(file, "r");
    }

    public void indexFile(int records) throws IOException {
        randomAccess.seek(0L);
        while (true) {
            if (records-- <= 0) break;
            Rec rec = new Rec();
            rec.getLines();
            if (lastRec == null) lastRec = rec;
            recs.add(rec);
        }
        long seekEnd = randomAccess.length() - BACKTRACK;
        if (seekEnd < 0) seekEnd = 0;
        randomAccess.seek(seekEnd);
        while (true) {
            Rec rec = new Rec();
            rec.getLines();
            if (rec.getRecordNumber() < 0) break;
            lastRec = rec;
        }
    }

    public Rec getRec(int recordNumber) throws IOException {
        if (recordNumber < recs.size()) {
            return recs.get(recordNumber);
        }
        if (lastRec == null || recordNumber > lastRec.recordNumber) return null;
        Rec endRec = recs.get(recs.size()-1);
        endRec.clear();
        endRec.getLines();
        while (recordNumber >= recs.size() - 1) {
            Rec nextRec = new Rec();
            nextRec.getLines();
            if (nextRec.getRecordNumber() < 0) break;
            recs.add(nextRec);
        }
        return recordNumber < recs.size() ? recs.get(recordNumber) : null;
    }

    @Override
    public int getSize() {
        return lastRec == null ? 0 : lastRec.getRecordNumber() + 1;
    }

    @Override
    public Object getElementAt(int index) {
        try {
            return getRec(index);
        }
        catch (IOException e) {
            return null;
        }
    }

    public class Rec {
        private int recordNumber = -1;
        private long seekPos = -1;
        private List<String> lines;

        public int getRecordNumber() {
            return recordNumber;
        }

        public List<String> getLines() throws IOException {
            if (lines == null) {
                if (seekPos >= 0 && randomAccess.getFilePointer() != seekPos) randomAccess.seek(seekPos);
                lines = new ArrayList<String>();
                boolean startFound = false;
                while (true) {
                    if (!startFound) seekPos = randomAccess.getFilePointer();
                    String line = randomAccess.readLine();
                    if (line == null) {
                        break;
                    }
                    Matcher startMatcher = ReportWriter.START.matcher(line);
                    if (startMatcher.matches()) {
                        recordNumber = Integer.parseInt(startMatcher.group(1));
                        startFound = true;
                        continue;
                    }
                    if (startFound && ReportWriter.END.matcher(line).matches()) break;
                    if (startFound) lines.add(line);
                }
            }
            return lines;
        }

        public String toString() {
            return recordNumber + "\n" + StringUtils.join(lines, '\n');
        }

        public void clear() {
            lines = null;
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("/Users/gerald/DelvingSIPCreator/StandaloneDataSets/sffdf_delving/report_abm.txt");
        ReportFile reportFile = new ReportFile(file);
        reportFile.indexFile(10);
        for (Rec rec : reportFile.recs) {
            System.out.println(rec);
        }
        System.out.println("size = "+ reportFile.getSize());
        System.out.println(reportFile.getElementAt(1872));
    }
}
