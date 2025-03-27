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

package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HtmlPanel;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;

import static eu.delving.sip.base.SwingHelper.scrollV;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * This frame shows the contents of the ReportFileModel which can contain multiple validation reports, shown in
 * multiple tabs.  The idea is that only after viewing these reports should you be deciding to upload a mapping.
 *
 *
 */

public class ReportFrame extends FrameBase implements ReportFileModel.Listener {
    private static final JLabel EMPTY_LABEL = new JLabel("No reports available", JLabel.CENTER);
    private JPanel mainPanel = new JPanel(new BorderLayout());

    public ReportFrame(SipModel sipModel) {
        super(Which.REPORT, sipModel, "Report");
        mainPanel.add(EMPTY_LABEL);
        sipModel.getReportFileModel().setListener(this);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(mainPanel);
    }

    @Override
    public void reportsUpdated(ReportFileModel reportFileModel) {
        ReportFile reportFile = reportFileModel.getReport();
        mainPanel.removeAll();
        if (reportFile == null) {
            mainPanel.add(EMPTY_LABEL, BorderLayout.CENTER);
        }
        else {
            mainPanel.add(new ReportPanel(reportFile), BorderLayout.CENTER);
        }
        mainPanel.validate();
    }

    private class ReportPanel extends JPanel {
        private HtmlPanel htmlPanel = new HtmlPanel("Record Details").addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                    SwingHelper.launchURL(e.getURL().toString());
            }
        });
        private ReportFile report;
        private JList<ReportFile.Rec> list;

        private ReportPanel(final ReportFile report) {
            super(new BorderLayout());
            this.report = report;
            htmlPanel.setHtml("<html><font size=\"+1\"><br><br><center>Select an item in the list");
            createList();
            add(createListPanel());
        }

        private JPanel createListPanel() {
            JPanel p = new JPanel(new GridLayout(1, 0));
            p.add(createLeft());
            p.add(htmlPanel);
            return p;
        }

        private JPanel createLeft() {
            JPanel p = new JPanel(new BorderLayout());
            p.add(scrollV("Reported Records for " + report.getPrefix().toUpperCase(), list), BorderLayout.CENTER);
            p.add(createConclusionPanel(), BorderLayout.SOUTH);
            return p;
        }

        private JPanel createConclusionPanel() {
            JPanel p = new JPanel(new GridLayout(1, 0));
            p.setBorder(createCompoundBorder(createTitledBorder("Conclusions"),createEmptyBorder(10, 10, 10, 10)));
            for (String line : report.getReportConclusions()) {
                JLabel label = new JLabel(String.format("<html><body>%s", line), JLabel.CENTER);
                label.setOpaque(false);
                label.setBackground(Color.WHITE);
                p.add(label);
            }
            return p;
        }

        @SuppressWarnings("unchecked")
        private void createList() {
            list = new JList<ReportFile.Rec>(report.getReportedRecListModel());
            list.setCellRenderer(report.getCellRenderer()); // too bad this is untyped still
            list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    final ReportFile.Rec rec = list.getSelectedValue();
                    if (rec == null) return;
                    Swing.Exec.later(new Swing() {
                        @Override
                        public void run() {
                            htmlPanel.setHtml(report.toHtml(rec));
                        }
                    });
                }
            });
        }
    }
}
