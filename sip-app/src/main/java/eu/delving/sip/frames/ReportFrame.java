/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.sip.base.*;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.scrollV;

/**
 * This frame shows the contents of the ReportFileModel which can contain multiple validation reports, shown in
 * multiple tabs.  The idea is that only after viewing these reports should you be deciding to upload a mapping.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFrame extends FrameBase implements ReportFileModel.Listener {
    public static final JLabel EMPTY_LABEL = new JLabel("No reports available", JLabel.CENTER);
    private JPanel center = new JPanel(new BorderLayout());
    private CultureHubClient cultureHubClient;
    private UploadAction uploadAction = new UploadAction();
    private JButton uploadButton = new JButton(uploadAction);

    public ReportFrame(SipModel sipModel, CultureHubClient cultureHubClient) {
        super(Which.REPORT, sipModel, "Report");
        this.cultureHubClient = cultureHubClient;
        center.add(EMPTY_LABEL);
        sipModel.getReportFileModel().setListener(this);
    }

    public Action getUploadAction() {
        return uploadAction;
    }

    @Override
    protected void buildContent(Container content) {
        content.add(center, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(uploadButton);
        content.add(south, BorderLayout.SOUTH);
    }

    @Override
    public void reportsUpdated(ReportFileModel reportFileModel) {
        List<ReportFileModel.ProcessingReport> reports = reportFileModel.getReports();
        center.removeAll();
        switch (reports.size()) {
            case 0:
                center.add(EMPTY_LABEL, BorderLayout.CENTER);
                break;
            case 1:
                center.add(createReportPanel(reports.get(0)), BorderLayout.CENTER);
                break;
            default:
                JTabbedPane tabs = new JTabbedPane();
                for (ReportFileModel.ProcessingReport report : reports) {
                    tabs.addTab(report.getPrefix().toUpperCase(), createReportPanel(report));
                }
                center.add(tabs, BorderLayout.CENTER);
                break;
        }
        center.validate();
    }

    private JPanel createReportPanel(ReportFileModel.ProcessingReport report) {
        JPanel p = new JPanel(new BorderLayout());
        JList invalid = new JList(report.getInvalid());
        JList summary = new JList(report.getSummary());
        p.add(scrollV("Summary", summary), BorderLayout.NORTH);
        p.add(scrollV("Invalid Records", invalid), BorderLayout.CENTER);
        return p;
    }

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super("Upload");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            DataSet dataSet = sipModel.getDataSetModel().getDataSet();
            final InvalidPrefixesFetcher fetcher = new InvalidPrefixesFetcher(dataSet);
            fetcher.swing = new Swing() {
                @Override
                public void run() {
                    if (fetcher.invalidPrefixes.isEmpty()) {
                        try {
                            cultureHubClient.uploadFiles(sipModel.getDataSetModel().getDataSet(), new Swing() {
                                @Override
                                public void run() {
                                    setEnabled(true);
                                }
                            });
                        }
                        catch (final StorageException e) {
                            sipModel.getFeedback().alert("Unable to complete uploading", e);
                            setEnabled(true);
                        }
                    }
                    else {
                        sipModel.getFeedback().alert(String.format("Upload not permitted until all mappings are validated. Still missing: %s.", fetcher.invalidPrefixes));
                        setEnabled(true);
                    }
                }
            };
            sipModel.exec(fetcher);
        }
    }

    private class InvalidPrefixesFetcher implements Work.DataSetWork {
        private DataSet dataSet;
        private Swing swing;
        private List<String> invalidPrefixes;

        private InvalidPrefixesFetcher(DataSet dataSet) {
            this.dataSet = dataSet;
        }

        @Override
        public void run() {
            try {
                invalidPrefixes = sipModel.getDataSetModel().getInvalidPrefixes();
                sipModel.exec(swing);
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to fetch invalid prefixes", e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FIND_INVALID_PREFIXES;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }


}
