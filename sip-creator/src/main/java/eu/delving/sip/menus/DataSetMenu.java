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

import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.RecordDefinition;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * The menu for choosing from local data sets.
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
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
        String latestPrefix = dataSet.getLatestPrefix();
        if (latestPrefix != null) {
            setPreference(dataSet, latestPrefix);
        }
        refresh();
    }

    private void setPreference(DataSet dataSet, String prefix) {
        sipModel.getPreferences().put(SELECTED_SPEC, dataSet.getSpec());
        sipModel.getPreferences().put(SELECTED_PREFIX, prefix);
    }

    private void refresh() {
        removeAll();
        ButtonGroup bg = new ButtonGroup();
        try {
            DataSetItem last = null, preferred = null;
            for (DataSet dataSet : sipModel.getStorage().getDataSets().values()) {
                List<FactDefinition> factDefinitions = dataSet.getFactDefinitions();
                for (RecordDefinition recordDefinition : dataSet.getRecordDefinitions(factDefinitions)) {
                    final DataSetItem item = new DataSetItem(dataSet, recordDefinition.prefix);
                    bg.add(item);
                    add(item);
                    item.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent actionEvent) {
                            sipModel.setDataSet(item.getDataSet(), false);
                            sipModel.setMetadataPrefix(item.getPrefix(), true);
                            setPreference(item.getDataSet(), item.getPrefix());
                        }
                    });
                    if (item.isPreferred()) preferred = item;
                    last = item;
                }
            }
            if (preferred != null) {
                preferred.doClick();
            }
            else if (last != null) {
                last.doClick();
            }
            if (bg.getButtonCount() == 0) {
                JMenuItem empty = new JMenuItem("No data sets available");
                empty.setEnabled(false);
                add(empty);
            }
        }
        catch (StorageException e) {
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
