/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.actions;

import eu.delving.sip.base.*;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Upload files, after having seen the validation report
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class UploadAction extends AbstractAction {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private ReportFilePopup reportFilePopup;
    private RealUploadAction realUploadAction = new RealUploadAction();
    private List<String> invalidPrefixes = new ArrayList<String>();
    private boolean busyUploading;

    public UploadAction(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Upload this data set");
        putValue(Action.SMALL_ICON, SwingHelper.UPLOAD_ICON);
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_U, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        this.reportFilePopup = new ReportFilePopup();
        this.sipModel.getDataSetModel().addListener(new Enabler());
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        if (realUploadAction.isEnabled()) {
            addIfAbsent();
            reportFilePopup.setVisible(true);
            reportFilePopup.upload.requestFocusInWindow();
        }
        else {
            sipModel.getFeedback().alert("Upload not permitted until all mappings are validated. Still invalid: " + invalidPrefixes);
        }
    }

    private void addIfAbsent() {
        boolean add = true;
        JInternalFrame[] frames = parent.getAllFrames();
        for (JInternalFrame frame : frames) if (frame == reportFilePopup) add = false;
        if (add) {
            reportFilePopup.setLocation(
                    (parent.getSize().width - reportFilePopup.getSize().width) / 2,
                    (parent.getSize().height - reportFilePopup.getSize().height) / 2
            );
            parent.add(reportFilePopup);
        }
    }

    private class InvalidPrefixesFetcher implements Work {
        @Override
        public void run() {
            try {
                final List<String> freshInvalidPrefixes = sipModel.getDataSetModel().getInvalidPrefixes();
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        invalidPrefixes = freshInvalidPrefixes;
                        realUploadAction.setEnabled(invalidPrefixes.isEmpty());
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to fetch invalid prefixes", e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FIND_INVALID_PREFIXES;
        }
    }

    private class Enabler implements DataSetModel.SwingListener {
        @Override
        public void stateChanged(DataSetModel model, DataSetState state) {
            if (realUploadAction.isEnabled()) realUploadAction.setEnabled(false);
            if (state.atLeast(DataSetState.VALIDATED)) sipModel.exec(new InvalidPrefixesFetcher());
        }
    }

    private class ReportFilePopup extends JInternalFrame {

        private ListModel reportFileModel = sipModel.getReportFileModel();
        private JList list = new JList(reportFileModel);
        private JButton upload;

        public ReportFilePopup() {
            super(
                    "Validation Report",
                    true, // resizable
                    true, // closeable
                    true, // maximizable
                    false // iconifiable
            );
            buildContent(getContentPane());
            setSize(600, 400);
        }

        private void buildContent(Container content) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            p.add(SwingHelper.scrollV(list), BorderLayout.CENTER);
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
            p.add(upload = new JButton(realUploadAction));
            getRootPane().setDefaultButton(upload);
            return p;
        }
    }

    private class RealUploadAction extends AbstractAction {

        private RealUploadAction() {
            super("Upload to Culture Hub");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (busyUploading) return;
            if (sipModel.getDataSetModel().isEmpty()) {
                sipModel.getFeedback().alert("Data set and mapping must be selected");
                return;
            }
            try {
                busyUploading = true;
                reportFilePopup.setVisible(false);
                cultureHubClient.uploadFiles(sipModel.getDataSetModel().getDataSet(), new CultureHubClient.UploadListener() {
                    @Override
                    public void uploadRefused(File file) {
                        sipModel.getFeedback().say(String.format("Hub already has %s", file.getName()));
                    }

                    @Override
                    public void uploadStarted(File file) {
                        sipModel.getFeedback().say(String.format("Uploading %s...", file.getName()));
                    }

                    @Override
                    public ProgressListener getProgressListener() {
                        ProgressListener listener = sipModel.getFeedback().progressListener("Uploading");
                        listener.setProgressMessage(String.format(
                                "<html><h3>Uploading the data of '%s' to the culture hub</h3>",
                                sipModel.getDataSetModel().getDataSet().getSpec()
                        ));
                        listener.setIndeterminateMessage(String.format(
                                "<html><h3>Culture hub is processing '%s' metadata</h3>",
                                sipModel.getDataSetModel().getDataSet().getSpec()
                        ));
                        return listener;
                    }

                    @Override
                    public void finished(final boolean success) {
                        busyUploading = false;
                        sipModel.getFeedback().alert(success ? "Upload complete" : "Upload failed");
                        sipModel.exec(new Swing() {
                            @Override
                            public void run() {
                                disappear();
                            }
                        });
                    }
                });
            }
            catch (final StorageException e) {
                sipModel.getFeedback().alert("Unable to complete uploading", e);
                busyUploading = false;
            }
        }

        private void disappear() {
            reportFilePopup.setVisible(false);
            busyUploading = false;
        }
    }
}
