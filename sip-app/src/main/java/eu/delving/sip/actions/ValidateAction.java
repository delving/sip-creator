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

package eu.delving.sip.actions;

import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import static eu.delving.sip.files.DataSetState.MAPPING;

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
    private Swing investigate;
    private DataSetMenu dataSetMenu;

    public ValidateAction(JDesktopPane parent, SipModel sipModel, DataSetMenu dataSetMenu, Swing investigate) {
        super("<html><b>Map and validate all records</b>");
        this.parent = parent;
        this.sipModel = sipModel;
        this.dataSetMenu = dataSetMenu;
        this.investigate = investigate;
        setEnabled(false);
        putValue(Action.SMALL_ICON, SwingHelper.VALIDATE_ICON);
        KeyStroke keyStrokeV = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        putValue(Action.ACCELERATOR_KEY, keyStrokeV);
        this.sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                enableAccordingTo(state);
            }
        });
        this.dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Invalid Record", Dialog.ModalityType.APPLICATION_MODAL);
        prepareDialog();
    }

    private void enableAccordingTo(DataSetState dataSetState) {
        setEnabled(dataSetState.atLeast(MAPPING));
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
        JPanel p = new JPanel(new GridLayout(0, 1, 15, 15));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(leftButton(investigateRecordAction));
        p.add(leftButton(allowInvalidRecordsAction));
        dialog.getContentPane().add(p, BorderLayout.CENTER);
        dialog.getContentPane().add(bp, BorderLayout.SOUTH);
        dialog.pack();
    }

    private JButton leftButton(Action action) {
        JButton button = new JButton(action);
        button.setHorizontalAlignment(JButton.LEFT);
        return button;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        performValidation(false);
    }

    private void performValidation(boolean allowInvalidRecords) {
        setEnabled(false);
        SipModel.ValidationListener validationListener = new SipModel.ValidationListener() {
            @Override
            public void failed(final FileProcessor fileProcessor, final int recordNumber, final String record, String message) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        investigateRecordAction.setMarker(fileProcessor, recordNumber);
                        allowInvalidRecordsAction.setMarker(fileProcessor);
                        Dimension all = parent.getSize();
                        Dimension d = dialog.getSize();
                        dialog.setLocation((all.width - d.width) / 2, (all.height - d.height) / 2);
                        dialog.setVisible(true);
                        sipModel.getMappingModel().setLocked(false);
                    }
                });
            }
        };
        sipModel.processFile(allowInvalidRecords, validationListener, new Swing() {

            @Override
            public void run() {
                setEnabled(true);
            }
        });
    }

    private class InvestigateRecordAction extends AbstractAction {
        private FileProcessor fileProcessor;
        private int recordNumber;

        private InvestigateRecordAction() {
            putName("sample_data_set_spec", "pfx", 100000);
        }

        private void setMarker(FileProcessor fileProcessor, int recordNumber) {
            this.fileProcessor = fileProcessor;
            this.recordNumber = recordNumber;
            putName(fileProcessor.getDataSet().getSpec(), fileProcessor.getPrefix(), recordNumber);
        }

        private void putName(String dataSetSpec, String prefix, int recordNumber) {
            putValue(Action.NAME, String.format(
                    "<html><b>Investigate</b> - Fix the %s mapping of data set %s, with invalid record %d in view",
                    prefix, dataSetSpec, recordNumber
            ));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (isNotCurrent(fileProcessor)) {
                sipModel.setDataSetPrefix(fileProcessor.getDataSet(), fileProcessor.getPrefix(), new Swing() {
                    @Override
                    public void run() {
                        dataSetMenu.refreshAndChoose(fileProcessor.getDataSet(), fileProcessor.getPrefix());
                        investigateRecord();
                    }
                });
            }
            else {
                investigateRecord();
            }
        }

        private void investigateRecord() {
            sipModel.seekRecordNumber(recordNumber);
            investigate.run();
            dialog.setVisible(false);
        }
    }

    private class AllowInvalidRecordsAction extends AbstractAction {

        private AllowInvalidRecordsAction() {
            super("<html><b>Accept - Redo</b> - Run the validation again, accepting invalid records");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            dialog.setVisible(false);
            performValidation(true);
        }

        public void setMarker(FileProcessor fileProcessor) {
            setEnabled(!isNotCurrent(fileProcessor));
        }
    }

    private boolean isNotCurrent(FileProcessor fileProcessor) {
        boolean dataSetNotCurrent = sipModel.getDataSetModel().isEmpty() || !sipModel.getDataSetModel().getDataSet().getSpec().equals(fileProcessor.getSpec());
        boolean prefixNotCurrent = !(sipModel.getMappingModel().hasRecMapping() && sipModel.getMappingModel().getRecMapping().getPrefix().equals(fileProcessor.getPrefix()));
        return dataSetNotCurrent || prefixNotCurrent;
    }

}
