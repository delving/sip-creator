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
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.scrollV;

/**
 * This frame shows the contents of the ReportFileModel which can contain multiple validation reports, shown in
 * multiple tabs.  The idea is that only after viewing these reports should you be deciding to upload a mapping.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFrame extends FrameBase implements ReportFileModel.Listener {
    private static Logger log = Logger.getLogger(ReportFrame.class);
    private static final int VISIBLE_JUMP = 25;
    private static final int MAX_ACTIVE_CHECKS = 3;
    private static final JLabel EMPTY_LABEL = new JLabel("No reports available", JLabel.CENTER);
    private JPanel listPanel = new JPanel(new BorderLayout());
    private HtmlPanel htmlPanel = new HtmlPanel("Details");
    private int chosenReport = 0;
    private ReportFile.Rec recShowing;

    public ReportFrame(SipModel sipModel) {
        super(Which.REPORT, sipModel, "Report");
        listPanel.add(EMPTY_LABEL);
        sipModel.getReportFileModel().setListener(this);
    }

    @Override
    protected void buildContent(Container content) {
        content.setLayout(new GridLayout(1, 0));
        content.add(listPanel);
        content.add(htmlPanel);
    }

    @Override
    public void reportsUpdated(ReportFileModel reportFileModel) {
        List<ReportFile> reports = reportFileModel.getReports();
        listPanel.removeAll();
        switch (reports.size()) {
            case 0:
                listPanel.add(EMPTY_LABEL, BorderLayout.CENTER);
                break;
            case 1:
                listPanel.add(new ListPanel(reports.get(0)), BorderLayout.CENTER);
                break;
            default:
                final JTabbedPane tabs = new JTabbedPane();
                for (ReportFile report : reports) {
                    tabs.addTab(report.getPrefix().toUpperCase(), new ListPanel(report));
                }
                listPanel.add(tabs, BorderLayout.CENTER);
                tabs.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        chosenReport = tabs.getSelectedIndex();
                    }
                });
                break;
        }
        listPanel.validate();
        htmlPanel.setHtml("<html><h4>Select an item</h4>");
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

    private class ListPanel extends JPanel implements ActionListener {
        private List<Work> activeLinkChecks = new ArrayList<Work>();
        private ReportFile report;
        private JList list;
        private JToggleButton toggle = new JToggleButton("Automatic Link Checking");
        private LoadAction loadAction = new LoadAction();
        private SaveAction saveAction = new SaveAction();
        private Timer timer = new Timer(123, this);
        private int currentIndex;

        private ListPanel(final ReportFile report) {
            super(new BorderLayout());
            this.report = report;
            list = new JList(report);
            list.setCellRenderer(report.getCellRenderer());
            list.setPrototypeCellValue("One single line of something or other");
            list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    final ReportFile.Rec rec = (ReportFile.Rec) list.getSelectedValue();
                    if (rec == null) return;
                    recShowing = rec;
                    if (!toggle.isSelected()) htmlPanel.setHtml(report.toHtml(rec));
                    final Work.SwingAfter work = rec.checkLinks(sipModel.getFeedback());
                    if (work == null) return;
                    activeLinkChecks.add(work);
                    work.setAfter(new Swing() {
                        @Override
                        public void run() {
                            if (recShowing == rec && (!toggle.isSelected())) {
                                htmlPanel.setHtml(report.toHtml(rec));
                            }
                            activeLinkChecks.remove(work);
                        }
                    });
                    sipModel.exec(work);
                }
            });
            timer.setRepeats(true);
            toggle.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        currentIndex = list.getSelectedIndex();
                        linkFileSetEnabled(false);
                        timer.start();
                        list.setEnabled(false);
                    }
                    else {
                        timer.stop();
                        linkFileSetEnabled(true);
                        list.setEnabled(true);
                    }
                }
            });
            add(scrollV("Records" + report.getPrefix().toUpperCase(), list), BorderLayout.CENTER);
            add(createControls(), BorderLayout.SOUTH);
        }

        private JPanel createControls() {
            JPanel p = new JPanel();
            p.setBorder(BorderFactory.createTitledBorder("Controls"));
            p.add(new JButton(loadAction));
            p.add(toggle);
            p.add(new JButton(saveAction));
            return p;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (activeLinkChecks.size() >= MAX_ACTIVE_CHECKS) {
                log.info("Active Links = " + activeLinkChecks.size() + ", waiting");
                return;
            }
            int visible = Math.min(currentIndex + VISIBLE_JUMP, list.getModel().getSize() - 1);
            if (list.getLastVisibleIndex() < currentIndex + VISIBLE_JUMP / 2) {
                list.ensureIndexIsVisible(visible);
            }
            list.setSelectedIndex(currentIndex);
            currentIndex++;
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
                        listPanel.repaint();
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


    }

}
