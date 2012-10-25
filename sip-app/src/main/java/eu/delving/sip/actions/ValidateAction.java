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
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static eu.delving.sip.files.DataSetState.MAPPING;

/**
 * Runs the mapping on all records of the input and validates the resulting records before discarding them (they
 * need not be stored).  Processing also records statistics and a file containing the invalid records and a
 * report of the numbers afterward.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class ValidateAction extends AbstractAction {
    private SipModel sipModel;
    private Swing investigate;

    public ValidateAction(SipModel sipModel, Swing investigate) {
        super("<html><b>Map and validate all records</b>");
        this.sipModel = sipModel;
        this.investigate = investigate;
        setEnabled(false);
        putValue(Action.SMALL_ICON, SwingHelper.ICON_VALIDATE);
        KeyStroke keyStrokeV = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        putValue(Action.ACCELERATOR_KEY, keyStrokeV);
        this.sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                enableAccordingTo(state);
            }
        });
    }

    private void enableAccordingTo(DataSetState dataSetState) {
        setEnabled(dataSetState.atLeast(MAPPING));
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        sipModel.processFile(new FileProcessor.Listener() {
            @Override
            public void failed(final FileProcessor fileProcessor) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().setLocked(false);
                        setEnabled(true);
                        if (isNotCurrent(fileProcessor)) {
                            sipModel.setDataSetPrefix(fileProcessor.getDataSet(), fileProcessor.getPrefix(), new Swing() {
                                @Override
                                public void run() {
//                                    dataSetMenu.refreshAndChoose(fileProcessor.getDataSet(), fileProcessor.getPrefix());
                                    sipModel.seekRecordNumber(fileProcessor.getRecordNumber());
                                    investigate.run();
                                }
                            });
                        }
                        else {
                            sipModel.seekRecordNumber(fileProcessor.getRecordNumber());
                            investigate.run();
                        }
                    }
                });
            }

            @Override
            public void aborted(FileProcessor fileProcessor) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().setLocked(false);
                        setEnabled(true);
                    }
                });
            }

            @Override
            public void succeeded(FileProcessor processor) {
                try {
                    DataSet dataSet = processor.getDataSet();
                    dataSet.setStats(processor.getStats(), false, processor.getPrefix());
                    dataSet.setValidation(processor.getPrefix(), processor.getValid(), processor.getRecordCount());
                }
                catch (StorageException e) {
                    sipModel.getFeedback().alert("Unable to store validation results", e);
                }
                sipModel.getReportFileModel().refresh();
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                    }
                });
            }
        });
    }

    private boolean isNotCurrent(FileProcessor fileProcessor) {
        boolean dataSetNotCurrent = sipModel.getDataSetModel().isEmpty() || !sipModel.getDataSetModel().getDataSet().getSpec().equals(fileProcessor.getSpec());
        boolean prefixNotCurrent = !(sipModel.getMappingModel().hasRecMapping() && sipModel.getMappingModel().getRecMapping().getPrefix().equals(fileProcessor.getPrefix()));
        return dataSetNotCurrent || prefixNotCurrent;
    }

}
