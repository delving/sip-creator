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

package eu.delving.sip.model;

import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A list model for showing the contents of the validation files, one for each mapping.  This class rather naively
 * just loads all of the lines of the file, which could be problematic if it is very large.  It should be made
 * more clever when time permits.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFileModel {
    public static final String DIVIDER = "<>=====<>=====<>";
    private SipModel sipModel;
    private Listener listener;
    private List<ProcessingReport> reports = new ArrayList<ProcessingReport>();

    public interface Listener {
        void reportsUpdated(ReportFileModel reportFileModel);
    }

    public ReportFileModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<ProcessingReport> getReports() {
        return reports;
    }

    public void refresh() {
        if (sipModel.getDataSetModel().isEmpty()) return;
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        reports.clear();
        for (SchemaVersion schemaVersion : dataSet.getSchemaVersions()) {
            ProcessingReport processingReport = new ProcessingReport(dataSet, schemaVersion.getPrefix());
            reports.add(processingReport);
            sipModel.exec(processingReport);
        }
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                listener.reportsUpdated(ReportFileModel.this);
            }
        });
    }

    public class ProcessingReport implements Work.DataSetPrefixWork {
        private DataSet dataSet;
        private String prefix;
        private LinesModel invalidListModel = new LinesModel();
        private LinesModel summaryListModel = new LinesModel();

        public ProcessingReport(DataSet dataSet, String prefix) {
            this.dataSet = dataSet;
            this.prefix = prefix;
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

        @Override
        public void run() {
            try {
                List<String> invalid = new ArrayList<String>();
                List<String> summary = new ArrayList<String>();
                if (dataSet.isValidated(prefix)) {
                    List<String> report = dataSet.getReport(prefix);
                    if (report == null) {
                        summary.add("Validated but no report found");
                    }
                    else {
                        boolean seenDivider = false;
                        for (String line : report) {
                            if (line.equals(DIVIDER)) {
                                seenDivider = true;
                            }
                            else if (seenDivider) {
                                summary.add(line);
                            }
                            else {
                                invalid.add(line);
                            }
                        }
                    }
                }
                else {
                    summary.add(String.format("Processing not yet performed for %s.", prefix.toUpperCase()));
                }
                invalidListModel.setLines(invalid);
                sipModel.exec(invalidListModel);
                summaryListModel.setLines(summary);
                sipModel.exec(summaryListModel);
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert(String.format("Unable to load report for %s - %s", getDataSet().getSpec(), getPrefix()), e);
            }
        }

        public ListModel getInvalid() {
            return invalidListModel;
        }

        public ListModel getSummary() {
            return summaryListModel;
        }
    }

    private static class LinesModel extends AbstractListModel implements Swing {
        private List<String> lines;

        public void setLines(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public int getSize() {
            if (lines == null) return 0;
            return lines.size();
        }

        @Override
        public Object getElementAt(int index) {
            return lines.get(index);
        }

        @Override
        public void run() {
            fireIntervalAdded(this, 0, getSize());
        }
    }
}