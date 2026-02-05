/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.files;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.Feedback;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Consumer;

import static eu.delving.sip.base.SwingHelper.*;

/**
 * Handles navigation in a report file, optimizing where possible
 *
 *
 */

public class ReportFile {
    private final DataSet dataSet;
    private final String prefix;
    private final List<String> reportConclusions;
    private RandomAccessFile reportAccess;
    private List<Rec> reportedRecs;
    private ReportedRecListModel reportedRecListModel = new ReportedRecListModel();
    private int totalCount;
    private int processedCount;

    private ObjectMapper mapper = new ObjectMapper();

    public ReportFile(File reportFile, DataSet dataSet, String prefix) throws IOException {
        this.dataSet = dataSet;
        this.prefix = prefix;

        // Scan report JSON and index locations of record reports and conclusions report.
        // Individual reports will be parsed later as they are needed.
        IndividualReport conclusions = null;
        this.reportedRecs = new ArrayList<>();
        int index = 0;
        if (reportFile != null) {
            JsonFactory jsonFactory = JsonFactory.builder().build();
            JsonParser json = jsonFactory.createParser(reportFile);
            if (json.nextToken() == JsonToken.START_OBJECT) {
                while (json.nextFieldName() != null) {
                    switch (json.currentName()) {
                        case "conclusions":
                            switch (json.nextToken()) {
                                case START_OBJECT:
                                    long reportStart = json.getCurrentLocation().getByteOffset() - 1; // -1 to go back to {
                                    json.skipChildren();
                                    int reportSize = (int) (json.getCurrentLocation().getByteOffset() - reportStart);
                                    conclusions = new IndividualReport(null, reportStart, reportSize);
                                    break;
                                case START_ARRAY:
                                    json.skipChildren();
                                    break;
                            }
                            break;
                        case "records":
                            switch (json.nextToken()) {
                                case START_OBJECT:
                                    while (json.nextFieldName() != null) {
                                        String key = json.currentName();
                                        switch (json.nextToken()) {
                                            case START_OBJECT:
                                                long recordStart = json.getCurrentLocation().getByteOffset() - 1; // -1 to go back to {
                                                json.skipChildren();
                                                int recordSize = (int) (json.getCurrentLocation().getByteOffset() - recordStart);
                                                reportedRecs.add(new Rec(new IndividualReport(key, recordStart, recordSize), index++));
                                                break;
                                            case START_ARRAY:
                                                json.skipChildren();
                                                break;
                                        }
                                    }
                                    break;
                                case START_ARRAY:
                                    json.skipChildren();
                                    break;
                            }
                            break;
                        default:
                            switch (json.nextToken()) {
                                case START_OBJECT:
                                case START_ARRAY:
                                    json.skipChildren();
                                    break;
                            }
                    }
                }
            }
            json.close();
        }

        // Now open the report file with random access to be able to access individual reports as needed
        if (reportFile != null) {
            this.reportAccess = new RandomAccessFile(reportFile, "r");
        }

        // Parse the conclusions report
        if (conclusions != null) {
            JsonNode report = conclusions.getNode();
            reportConclusions = new ArrayList<>();
            this.totalCount = report.has("total") ? report.get("total").asInt() : 0;
            this.processedCount = report.has("processed") ? report.get("processed").asInt() : 0;
            for (Iterator<Map.Entry<String, JsonNode>> it = report.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> i = it.next();
                int value = i.getValue().asInt();
                reportConclusions.add(String.format("<center><b>%s:</b><br>%s<br>(%.1f%%)</center>",
                    StringEscapeUtils.escapeHtml4(StringUtils.capitalize(i.getKey().toLowerCase())),
                    value,  totalCount > 0 ? (((double) value) / totalCount * 100) : 0f));
            }
        }  else {
            List<String> apology = new ArrayList<>();
            apology.add("No conclusions. They should appear after next validation");
            this.reportConclusions = apology;
            this.totalCount = 0;
            this.processedCount = 0;
        }
    }

    class IndividualReport {
        String key;
        long start;
        int size;

        IndividualReport(String key, long start, int size) {
            this.key = key;
            this.start = start;
            this.size = size;
        }

        JsonNode getNode() throws IOException {
            synchronized (reportAccess) {
                byte[] bytes = new byte[size];
                reportAccess.seek(start);
                reportAccess.read(bytes);
                return mapper.readTree(bytes);
            }
        }
    }

