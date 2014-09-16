/*
 * Copyright 2012 Delving BV
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

import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class HubDataSetTableModel extends AbstractTableModel {
    private final SipModel sipModel;
    private final NetworkClient networkClient;
    private DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
    private List<HubDataSetTableRow> rows = new ArrayList<HubDataSetTableRow>();
    private Map<Integer, Integer> index;
    private boolean fetchNeeded;
    private String pattern = "";
    private JTextField patternField = new JTextField(6);
    private HubDataSetTableRow selectedRow = null;

    public HubDataSetTableModel(SipModel sipModel, NetworkClient networkClient) {
        this.sipModel = sipModel;
        this.networkClient = networkClient;
        setStateCheckDelay(500);
        createSpecNameColumn();
        createStateColumn();
        createSchemaVersionsColumn();
        createRecordCountColumn();
        createOwnedColumn();
    }

    public ListSelectionListener getSelectionListener() {
        return new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                ListSelectionModel selectionModel = (ListSelectionModel) e.getSource();
                selectedRow = null;
                for (int index = e.getFirstIndex(); index <= e.getLastIndex(); index++) {
                    if (selectionModel.isSelectedIndex(index)) {
                        selectedRow = rows.get(index);
                    }
                }
                checkEnabled();
            }
        };
    }

    public void checkEnabled() {
        DOWNLOAD_ACTION.checkEnabled();
        EDIT_ACTION.checkEnabled();
        RELEASE_ACTION.checkEnabled();
    }

    public JTextField getPatternField() {
        return patternField;
    }

    public boolean isFetchNeeded() {
        return fetchNeeded;
    }

    public TableColumnModel getColumnModel() {
        return columnModel;
    }

    private void setStateCheckDelay(int delay) { // call only once
        Timer timer = new Timer(delay, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!patternField.getText().equalsIgnoreCase(pattern)) {
                    pattern = patternField.getText().toLowerCase();
                    setPattern();
                }
                int row = 0;
                for (HubDataSetTableRow entry : rows) {
                    if (entry.hasStateChanged()) fireTableRowsUpdated(row, row);
                    row++;
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public void setPattern() {
        if (pattern.isEmpty()) {
            if (index != null) {
                index = null;
                fireTableStructureChanged();
            }
        }
        else {
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int actual = 0, virtual = 0;
            for (HubDataSetTableRow hubRow : rows) {
                if (hubRow.getSpec().contains(pattern)) {
                    map.put(virtual, actual);
                    virtual++;
                }
                actual++;
            }
            index = map;
            fireTableStructureChanged();
        }
    }

    public void setHubEntries(List<NetworkClient.DataSetEntry> list) {
        fetchNeeded = list == null;
        List<HubDataSetTableRow> freshHubRows = new ArrayList<HubDataSetTableRow>();
        Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets(false);
        if (list != null) {
            for (NetworkClient.DataSetEntry incoming : list) {
                DataSet dataSet = dataSets.get(incoming.getDirectoryName());
                freshHubRows.add(new HubDataSetTableRow(sipModel, incoming, dataSet));
                if (dataSet != null) dataSets.remove(incoming.getDirectoryName()); // remove used ones
            }
        }
        for (DataSet dataSet : dataSets.values()) { // remaining ones
            freshHubRows.add(new HubDataSetTableRow(sipModel, null, dataSet));
        }
        Collections.sort(freshHubRows);
        rows = freshHubRows;
        fireTableStructureChanged();
    }

    public HubDataSetTableRow getRow(int rowIndex) {
        if (index != null) {
            Integer foundRow = index.get(rowIndex);
            if (foundRow != null) rowIndex = foundRow;
        }
        return rows.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        if (index != null) {
            return index.size();
        }
        else {
            return rows.size();
        }
    }

    @Override
    public int getColumnCount() {
        return columnModel.getColumnCount();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    private void createSpecNameColumn() {
        TableColumn tc = addColumn("Name/Spec", "a rather wide spec can take up space");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String specName = (String) value;
                String[] part = specName.split(":::");
                String name = part[0];
                String spec = part[1];
                DataSet dataSet = sipModel.getDataSetModel().getDataSet();
                boolean isCurrentDataset = dataSet != null && dataSet.getSpec().equals(spec);
                label.setText(String.format(
                        "<html>%s<b>%s</b><br/>(%s)",
                        isCurrentDataset ? ">>>" : "", name, spec
                ));
                return label;
            }
        });
    }

    private void createStateColumn() {
        TableColumn tc = addColumn("State", "fetch state");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                HubDataSetState hubDataSetState = fetchNeeded ? HubDataSetState.NEEDS_FETCH : (HubDataSetState) value;
                label.setIcon(hubDataSetState.icon);
                label.setText(hubDataSetState.string);
                return label;
            }
        });
        tc.setHeaderValue("State");
//            tc.setMinWidth(100);
    }

    private void createRecordCountColumn() {
        TableColumn tc = addColumn("Record Count", "1000000");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(JLabel.RIGHT);
                return label;
            }
        });
    }

    private void createOwnedColumn() {
        addColumn("Owner", "name <their@email.address>");
    }

    private void createSchemaVersionsColumn() {
        TableColumn tc = addColumn("Schemas", "schemaname_1.0.0");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                SchemaVersion[] list = (SchemaVersion[]) value;
                StringBuilder out = new StringBuilder("<html>");
                for (SchemaVersion sv : list) {
                    out.append(sv).append("<br/>");
                }
                label.setText(out.toString());
                return label;
            }
        });
    }

    private TableColumn addColumn(String title, String prototype) {
        JLabel label = new JLabel(prototype);
        int width = label.getPreferredSize().width;
        TableColumn tableColumn = new TableColumn(columnModel.getColumnCount(), width);
        tableColumn.setHeaderValue(title);
        tableColumn.setMaxWidth(width * 2);
        tableColumn.setMinWidth(width * 3 / 4);
        columnModel.addColumn(tableColumn);
        return tableColumn;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        HubDataSetTableRow hubRow = getRow(rowIndex);
        HubDataSetState hubDataSetState = hubRow.getState();
        NetworkClient.DataSetEntry entry = hubRow.getDataSetEntry();
        switch (columnIndex) {
            case 0:
                return hubRow.getDataSetName() + ":::" + hubRow.getSpec();
            case 1:
                return hubDataSetState;
            case 2:
                List<SchemaVersion> schemaVersions = hubRow.getSchemaVersions();
                if (schemaVersions == null) return new SchemaVersion[0];
                SchemaVersion[] array = new SchemaVersion[schemaVersions.size()];
                return schemaVersions.toArray(array);
            case 3:
                int recordCount = 0;
                if (entry != null) {
                    recordCount = entry.recordCount;
                }
                else {
                    String recordCountString = hubRow.getDataSet().getHints().get("recordCount");
                    if (recordCountString != null) {
                        recordCount = Integer.parseInt(recordCountString);
                    }
                }
                return String.format(" %11d ", recordCount);
            case 4:
                return entry == null || entry.lockedBy == null ? "" : entry.lockedBy;
        }
        throw new RuntimeException();
    }

    //    private class UpDownAction extends AbstractAction {
//        private boolean down = false;
//
//        private UpDownAction(boolean down) {
//            super(down ? "Down" : "Up");
//            this.down = down;
//            putValue(Action.ACCELERATOR_KEY, down ? DOWN : UP);
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            HubDataSetTableRow selectedRow = hubTableSelection.getSelectedRow();
//            ListSelectionModel selection = hubTable.getSelectionModel();
//            if (getRowCount() > 0 && hubTable.getSelectionModel().isSelectionEmpty()) {
//                int row = down ? 0 : hubTableModel.getRowCount() - 1;
//                selection.setSelectionInterval(row, row);
//                hubTable.requestFocus();
//                Rectangle cellRect = hubTable.getCellRect(row, 0, false);
//                hubTable.scrollRectToVisible(cellRect);
//            }
//        }
//    }

    public final Action REFRESH_ACTION = new RefreshAction();

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh list from the CultureHub");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            // todo: change the icon to a waiting one?
            networkClient.fetchHubDatasetList(new NetworkClient.HubListListener() {
                @Override
                public void listReceived(List<NetworkClient.DataSetEntry> entries) {
                    setHubEntries(entries);
                    setEnabled(true);
                }

                @Override
                public void failed(Exception e) {
                    sipModel.getFeedback().alert("Unable to fetch hub data set list", e);
                    setHubEntries(null);
                    getPatternField().setText(null);
                    setEnabled(true);
                }
            });
        }
    }

    public final EditAction EDIT_ACTION = new EditAction();

    public class EditAction extends AbstractAction {

        private EditAction() {
            super("Select this data set for editing");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
        }

        public void checkEnabled() {
            this.setEnabled(selectedRow != null && selectedRow.getState().selectable);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedRow == null) return;
            List<SchemaVersion> schemaVersions = selectedRow.getSchemaVersions();
            if (schemaVersions == null || schemaVersions.isEmpty()) return;
            setEnabled(false);
            String prefix;
            if (schemaVersions.size() == 1) {
                prefix = schemaVersions.get(0).getPrefix();
            }
            else {
                prefix = askForPrefix(schemaVersions);
                if (prefix == null) return;
            }
            sipModel.setDataSetPrefix(selectedRow.getDataSet(), prefix, new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                    sipModel.getViewSelector().selectView(AllFrames.View.QUICK_MAPPING);
                }
            });
        }

        private String askForPrefix(List<SchemaVersion> schemaVersions) {
            JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (SchemaVersion schemaVersion : schemaVersions) {
                JRadioButton b = new JRadioButton(schemaVersion.getPrefix() + " mapping");
                if (buttonGroup.getButtonCount() == 0) b.setSelected(true);
                b.setActionCommand(schemaVersion.getPrefix());
                buttonGroup.add(b);
                buttonPanel.add(b);
            }
            return sipModel.getFeedback().form("Choose Schema", buttonPanel) ? buttonGroup.getSelection().getActionCommand() : null;
        }
    }

    public final ReleaseAction RELEASE_ACTION = new ReleaseAction();

    public class ReleaseAction extends AbstractAction {

        public ReleaseAction() {
            super("Release ownership of this data set");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EMPTY);
        }

        public void checkEnabled() {
            this.setEnabled(!isFetchNeeded() && selectedRow != null && selectedRow.getState() == HubDataSetState.OWNED_BY_YOU);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedRow == null) return;
            boolean unlock = sipModel.getFeedback().confirm(
                    "Release",
                    String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                                    "this data set %s, and unlock it so that someone else can access it?",
                            selectedRow.getSpec()
                    )
            );
            if (unlock) unlockDataSet(selectedRow.getDataSet());
        }

        private void unlockDataSet(final DataSet dataSet) {
            networkClient.unlockHubDataset(dataSet, new NetworkClient.ReleaseListener() {
                @Override
                public void unlockComplete(boolean successful) {
                    if (successful) {
                        try {
                            sipModel.exec(new Swing() {
                                @Override
                                public void run() {
                                    sipModel.seekReset(); // release the file handle
                                    REFRESH_ACTION.actionPerformed(null); // no need for an action event
                                }
                            });
                            sipModel.getDataSetModel().clearDataSet();
                            dataSet.remove();
                        }
                        catch (StorageException e) {
                            sipModel.getFeedback().alert("Unable to remove data set", e);
                        }
                    }
                    else {
                        sipModel.getFeedback().alert("Unable to unlock the data set");
                    }
                }
            });
        }
    }

    public final DownloadAction DOWNLOAD_ACTION = new DownloadAction();

    public class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download from CultureHub");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_DOWNLOAD);
        }

        public void checkEnabled() {
            this.setEnabled(!isFetchNeeded() && selectedRow != null && selectedRow.getState().downloadable);
        }

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            if (selectedRow == null) return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(false, selectedRow.getSpec(), selectedRow.getOrganization());
                networkClient.downloadHubDataset("culture-hub-sip.zip", dataSet, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                        REFRESH_ACTION.actionPerformed(actionEvent);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + selectedRow.getSpec(), e);
            }
        }
    }


}
