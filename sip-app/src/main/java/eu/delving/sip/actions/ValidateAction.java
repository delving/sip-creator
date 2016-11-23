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
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static eu.delving.sip.base.KeystrokeHelper.configAction;
import static eu.delving.sip.base.SwingHelper.ICON_VALIDATE;
import static eu.delving.sip.files.DataSetState.MAPPING;

/**
 * Runs the mapping on all records of the input and validates the resulting records before discarding them (they
 * need not be stored).  Processing also records statistics and a file containing the invalid records and a
 * report of the numbers afterward.
 *
 */

public class ValidateAction extends AbstractAction {
    private SipModel sipModel;
    private Swing investigate;

    public ValidateAction(SipModel sipModel, Swing investigate) {
        configAction(this, "<html><b>Map and validate all records</b>", ICON_VALIDATE, null);
        this.sipModel = sipModel;
        this.investigate = investigate;
        setEnabled(false);
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

    private boolean allowInvalid() {
        DataSetModel dsm = sipModel.getDataSetModel();
        JRadioButton investigate = new JRadioButton(String.format(
                "<html><b>Validate - Investigate</b> - Validate the %s mapping of data set %s, stopping when necessary",
                dsm.getPrefix(), dsm.getDataSet().getSpec()
        ));
        JRadioButton validateAll = new JRadioButton(String.format(
                "<html><b>Validate - All</b> - Validate all mappings of of data set %s, allowing invalid records",
                dsm.getDataSet().getSpec()
        ));
        ButtonGroup bg = new ButtonGroup();
        bg.add(investigate);
        bg.add(validateAll);
        investigate.setSelected(true);
        return sipModel.getFeedback().form("How to validate?", investigate, validateAll) && validateAll.isSelected();
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        sipModel.processFile(allowInvalid(), new FileProcessor.Listener() {
            @Override
            public void failed(final FileProcessor processor) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().setLocked(false);
                        setEnabled(true);
                        if (isNotCurrent(processor)) {
                            sipModel.setDataSet(processor.getDataSet(), new Swing() {
                                @Override
                                public void run() {
//                                    dataSetMenu.refreshAndChoose(fileProcessor.getDataSet(), fileProcessor.getPrefix());
                                    sipModel.seekRecordNumber(processor.getFailedRecordNumber());
                                    investigate.run();
                                }
                            });
                        }
                        else {
                            sipModel.seekRecordNumber(processor.getFailedRecordNumber());
                            investigate.run();
                        }
                    }
                });
                processor.getDataSet().deleteResults();
                sipModel.getReportFileModel().refresh();
            }

            @Override
            public void aborted(final FileProcessor processor) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().setLocked(false);
                        setEnabled(true);
                    }
                });
                processor.getDataSet().deleteResults();
                sipModel.getReportFileModel().refresh();
            }

            @Override
            public void succeeded(FileProcessor processor) {
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
