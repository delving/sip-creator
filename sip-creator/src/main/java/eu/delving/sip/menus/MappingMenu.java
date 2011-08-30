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

import eu.delving.metadata.RecordMapping;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.model.DataSetStoreModel;
import eu.delving.sip.model.SipModel;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The menu for choosing from among output mappings
 *
 * @author Gerald de Jong, Beautiful Code BV, <geralddejong@gmail.com>
 */

public class MappingMenu extends JMenu {
    private SipModel sipModel;

    public MappingMenu(SipModel sipModel) {
        super("Mappings");
        this.sipModel = sipModel;
        sipModel.getStoreModel().addListener(new DataSetStoreModel.Listener() {
            @Override
            public void storeSet(FileStore.DataSetStore store) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }

            @Override
            public void storeStateChanged(FileStore.DataSetStore store, FileStore.StoreState storeState) {
            }
        });
    }

    private void refresh() {
        removeAll();
        String currentPrefix = null;
        RecordMapping recordMapping = sipModel.getMappingModel().getRecordMapping();
        if (recordMapping != null) {
            currentPrefix = recordMapping.getPrefix();
        }
        if (currentPrefix == null && sipModel.getStoreModel().hasStore()) {
            currentPrefix = sipModel.getStoreModel().getStore().getLatestPrefix();
            if (currentPrefix.isEmpty()) {
                currentPrefix = null;
            }
            else {
                sipModel.setMetadataPrefix(currentPrefix);
            }
        }
        ButtonGroup bg = new ButtonGroup();
        for (String prefix : sipModel.getMetadataModel().getPrefixes()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(prefix, prefix.equals(currentPrefix));
            add(item);
            bg.add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    sipModel.setMetadataPrefix(actionEvent.getActionCommand());
                }
            });
        }
    }
}
