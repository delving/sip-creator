/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.frames;

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.model.DataSetStoreModel;
import eu.delving.sip.model.FactModel;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Show the facts and the status of the current dataset/mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatusFrame extends FrameBase {
    private FactsTableModel factsTableModel = new FactsTableModel();
    private JLabel statusLabel = new JLabel();
    private JTable factsTable = new JTable(factsTableModel);

    public StatusFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Status", false);
        sipModel.getStoreModel().addListener(new DataSetStoreModel.Listener() {
            @Override
            public void storeSet(FileStore.DataSetStore store) {
                showStatus(store.getState());
            }

            @Override
            public void storeStateChanged(FileStore.DataSetStore store, FileStore.StoreState storeState) {
                showStatus(storeState);
            }
        });
        statusLabel.setFont(new Font("Sans", Font.BOLD, 24));
        sipModel.getDataSetFacts().addListener(factsTableModel);
        setDefaultSize(400, 600);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createStatusPanel(), BorderLayout.NORTH);
        content.add(createFactsPanel(), BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    private void showStatus(final FileStore.StoreState storeState) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                statusLabel.setText(String.format("<html><b>Status:</b><i>%s</i>", storeStateDescription(storeState)));
            }
        });
    }

    private String storeStateDescription(FileStore.StoreState storeState) {
        switch (storeState) {
            case EMPTY:
                return "has no source yet";
            case IMPORTED_FRESH:
                return "";
            case IMPORTED_PENDING_ANALYZE:
                return "imported - needs analysis";
            case IMPORTED_PENDING_CONVERT:
                return "can now convert to standard format";
            case SOURCED_PENDING_ANALYZE:
                return "source present - needs analysis";
            case SOURCED_UNMAPPED:
                return "analysis complete - mapping possible";
            case MAPPED_UNVALIDATED:
                return "mapping exists - not yet validated";
            case READY_FOR_UPLOAD:
                return "ready for upload!";
            default:
                throw new IllegalArgumentException("Unknown store state: "+storeState);
        }
    }

    private JPanel createStatusPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Status"));
        p.add(statusLabel);
        return p;
    }

    private JPanel createFactsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Data Set Facts"));
        p.add(factsTable.getTableHeader(), BorderLayout.NORTH);
        p.add(factsTable, BorderLayout.CENTER);
        return p;
    }

    private class FactsTableModel extends AbstractTableModel implements FactModel.Listener {

        private List<Map.Entry<String, String>> entryList = new ArrayList<Map.Entry<String, String>>();

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Fact Name" : "Value";
        }

        @Override
        public int getRowCount() {
            return entryList.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int row, int col) {
            return col == 0 ? entryList.get(row).getKey() : entryList.get(row).getValue();
        }

        @Override
        public void factUpdated(String name, String value) {
            // doesn't happen
        }

        @Override
        public void allFactsUpdated(Map<String, String> map) {
            if (!entryList.isEmpty()) {
                int oldRows = getRowCount();
                entryList.clear();
                fireTableRowsDeleted(0, oldRows);
            }
            entryList.addAll(map.entrySet());
            if (!entryList.isEmpty()) {
                fireTableRowsInserted(0, getRowCount());
            }
        }
    }
}
