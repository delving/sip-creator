/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.metadata.ValidationException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * Runs the validation for a file.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class ValidateAction extends AbstractAction {
    private SipModel sipModel;
    private JDialog dialog;
    private JDesktopPane parent;
    private InvestigateRecordAction investigateRecordAction = new InvestigateRecordAction();
    private AllowInvalidRecordsAction allowInvalidRecordsAction = new AllowInvalidRecordsAction();

    public ValidateAction(JDesktopPane parent, SipModel sipModel) {
        super("<html><b>Map and validate all records</b>");
        this.parent = parent;
        this.sipModel = sipModel;
        setEnabled(false);
        putValue(Action.SMALL_ICON, new ImageIcon(getClass().getResource("/validate-icon.png")));
        putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
        this.sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                enableAccordingTo(dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                setEnabled(false);
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                enableAccordingTo(dataSetState);
            }
        });
        this.dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Invalid Record", Dialog.ModalityType.APPLICATION_MODAL);
        prepareDialog();
    }

    private void enableAccordingTo(DataSetState dataSetState) {
        setEnabled(dataSetState.ordinal() >= DataSetState.MAPPING.ordinal());
    }

    private void prepareDialog() {
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.setVisible(false);
            }
        });
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.add(cancel);
        JPanel p = new JPanel(new GridLayout(1, 0, 15, 15));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(new JButton(investigateRecordAction));
        p.add(new JButton(allowInvalidRecordsAction));
        dialog.getContentPane().add(p, BorderLayout.CENTER);
        dialog.getContentPane().add(bp, BorderLayout.SOUTH);
        dialog.pack();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        performValidation(false);
    }

    private void performValidation(boolean allowInvalidRecords) {
        String message = String.format(
                "<html><h3>Transforming the raw data of '%s' into '%s' format and validating</h3>",
                sipModel.getDataSetModel().getDataSet().getSpec(),
                sipModel.getMappingModel().getRecordMapping().getPrefix()
        );
        ProgressListener progressListener = sipModel.getFeedback().progressListener("Validating", message);
        sipModel.validateFile(
                allowInvalidRecords,
                progressListener,
                new SipModel.ValidationListener() {
                    @Override
                    public void failed(ValidationException validationException) {
                        Dimension all = parent.getSize();
                        Dimension d = dialog.getSize();
                        dialog.setLocation((all.width - d.width) / 2, (all.height - d.height) / 2);
                        dialog.setVisible(true);
                    }
                }
        );
    }

    private class InvestigateRecordAction extends AbstractAction {

        private InvestigateRecordAction() {
            super("<html><center><br><h2>Investigate</h2>Fix the mapping, with<br>the invalid record in view<br><br>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
        }
    }

    private class AllowInvalidRecordsAction extends AbstractAction {

        private AllowInvalidRecordsAction() {
            super("<html><center><br><h2>Accept - Redo</h2>Run the validation again<br>accepting invalid records<br><br>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
            performValidation(true);
        }
    }
}
