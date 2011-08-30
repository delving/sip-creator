/*
 * Copyright 2010 DELVING BV
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

import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
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
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OutputFrame extends FrameBase {
    private JCheckBox allowInvalid = new JCheckBox("Allow invalid records");
    private CultureHubClient cultureHubClient;
    private Action validateAction = new ValidateAction();
    private Action uploadAction = new UploadAction();
    private ValidationFilePopup validationFilePopup = new ValidationFilePopup(this);

    public OutputFrame(JDesktopPane desktop, SipModel sipModel, CultureHubClient cultureHubClient) {
        super(desktop, sipModel, "Output", false);
        this.cultureHubClient = cultureHubClient;
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createOutputPanel(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createSouth() {
        JButton upload = new JButton("Upload dataset and mapping");
        upload.setEnabled(false);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(new JButton(validateAction));
        p.add(allowInvalid);
        p.add(new JButton(validationFilePopup.getAction()));
        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output record"));
        JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setEditable(false);
        p.add(scroll(area));
        return p;
    }

    private class ValidateAction extends AbstractAction {

        private ValidateAction() {
            super("Validate all records");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String message = String.format(
                    "<html><h3>Transforming the raw data of '%s' into '%s' format and validating</h3>",
                    sipModel.getStoreModel().getStore().getSpec(),
                    sipModel.getMappingModel().getRecordMapping().getPrefix()
            );
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(OutputFrame.this),
                    "<html><h2>Validating</h2>",
                    message,
                    0, 100
            );
            sipModel.validateFile(
                    allowInvalid.isSelected(),
                    new ProgressListener.Adapter(progressMonitor) {
                        @Override
                        public void swingFinished(boolean success) {
                            setEnabled(true);
                        }
                    }
            );
        }
    }

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super("Upload to Culture Hub");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!sipModel.hasDataSetStore()) {
                JOptionPane.showInternalMessageDialog(parent, "Data set and mapping must be selected");
                return;
            }
            setEnabled(false);
            String message = String.format(
                    "<html><h3>Uploading the data of '%s' to the culture hub</h3>",
                    sipModel.getStoreModel().getStore().getSpec()
            );
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(parent),
                    "<html><h2>Uploading</h2>",
                    message,
                    0, 100
            );
            try {
                cultureHubClient.uploadFiles(sipModel.getStoreModel().getStore(), new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(true);
                    }
                });
            }
            catch (FileStoreException e) {
                JOptionPane.showInternalMessageDialog(parent, "<html>Problem uploading files<br>" + e.getMessage());
            }
            finally {
                setEnabled(true);
            }
        }
    }

    private class ValidationFilePopup extends FrameBase {

        private ListModel validationFileModel = sipModel.getValidationFileModel();
        private JList list = new JList(validationFileModel);

        public ValidationFilePopup(FrameBase parent) {
            super(parent, parent.getSipModel(), "Validation Report", true);
            getAction().setEnabled(false);
            sipModel.getValidationFileModel().addListDataListener(new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent listDataEvent) {
                    getAction().setEnabled(true);
                }

                @Override
                public void intervalRemoved(ListDataEvent listDataEvent) {
                    getAction().setEnabled(false);
                }

                @Override
                public void contentsChanged(ListDataEvent listDataEvent) {
                }
            });
        }

        @Override
        protected void buildContent(Container content) {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            p.add(scroll(list), BorderLayout.CENTER);
            p.add(createButtons(), BorderLayout.SOUTH);
            content.add(p, BorderLayout.CENTER);
        }

        @Override
        protected void refresh() {
        }

        private JPanel createButtons() {
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    closeFrame();
                }
            });
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            p.add(cancel);
            p.add(new JButton(uploadAction));
            return p;
        }
    }
}
