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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpStatus;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd");
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

        public Work.SwingAfter checkLinks(final Feedback feedback) {
            if (reportType != VALID) return null;
            if (lines == null) return null;
            final List<String> safeLines = new ArrayList<String>(lines);
            return new Work.DataSetPrefixWorkSwingAfter() {
                private Swing after;

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
                        boolean linkChecked = false;
                        for (String line : safeLines) {
                            Matcher matcher = ReportWriter.LINK.matcher(line);
                            if (!matcher.matches()) continue; // RuntimeException?
                            RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
                            if (!check.fetch) continue;
                            String url = matcher.group(2);
                            if (!linkChecker.contains(url)) {
                                linkChecker.request(url, dataSet.getSpec(), dataSet.getOrganization(), localId);
                                linkChecked = true;
                            }
                        }
                        if (linkChecked) Swing.Exec.later(new Swing() {
                            @Override
                            public void run() {
                                fireContentsChanged(ReportFile.this, recordNumber, recordNumber);
                            }
                        });

                    }
                    catch (IOException e) {
                        feedback.alert("Unable to check link", e);
                    }
                }

                @Override
                public void setAfter(Swing after) {
                    this.after = after;
                }

                @Override
                public Swing getAfter() {
                    return after;
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
                                        out.append(":").append(DATE_FORMAT.format(new Date(linkCheck.time)));
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
                validToHtml(rec, out);
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

    private void validToHtml(Rec rec, StringBuilder out) {
        out.append("<table width='100%'>\n");
        for (String line : rec.lines) {
            Matcher matcher = ReportWriter.LINK.matcher(line);
            if (!matcher.matches()) continue; // RuntimeException?
            RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
            String content = matcher.group(2);
            out.append("<tr><td>");
            out.append(String.format("<table width='100%%'><tr><td><h2>%s</h2></td></tr><td>", check));
            switch (check) {
                case LANDING_PAGE:
                case DIGITAL_OBJECT:
                case THUMBNAIL:
                case DEEP_ZOOM:
                case THESAURUS_REFERENCE:
                case LOD_REFERENCE:
                    out.append(String.format(
                            "<a href=\"%s\">%s<a><br>\n",
                            content, StringEscapeUtils.escapeHtml(content))
                    );
                    if (linkChecker == null || !linkChecker.contains(content)) {
                        out.append("<ul><li>unchecked</li></ul>");
                    }
                    else {
                        if (check == RecDef.Check.THUMBNAIL) {
                            out.append(String.format(
                                    "<table cellpadding=10><tr><td><img src=\"%s\"/></td</tr></table><br>",
                                    content
                            ));
                        }
                        LinkCheck linkCheck = linkChecker.lookup(content);
                        out.append("<ul>\n");
                        out.append(String.format("<li>Ok: %s</li>\n", linkCheck.ok));
                        out.append(String.format("<li>Checked: %s</li>\n", DATE_FORMAT.format(new Date(linkCheck.time))));
                        out.append(String.format("<li>HTTP status: %d</li>\n", linkCheck.httpStatus));
                        out.append(String.format("<li>Status reason: %s</li>\n", linkCheck.getStatusReason()));
                        out.append(String.format("<li>File size: %d</li>\n", linkCheck.fileSize));
                        out.append(String.format("<li>Status reason: %s</li>\n", linkCheck.mimeType));
                        out.append("</ul>\n");
                    }
                    break;
                case GEO_COORDINATE:
                    String thumbnail = String.format(
                            "http://maps.google.com/maps/api/staticmap?center=%s&size=400x400&zoom=10&maptype=roadmap&format=jpg&sensor=false&markers=color:blue%%7Clabel:S%%7C%s",
                            content, content
                    );
                    out.append(String.format(
                            "<table cellpadding=10><tr><td><img src=\"%s\"/></td</tr></table><br>",
                            thumbnail
                    ));
                    out.append(String.format(
                            "<a href=\"https://maps.google.com/maps?q=%s\">%s<a><br>",
                            content, content
                    ));
                    break;
                case DATE:
                    out.append(content);
                    out.append("<ul><li>not checking yet</li></ul>");
                    break;
            }
            out.append("</td></tr></table>");
            out.append("</td><tr>");
        }
        out.append("</table>\n");
    }

    public interface PresenceStatsCallback {

    }

    public Work gatherStats(final PresenceStatsCallback callback, final Swing finished) {
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
                return Job.GATHER_PRESENCE_STATS;
            }

            @Override
            public void run() {
                try {
                    callback.hashCode(); // todo: give something back
                }
                finally {
                    Swing.Exec.later(finished);
                }
            }
        };
    }
}