    public List<String> getReportConclusions() {
        return reportConclusions;
    }

    public ReportedRecListModel getReportedRecListModel() {
        return reportedRecListModel;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getValidCount() {
        return processedCount;
    }

    public int getInvalidCount() {
        return totalCount - processedCount;
    }

    public Work fetchRecords(final Feedback feedback) {
        return new Work.DataSetPrefixWork() {
            @Override
            public Job getJob() {
                return Job.LOAD_REPORT;
            }

            @Override
            public void run() {
                try {
                    for (Rec rec : reportedRecs) {
                        rec.readIn(true); // Just to update the list item with real data
                        rec.lines = null; // Free memory
                        rec.code = null;
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
        IOUtils.closeQuietly(reportAccess);
    }

    public class Rec {
        private final IndividualReport report;
        private final int index;
        private int recordNumber;
        private ReportWriter.ReportType reportType = ReportWriter.ReportType.UNEXPECTED;
        private String error;
        private List<String> lines;
        private String code;

        public Rec(IndividualReport report, int index) {
            this.report = report;
            this.index = index;
        }

        public int getRecordNumber() {
            return recordNumber;
        }

        public void readIn(boolean updateList) throws IOException {
            if (lines != null) {
                // We're already done
                return;
            }
            final List<String> lines = new ArrayList<>();
            String code = null;
            try {
                JsonNode record = report.getNode();
                this.recordNumber = record.get("recordNumber").asInt();
                this.error = record.get("message").asText();
                if (record.has("warnings")) {
                    JsonNode warnings = record.get("warnings");
                    warnings.forEach(new Consumer<JsonNode>() {
                        @Override
                        public void accept(JsonNode jsonNode) {
                            String[] output = jsonNode.asText().split("\n");
                            lines.addAll(List.of(output));
                            lines.add("");
                        }
                    });
                }
                reportType = ReportWriter.ReportType.valueOf(record.get("type").asText());
                if (record.has("output")) {
                    lines.add("See mapped output:");
                    code = record.get("output").asText();
                } else if (record.has("input")) {
                    lines.add("See input record:");
                    code = record.get("input").asText();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.lines = lines;
            this.code = code;
            if (updateList) {
                // Don't do this unless necessary (when generating list contents) as it slows down selection
                Swing.Exec.later(new Swing() {
                    @Override
                    public void run() {
                        reportedRecListModel.fireContentsChanged(index);
                    }
                });
            }
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
            String string = "<" + rec.getRecordNumber() + ":" + rec.reportType + "> " + rec.error;
            Component component = super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
            if (!isSelected) {
                switch (rec.reportType) {
                    case INVALID:
                        component.setBackground(ERROR_BG);
                        component.setForeground(NORMAL_FG);
                        break;
                    case WARNING:
                        component.setBackground(WARNING_BG);
                        component.setForeground(NORMAL_FG);
                        break;
                }
            }
            return component;
        }
    }

    public String toHtml(Rec rec) {
        if (rec.lines == null) {
            try {
                rec.readIn(false);
            } catch (IOException e) {
                // We tried
            }
        };
        if (rec.lines == null) {
            return "??";
        }
        StringBuilder out = new StringBuilder("<html><table cellpadding=6>\n");
        out.append("<tr><td>\n");
        out.append("<b>").append(rec.error).append("</b><br>\n");
        for (String line : rec.lines) {
            out.append("<br>\n").append(StringEscapeUtils.escapeHtml4(line));
        }
        out.append("</td></tr>\n");
        if (rec.code != null) {
            out.append("</table><table cellpadding=6>\n");
            out.append("<tr><td><pre>");
            out.append(StringEscapeUtils.escapeHtml4(rec.code));
            out.append("</pre></td></tr>\n");
        }
        out.append("</table>");
        rec.lines = null; // Free memory
        rec.code = null;
        return out.toString();
    }

    public class ReportedRecListModel extends AbstractListModel<Rec> {
        @Override
        public int getSize() {
            return reportedRecs.size();
        }

        @Override
        public Rec getElementAt(int index) {
            return reportedRecs.get(index);
        }

        public void fireContentsChanged(int index) {
            fireContentsChanged(ReportedRecListModel.this, index, index);
        }
    }
}
