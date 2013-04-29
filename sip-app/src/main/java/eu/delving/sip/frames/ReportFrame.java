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
import eu.delving.sip.files.LinkChecker;
import eu.delving.sip.files.LinkFile;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HtmlPanel;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import javax.swing.event.*;
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
    private static final int VISIBLE_JUMP = 25;
    private static final int MAX_ACTIVE_CHECKS = 3;
    private static final JLabel EMPTY_LABEL = new JLabel("No reports available", JLabel.CENTER);
    private JPanel mainPanel = new JPanel(new BorderLayout());
    private int chosenReport = 0;

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
        List<ReportFile> reports = reportFileModel.getReports();
        mainPanel.removeAll();
        chosenReport = 0;
        switch (reports.size()) {
            case 0:
                mainPanel.add(EMPTY_LABEL, BorderLayout.CENTER);
                break;
            case 1:
                mainPanel.add(new ReportPanel(reports.get(0)), BorderLayout.CENTER);
                break;
            default:
                final JTabbedPane tabs = new JTabbedPane();
                for (ReportFile report : reports) {
                    tabs.addTab(report.getPrefix().toUpperCase(), new ReportPanel(report));
                }
                mainPanel.add(tabs, BorderLayout.CENTER);
                tabs.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        chosenReport = tabs.getSelectedIndex();
                    }
                });
                break;
        }
        mainPanel.validate();
    }

    private void linkFile(boolean load, Swing finished) {
        List<ReportFile> reports = sipModel.getReportFileModel().getReports();
        if (chosenReport >= reports.size()) return;
        ReportFile reportFile = reports.get(chosenReport);
        LinkFile linkFile = reportFile.getLinkFile();
        LinkChecker linkChecker = reportFile.getLinkChecker();
        Feedback feedback = sipModel.getFeedback();
        Work work;
        if (load) {
            work = linkFile.load(linkChecker.getMap(), feedback, finished);
        }
        else {
            work = linkFile.save(linkChecker.getMap(), feedback, finished);
        }
        if (work != null) sipModel.exec(work);
    }

    private class ReportPanel extends JPanel implements ActionListener {
        private HtmlPanel htmlPanel = new HtmlPanel("Record Details").addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                    SwingHelper.launchURL(e.getURL().toString());
            }
        });
        private ReportFile.Rec recShowing;
        private List<ReportFile.Rec> activeLinkChecks = new ArrayList<ReportFile.Rec>();
        private ReportFile report;
        private JList list;
        private JToggleButton toggle = new JToggleButton("Automatic Link Checking");
        private LoadAction loadAction = new LoadAction();
        private SaveAction saveAction = new SaveAction();
        private Timer timer = new Timer(123, this);
        private int currentIndex;

        private ReportPanel(final ReportFile report) {
            super(new BorderLayout());
            this.report = report;
            htmlPanel.setHtml("<html><font size=\"+1\"><br><br><center>Select an item in the list");
            createList();
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
            JTabbedPane tabs = new JTabbedPane(JTabbedPane.BOTTOM);
            tabs.addTab("Record Viewing", createListPanel());
            tabs.addTab("Statistics", createStatsPanel());
            add(tabs);
        }

        private JPanel createStatsPanel() {
            JPanel p = new JPanel(new GridLayout(1, 0));
            p.add(new PresenceStatsPanel(report));
            p.add(new LinkStatsPanel(report.getLinkFile()));
            return p;
        }

        private JPanel createListPanel() {
            JPanel p = new JPanel(new GridLayout(1, 0));
            p.add(createLeft());
            p.add(htmlPanel);
            return p;
        }

        private JPanel createLeft() {
            JPanel p = new JPanel(new BorderLayout());
            p.add(scrollV("Output Records for " + report.getPrefix().toUpperCase(), list), BorderLayout.CENTER);
            p.add(createControls(), BorderLayout.SOUTH);
            return p;
        }

        private void createList() {
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
                    Work.DataSetPrefixWork work = rec.checkLinks(sipModel.getFeedback(), new Swing() {
                        @Override
                        public void run() {
                            if (recShowing == rec && (!toggle.isSelected())) {
                                htmlPanel.setHtml(report.toHtml(rec));
                            }
                            activeLinkChecks.remove(rec);
                        }
                    });
                    if (work == null) return;
                    activeLinkChecks.add(rec);
                    sipModel.exec(work);
                }
            });
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
            if (activeLinkChecks.size() >= MAX_ACTIVE_CHECKS) return;
            int visible = Math.min(currentIndex + VISIBLE_JUMP, list.getModel().getSize() - 1);
            if (list.getLastVisibleIndex() < currentIndex + VISIBLE_JUMP / 2) list.ensureIndexIsVisible(visible);
            list.setSelectedIndex(currentIndex);
            currentIndex++;
            if (currentIndex == list.getModel().getSize()) toggle.setSelected(false);
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
                        mainPanel.repaint();
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

    private class PresenceStatsPanel extends JPanel implements ReportFile.PresenceStatsCallback {
        private JPanel center = new JPanel(new BorderLayout());
        private ReportFile reportFile;
        private ChartPanel presencePanel;

        private PresenceStatsPanel(ReportFile reportFile) {
            super(new BorderLayout());
            this.reportFile = reportFile;
            setBorder(BorderFactory.createTitledBorder("Presence Statistics"));
            center.add(new JLabel("soon"));
            add(center, BorderLayout.CENTER);
            add(new JButton(new GatherAction()), BorderLayout.SOUTH);
        }

        @Override
        public void presenceCounts(int[] presence, int totalRecords) {
            presencePanel = ReportChartHelper.createPresenceChart(reportFile.getDataSet(), reportFile.getPrefix(), presence, totalRecords);
        }

        private class GatherAction extends AbstractAction {
            private GatherAction() {
                super("Gather presence statistics");
            }

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setEnabled(false);
                Work work = reportFile.gatherStats(PresenceStatsPanel.this, sipModel.getFeedback(), new Swing() {
                    @Override
                    public void run() {
                        center.removeAll();
                        center.add(presencePanel);
                        center.validate();
                        setEnabled(true);
                    }
                });
                sipModel.exec(work);
            }
        }
    }

    private class LinkStatsPanel extends JPanel implements LinkFile.LinkStatsCallback {
        private JPanel center = new JPanel(new BorderLayout());
        private LinkFile linkFile;

        private LinkStatsPanel(LinkFile linkFile) {
            super(new BorderLayout());
            this.linkFile = linkFile;
            setBorder(BorderFactory.createTitledBorder("Link Statistics"));
            center.add(new JLabel("soon"));
            add(center, BorderLayout.CENTER);
            add(new JButton(new GatherAction()), BorderLayout.SOUTH);
        }

        @Override
        public void linkStatistics(final LinkFile.LinkStats linkStats) {
            sipModel.exec(new Swing() {
                @Override
                public void run() {
                    center.removeAll();
                    center.add(ReportChartHelper.createLinkChart(linkFile.getDataSet(), linkFile.getPrefix(), linkStats));
                    center.validate();
                }
            });
        }

        private class GatherAction extends AbstractAction {
            private GatherAction() {
                super("Gather link statistics");
            }

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setEnabled(false);
                Work work = linkFile.gatherStats(sipModel.getFeedback(), LinkStatsPanel.this, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                    }
                });
                sipModel.exec(work);
            }
        }
    }
}
