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

import eu.delving.sip.files.FileStore;
import eu.delving.sip.model.SipModel;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
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
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
    }

    public void refresh() {
        removeAll();
        ButtonGroup bg = new ButtonGroup();
        String selectedSpec = sipModel.getPreferences().get(SELECTED, "");
        for (FileStore.DataSetStore store : sipModel.getFileStore().getDataSetStores().values()) {
            final DataSetItem item = new DataSetItem(store);
            bg.add(item);
            add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    sipModel.setDataSetStore(item.getStore());
                    sipModel.getPreferences().put(SELECTED, item.getStore().getSpec());
                }
            });
            if (sipModel.getDataSetStore() == null && selectedSpec.equals(store.getSpec())) {
                sipModel.setDataSetStore(store);
            }
            if (store == sipModel.getDataSetStore()) {
                item.setSelected(true);
            }
        }
    }

    private class DataSetItem extends JRadioButtonMenuItem {
        private FileStore.DataSetStore store;

        private DataSetItem(FileStore.DataSetStore store) {
            super(store.getSpec());
            this.store = store;
        }

        public FileStore.DataSetStore getStore() {
            return store;
        }
    }
}
