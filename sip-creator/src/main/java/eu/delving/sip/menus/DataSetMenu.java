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
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * The menu for choosing from local data sets.
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 */

public class DataSetMenu extends JMenu {
    private final String SELECTED = "datasetSelected";
    private SipModel sipModel;

    public DataSetMenu(final SipModel sipModel) {
        super("Data Sets");
        this.sipModel = sipModel;
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void factChanged() {
            }

            @Override
            public void select(FieldMapping fieldMapping) {
            }

            @Override
            public void fieldMappingChanged() {
            }

            @Override
            public void recordMappingChanged(RecordMapping recordMapping) {
            }

            @Override
            public void recordMappingSelected(RecordMapping recordMapping) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
            }

            @Override
            public void dataSetRemoved() {
                refresh();
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
            }
        });
        String selectedSpec = sipModel.getPreferences().get(SELECTED, "");
        if (!selectedSpec.isEmpty()) {
            final DataSet dataSet = sipModel.getStorage().getDataSets().get(selectedSpec);
            if (dataSet != null) {
                sipModel.setDataSet(dataSet, true);
            }
            else {
                refresh();
            }
        }
        else {
            refresh();
        }
    }

    public void setPreference(DataSet dataSet) {
        sipModel.getPreferences().put(SELECTED, dataSet.getSpec());
    }

    public void refresh() {
        removeAll();
        ButtonGroup bg = new ButtonGroup();
        try {
            DataSet last = null;
            boolean selected = false;
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
                            setPreference(item.getDataSet());
                        }
                    });
                    if (sipModel.hasDataSet()) {
                        DataSet existing = sipModel.getDataSetModel().getDataSet();
                        RecordMapping mapping = sipModel.getMappingModel().getRecordMapping();
                        String prefix = mapping == null ? null : mapping.getPrefix();
                        if (dataSet.getSpec().equals(existing.getSpec()) && recordDefinition.prefix.equals(prefix)) {
                            item.setSelected(true);
                            selected = true;
                        }
                    }
                    last = dataSet;
                }
            }
            if (!selected) {
                if (sipModel.hasDataSet()) {
                    sipModel.setMetadataPrefix(sipModel.getDataSetModel().getDataSet().getLatestPrefix(), true);
                }
                else if (last != null) {
                    sipModel.setDataSet(last, true);
                }
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
    }
}
