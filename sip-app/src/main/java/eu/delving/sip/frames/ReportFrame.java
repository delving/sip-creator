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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.LinkChecker;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HtmlPanel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
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
    private JPanel left = new JPanel(new BorderLayout());
    private HtmlPanel htmlPanel = new HtmlPanel("Details");
    private LoadAction loadAction = new LoadAction();
    private SaveAction saveAction = new SaveAction();
    private int chosenReport = 0;
    private ReportFile.Rec recShowing;

    public ReportFrame(SipModel sipModel) {
        super(Which.REPORT, sipModel, "Report");
        left.add(EMPTY_LABEL);
        sipModel.getReportFileModel().setListener(this);
    }

    @Override
    protected void buildContent(Container content) {
        content.setLayout(new GridLayout(1, 0));
        content.add(left);
        content.add(createRight());
    }

    private JPanel createRight() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(htmlPanel, BorderLayout.CENTER);
        p.add(createControls(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createControls() {
        JPanel p = new JPanel();
        p.add(new JButton(loadAction));
        p.add(new JButton(saveAction));
        return p;
    }

    @Override
    public void reportsUpdated(ReportFileModel reportFileModel) {
        List<ReportFile> reports = reportFileModel.getReports();
        left.removeAll();
        switch (reports.size()) {
            case 0:
                left.add(EMPTY_LABEL, BorderLayout.CENTER);
                break;
            case 1:
                left.add(createReportPanel(reports.get(0)), BorderLayout.CENTER);
                break;
            default:
                final JTabbedPane tabs = new JTabbedPane();
                for (ReportFile report : reports) {
                    tabs.addTab(report.getPrefix().toUpperCase(), createReportPanel(report));
                }
                left.add(tabs, BorderLayout.CENTER);
                tabs.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        chosenReport = tabs.getSelectedIndex();
                    }
                });
                break;
        }
        left.validate();
    }

    private JComponent createReportPanel(final ReportFile report) {
        final JList list = new JList(report);
        list.setCellRenderer(report.getCellRenderer());
        list.setPrototypeCellValue("One single line of something or other");
        list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                final ReportFile.Rec rec = (ReportFile.Rec) list.getSelectedValue();
                if (rec == null) return;
                recShowing = rec;
                htmlPanel.setHtml(report.toHtml(rec));
                Work work = rec.checkLinks(sipModel.getFeedback(), new Swing() {
                    @Override
                    public void run() {
                        if (recShowing == rec) {
                            htmlPanel.setHtml(report.toHtml(rec));
                        }
                    }
                });
                if (work == null) return;
                sipModel.exec(work);
            }
        });
        return scrollV("Report", list);
    }

    private class LoadAction extends AbstractAction {

        private LoadAction() {
            super("Load Link Checks");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            linkFileSetEnabled(false);
            linkFile(true, new Swing() {
                @Override
                public void run() {
                    linkFileSetEnabled(true);
                }
            });
        }
    }

    private class SaveAction extends AbstractAction {

        private SaveAction() {
            super("Save Link Checks");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            linkFileSetEnabled(false);
            linkFile(false, new Swing() {
                @Override
                public void run() {
                    linkFileSetEnabled(true);
                }
            });
        }
    }

    private void linkFileSetEnabled(boolean enabled) {
        loadAction.setEnabled(enabled);
        saveAction.setEnabled(enabled);
    }

    private void linkFile(boolean load, Swing finished) {
        List<ReportFile> reports = sipModel.getReportFileModel().getReports();
        if (chosenReport >= reports.size()) return;
        LinkChecker linkChecker = reports.get(chosenReport).getLinkChecker();
        if (linkChecker == null) return;
        Feedback feedback = sipModel.getFeedback();
        Work work = load ? linkChecker.load(feedback, finished) : linkChecker.save(feedback, finished);
        if (work != null) sipModel.exec(work);
    }
}
