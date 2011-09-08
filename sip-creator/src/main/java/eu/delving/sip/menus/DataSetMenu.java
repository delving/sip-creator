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

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The menu for choosing from local data sets
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 */

public class DataSetMenu extends JMenu {
    private final String SELECTED = "datasetSelected";
    private SipModel sipModel;

    public DataSetMenu(SipModel sipModel) {
        super("Data Sets");
        this.sipModel = sipModel;
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {

            @Override
            public void dataSetChanged(DataSet dataSet) {
                refresh();
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                refresh();
            }
        });
        String selectedSpec = sipModel.getPreferences().get(SELECTED, "");
        if (!selectedSpec.isEmpty()) {
            final DataSet dataSet = sipModel.getStorage().getDataSets().get(selectedSpec);
            if (dataSet != null) {
                sipModel.setDataSet(dataSet);
            }
        }
        refresh();
    }

    public void setPreference(DataSet dataSet) {
        sipModel.getPreferences().put(SELECTED, dataSet.getSpec());
    }

    public void refresh() {
        removeAll();
        ButtonGroup bg = new ButtonGroup();
        for (DataSet dataSet : sipModel.getStorage().getDataSets().values()) {
            final DataSetItem item = new DataSetItem(dataSet);
            bg.add(item);
            add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    sipModel.setDataSet(item.getDataSet());
                    setPreference(item.getDataSet());
                }
            });
            if (sipModel.hasDataSet()) {
                if (dataSet.getSpec().equals(sipModel.getDataSetModel().getDataSet().getSpec())) {
                    item.setSelected(true);
                }
            }
        }
    }

    private class DataSetItem extends JRadioButtonMenuItem {
        private DataSet dataSet;

        private DataSetItem(DataSet dataSet) {
            super(dataSet.getSpec());
            this.dataSet = dataSet;
        }

        public DataSet getDataSet() {
            return dataSet;
        }
    }
}
