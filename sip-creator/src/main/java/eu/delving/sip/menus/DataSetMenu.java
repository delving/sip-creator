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

package eu.delving.sip.menus;

import eu.delving.sip.base.Exec;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The menu for choosing from local data sets.
 *
 * @author Gerald de Jong, Beautiful Code BV, <gerald@delving.eu>
 */

public class DataSetMenu extends JMenu {
    private final String SELECTED_SPEC = "selectedSpec";
    private final String SELECTED_PREFIX = "selectedPrefix";
    private SipModel sipModel;

    public DataSetMenu(final SipModel sipModel) {
        super("Data Sets");
        this.sipModel = sipModel;
        refresh();
    }

    public void refreshAndChoose(DataSet dataSet) {
        if (dataSet != null) {
            setPreference(dataSet, null);
        }
        else {
            clearPreference();
        }
        refresh();
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
        removeAll();
        ButtonGroup bg = new ButtonGroup();
        try {
            DataSetItem last = null;
            boolean somethingSelected = false;
            for (DataSet dataSet : sipModel.getStorage().getDataSets().values()) {
                for (String prefix : dataSet.getRecDefPrefixes()) {
                    final DataSetItem item = new DataSetItem(dataSet, prefix);
                    bg.add(item);
                    add(item);
                    item.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent actionEvent) {
                            sipModel.setDataSet(item.getDataSet(), false);
                            sipModel.setMetadataPrefix(item.getPrefix());
                            setPreference(item.getDataSet(), item.getPrefix());
                        }
                    });
                    if (item.isPreferred()) {
                        Exec.swingLater(new Runnable() {
                            @Override
                            public void run() {
                                item.doClick();
                            }
                        });
                        somethingSelected = true;
                    }
                    last = item;
                }
            }
            if (!somethingSelected && last != null) {
                final DataSetItem clicker = last;
                Exec.swingLater(new Runnable() {
                    @Override
                    public void run() {
                        clicker.doClick();
                    }
                });
            }
            if (bg.getButtonCount() == 0) {
                JMenuItem empty = new JMenuItem("No data sets available");
                empty.setEnabled(false);
                add(empty);
            }
        }
        catch (final StorageException e) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    sipModel.getFeedback().alert("Problem loading data set list", e);
                }
            });
        }
    }

    private class DataSetItem extends JRadioButtonMenuItem {

        private DataSet dataSet;
        private String prefix;

        private DataSetItem(DataSet dataSet, String prefix) {
            super(String.format("%s - %s", dataSet.getSpec(), prefix));
            this.dataSet = dataSet;
            this.prefix = prefix;
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        public String getPrefix() {
            return prefix;
        }

        public boolean isPreferred() {
            String selectedSpec = sipModel.getPreferences().get(SELECTED_SPEC, "");
            String selectedPrefix = sipModel.getPreferences().get(SELECTED_PREFIX, "");
            return selectedSpec.equals(dataSet.getSpec()) && selectedPrefix.equals(prefix);
        }
    }
}
