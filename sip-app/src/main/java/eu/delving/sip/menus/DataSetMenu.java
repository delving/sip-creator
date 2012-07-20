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

package eu.delving.sip.menus;

import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;

/**
 * The menu for choosing from local dataset/prefix combinations, which triggers the SipModel to load when
 * choices are made, and which gets updated when things on disk may have changed.  There is also a connection
 * with the WorkModel which prevents datasets from being selected when there is a long-term job busy on them.
 *
 * @author Gerald de Jong, Beautiful Code BV, <gerald@delving.eu>
 */

public class DataSetMenu extends JMenu {
    private final String SELECTED_SPEC = "selectedSpec";
    private final String SELECTED_PREFIX = "selectedPrefix";
    private SipModel sipModel;
    private UnlockMappingAction unlockMappingAction = new UnlockMappingAction();
    private ButtonGroup buttonGroup;

    public DataSetMenu(final SipModel sipModel) {
        super("Data Sets");
        this.sipModel = sipModel;
        Timer disableTimer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disableBusy();
            }
        });
        disableTimer.setRepeats(true);
        disableTimer.start();
        refresh();
    }

    public Action getUnlockMappingAction() {
        return unlockMappingAction;
    }

    public void refreshAndChoose(DataSet dataSet, String prefix) {
        if (dataSet != null) {
            setPreference(dataSet, prefix);
        }
        else {
            clearPreference();
        }
        refresh();
    }

    public void disableBusy() {
        if (buttonGroup == null) return;
        Enumeration<AbstractButton> buttons = buttonGroup.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton button = buttons.nextElement();
            DataSetItem dataSetItem = (DataSetItem) button;
            dataSetItem.checkDataSetBusy();
        }
    }

    private void clearPreference() {
        sipModel.getPreferences().put(SELECTED_SPEC, "");
        sipModel.getPreferences().put(SELECTED_PREFIX, "");
    }

    private void setPreference(DataSet dataSet, String prefix) {
        sipModel.getPreferences().put(SELECTED_SPEC, dataSet.getSpec());
        if (prefix == null) prefix = dataSet.getLatestPrefix();
        if (prefix == null) prefix = "";
        sipModel.getPreferences().put(SELECTED_PREFIX, prefix);
    }

    private void refresh() {
        buttonGroup = new ButtonGroup();
        removeAll();
        add(unlockMappingAction);
        addSeparator();
        try {
            for (DataSet dataSet : sipModel.getStorage().getDataSets().values()) {
                for (String prefix : dataSet.getPrefixes()) {
                    final DataSetItem item = new DataSetItem(dataSet, prefix);
                    buttonGroup.add(item);
                    add(item);
                    item.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent actionEvent) {
                            sipModel.setDataSetPrefix(item.getDataSet(), item.getPrefix(), new Swing() {
                                @Override
                                public void run() {
                                    setPreference(item.getDataSet(), item.getPrefix());
                                }
                            });
                        }
                    });
                    if (item.isPreferred()) sipModel.exec(new Swing() {
                        @Override
                        public void run() {
                            item.doClick();
                        }
                    });
                }
            }
            if (buttonGroup.getButtonCount() == 0) {
                JMenuItem empty = new JMenuItem("No data sets available");
                empty.setEnabled(false);
                add(empty);
            }
        }
        catch (final StorageException e) {
            sipModel.getFeedback().alert("Problem loading data set list", e);
        }
    }

    private class DataSetItem extends JRadioButtonMenuItem {

        private DataSet dataSet;
        private String prefix;

        private DataSetItem(DataSet dataSet, String prefix) {
            super(String.format("%s - %s", dataSet.getSpec(), prefix));
            this.dataSet = dataSet;
            this.prefix = prefix;
            checkDataSetBusy();
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        public String getPrefix() {
            return prefix;
        }

        public void checkDataSetBusy() {
            setEnabled(!sipModel.getWorkModel().isDataSetBusy(dataSet.getSpec()));
        }

        public boolean isPreferred() {
            String selectedSpec = sipModel.getPreferences().get(SELECTED_SPEC, "");
            String selectedPrefix = sipModel.getPreferences().get(SELECTED_PREFIX, "");
            return selectedSpec.equals(dataSet.getSpec()) && selectedPrefix.equals(prefix);
        }
    }

    private class UnlockMappingAction extends AbstractAction implements Work {

        private UnlockMappingAction() {
            super("Unlock this mapping for further editing");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.exec(this);
        }

        @Override
        public void run() {
            sipModel.getMappingModel().setLocked(false);
            try {
                sipModel.getDataSetModel().deleteValidation();
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to delete validation", e);
            }
        }

        @Override
        public Job getJob() {
            return Job.UNLOCK_MAPPING;
        }
    }


}
