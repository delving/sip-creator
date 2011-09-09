/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.sip.base;

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Upload files, after having seen the validation report
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class UploadAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private ReportFilePopup reportFilePopup;
    private RealUploadAction realUploadAction = new RealUploadAction();

    public UploadAction(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Upload");
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        this.reportFilePopup = new ReportFilePopup();
        this.sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                setEnabled(dataSetState == DataSetState.VALIDATED);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        addIfAbsent();
        reportFilePopup.setVisible(true);
    }

    private void addIfAbsent() {
        boolean add = true;
        JInternalFrame[] frames = parent.getAllFrames();
        for (JInternalFrame frame : frames) {
            if (frame == reportFilePopup) {
                add = false;
            }
        }
        if (add) {
            reportFilePopup.setLocation(
                    (parent.getSize().width - reportFilePopup.getSize().width) / 2,
                    (parent.getSize().height - reportFilePopup.getSize().height) / 2
            );
            parent.add(reportFilePopup);
        }
    }



    private void setActionEnabled(boolean enabled) {
        setEnabled(enabled);
    }

    private class ReportFilePopup extends JInternalFrame {

        private ListModel reportFileModel = sipModel.getReportFileModel();
        private JList list = new JList(reportFileModel);

        public ReportFilePopup() {
            super(
                    "Validation Report",
                    true, // resizable
                    true, // closeable
                    true, // maximizable
                    false // iconifiable
            );
            sipModel.getReportFileModel().addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent listDataEvent) {
                    setActionEnabled(true);
                }

                @Override
                public void intervalRemoved(ListDataEvent listDataEvent) {
                    setActionEnabled(false);
                }

                @Override
                public void contentsChanged(ListDataEvent listDataEvent) {
                }
            });
            buildContent(getContentPane());
            setSize(600, 400);
        }

        private void buildContent(Container content) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            p.add(FrameBase.scroll(list), BorderLayout.CENTER);
            p.add(createButtons(), BorderLayout.SOUTH);
            content.add(p, BorderLayout.CENTER);
        }

        private JPanel createButtons() {
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    setVisible(false);
                }
            });
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            p.add(cancel);
            p.add(new JButton(realUploadAction));
            return p;
        }
    }

    private class RealUploadAction extends AbstractAction {

        private RealUploadAction() {
            super("Upload to Culture Hub");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            reportFilePopup.setVisible(false);
            if (!sipModel.hasDataSet()) {
                JOptionPane.showInternalMessageDialog(parent, "Data set and mapping must be selected");
                return;
            }
            setEnabled(false);
            String message = String.format(
                    "<html><h3>Uploading the data of '%s' to the culture hub</h3>",
                    sipModel.getDataSetModel().getDataSet().getSpec()
            );
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(parent),
                    "<html><h2>Uploading</h2>",
                    message,
                    0, 100
            );
            try {
                cultureHubClient.uploadFiles(sipModel.getDataSetModel().getDataSet(), new ProgressAdapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(true);
                    }
                });
            }
            catch (StorageException e) {
                JOptionPane.showInternalMessageDialog(parent, "<html>Problem uploading files<br>" + e.getMessage());
            }
            finally {
                setEnabled(true);
            }
        }
    }


}
