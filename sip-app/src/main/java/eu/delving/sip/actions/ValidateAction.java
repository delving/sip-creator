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
import java.awt.event.KeyEvent;

import static eu.delving.sip.files.DataSetState.MAPPING;

/**
 * Runs the validation for a file.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class ValidateAction extends AbstractAction {
    private SipModel sipModel;
    private Swing investigate;
    private DataSetMenu dataSetMenu;

    public ValidateAction(SipModel sipModel, DataSetMenu dataSetMenu, Swing investigate) {
        super("<html><b>Map and validate all records</b>");
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
    }

    private void enableAccordingTo(DataSetState dataSetState) {
        setEnabled(dataSetState.atLeast(MAPPING));
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        SipModel.ValidationListener validationListener = new SipModel.ValidationListener() {
            @Override
            public void failed(final FileProcessor fileProcessor, final int recordNumber, final String record, String message) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().setLocked(false);
                        if (isNotCurrent(fileProcessor)) {
                            sipModel.setDataSetPrefix(fileProcessor.getDataSet(), fileProcessor.getPrefix(), new Swing() {
                                @Override
                                public void run() {
                                    dataSetMenu.refreshAndChoose(fileProcessor.getDataSet(), fileProcessor.getPrefix());
                                    investigateRecord(recordNumber);
                                }
                            });
                        }
                        else {
                            investigateRecord(recordNumber);
                        }
                    }
                });
            }

            private void investigateRecord(int recordNumber) {
                sipModel.seekRecordNumber(recordNumber);
                investigate.run();
            }
        };
        sipModel.processFile(validationListener, new Swing() {
            @Override
            public void run() {
                setEnabled(true);
            }
        });
    }

    private boolean isNotCurrent(FileProcessor fileProcessor) {
        boolean dataSetNotCurrent = sipModel.getDataSetModel().isEmpty() || !sipModel.getDataSetModel().getDataSet().getSpec().equals(fileProcessor.getSpec());
        boolean prefixNotCurrent = !(sipModel.getMappingModel().hasRecMapping() && sipModel.getMappingModel().getRecMapping().getPrefix().equals(fileProcessor.getPrefix()));
        return dataSetNotCurrent || prefixNotCurrent;
    }

}
