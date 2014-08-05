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
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static eu.delving.sip.base.KeystrokeHelper.DOWN;
import static eu.delving.sip.base.KeystrokeHelper.SPACE;
import static eu.delving.sip.base.KeystrokeHelper.UP;
import static eu.delving.sip.base.KeystrokeHelper.addKeyboardAction;
import static eu.delving.sip.base.KeystrokeHelper.attachAccelerator;
import static eu.delving.sip.base.NetworkClient.*;
import static eu.delving.sip.base.SwingHelper.ICON_DOWNLOAD;
import static eu.delving.sip.base.SwingHelper.ICON_HUH;
import static eu.delving.sip.base.SwingHelper.ICON_OWNED;
import static eu.delving.sip.base.SwingHelper.ICON_UNAVAILABLE;

/**
 * Show the datasets both local and on the server, so all info about their status is unambiguous.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetHubFrame extends FrameBase {
    private NetworkClient networkClient;
    private HubDataSetTableModel hubTableModel = new HubDataSetTableModel();
    private JTable hubTable;
    private EditAction editAction = new EditAction();
    private DownloadAction downloadAction = new DownloadAction();
    private ReleaseAction releaseAction = new ReleaseAction();
    private DataSetHubFrame.RefreshAction refreshAction = new RefreshAction();
    private JTextField patternField = new JTextField(6);

    public DataSetHubFrame(final SipModel sipModel, NetworkClient networkClient) {
        super(Which.DATA_SET, sipModel, "Data Sets");
        this.networkClient = networkClient;
        this.hubTable = new JTable(hubTableModel, hubTableModel.getColumnModel());
        this.hubTable.setFont(this.hubTable.getFont().deriveFont(Font.PLAIN, 10));
        this.hubTable.setRowHeight(25);
        this.hubTable.setIntercellSpacing(new Dimension(12, 4));
        this.hubTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.hubTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                downloadAction.checkEnabled();
                editAction.checkEnabled();
                releaseAction.checkEnabled();
            }
        });
        this.hubTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && editAction.isEnabled()) {
                    if (hubTable.getSelectedRow() != hubTable.rowAtPoint(e.getPoint())) return;
                    editAction.actionPerformed(null);
                }
            }
        });
        hubTableModel.setStateCheckDelay(500);
        editAction.checkEnabled();
        downloadAction.checkEnabled();
        releaseAction.checkEnabled();
    }

    @Override
    protected void onOpen(boolean opened) {
        if (opened) Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                hubTable.requestFocus();
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Hub Data Sets", hubTable), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    private JPanel createSouth() {
        JPanel p = new JPanel(new GridLayout(1, 0, 10, 10));
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(refreshAction));
        p.add(createFilter());
        p.add(button(editAction));
        addKeyboardAction(editAction, SPACE, (JComponent) getContentPane());
        addKeyboardAction(new UpDownAction(false), UP, (JComponent) getContentPane());
        addKeyboardAction(new UpDownAction(true), DOWN, (JComponent) getContentPane());
        p.add(button(downloadAction));
        p.add(button(releaseAction));
        return p;
    }

    private JButton button(Action action) {
        JButton button = new JButton(action);
        button.setHorizontalAlignment(JButton.LEFT);
        return button;
    }

    private JPanel createFilter() {
        JLabel label = new JLabel("Filter:", JLabel.RIGHT);
        label.setLabelFor(patternField);
        JPanel p = new JPanel(new BorderLayout());
        p.add(label, BorderLayout.WEST);
        attachAccelerator(new UpDownAction(true), patternField);
        attachAccelerator(new UpDownAction(false), patternField);
        p.add(patternField, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                hubTableModel.setHubEntries(null);
                refreshAction.actionPerformed(null);
            }
        });
    }

    private class UpDownAction extends AbstractAction {
        private boolean down = false;

        private UpDownAction(boolean down) {
            super(down ? "Down" : "Up");
            this.down = down;
            putValue(Action.ACCELERATOR_KEY, down ? DOWN : UP);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ListSelectionModel selection = hubTable.getSelectionModel();
            if (hubTableModel.getRowCount() > 0 && hubTable.getSelectionModel().isSelectionEmpty()) {
                int row = down ? 0 : hubTableModel.getRowCount() - 1;
                selection.setSelectionInterval(row, row);
                hubTable.requestFocus();
                Rectangle cellRect = hubTable.getCellRect(row, 0, false);
                hubTable.scrollRectToVisible(cellRect);
            }
        }
    }

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh list from the culture hub");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            // todo: change the icon to a waiting one?
            networkClient.fetchHubDatasetList(new HubListListener() {
                @Override
                public void listReceived(List<NetworkClient.DataSetEntry> entries) {
                    hubTableModel.setHubEntries(entries);
                    setEnabled(true);
                }

                @Override
                public void failed(Exception e) {
                    sipModel.getFeedback().alert("Unable to fetch hub data set list", e);
                    hubTableModel.setHubEntries(null);
                    patternField.setText(null);
                    setEnabled(true);
                }
            });

//            todo: Just a test
            networkClient.fetchNarthexSipList(
                    new NarthexListListener() {
                        @Override
                        public void listReceived(List<NetworkClient.Sip> entries) {
                            for (Sip sip : entries) {
                                System.out.println("Narthex: " + sip.file.trim());
                            }
                        }

                        @Override
                        public void failed(Exception e) {
                            sipModel.getFeedback().alert("Unable to fetch data set list", e);
                        }
                    },
                    sipModel.getPreferences().get(Storage.NARTHEX_URL, ""),
                    sipModel.getPreferences().get(Storage.NARTHEX_EMAIL, ""),
                    sipModel.getPreferences().get(Storage.NARTHEX_API_KEY, "")
            );
        }
    }

    private class EditAction extends AbstractAction {

        private EditAction() {
            super("Select this data set for editing");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
        }

        public void checkEnabled() {
            HubRow hubRow = getSelectedRow();
            this.setEnabled(hubRow != null && hubRow.getState().selectable);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            HubRow hubRow = getSelectedRow();
            if (hubRow == null) return;
            List<SchemaVersion> schemaVersions = hubRow.getSchemaVersions();
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
            sipModel.setDataSetPrefix(hubRow.dataSet, prefix, new Swing() {
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

    private class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download from culture hub");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_DOWNLOAD);
        }

        public void checkEnabled() {
            HubRow hubRow = getSelectedRow();
            this.setEnabled(!hubTableModel.needsFetch && hubRow != null && hubRow.getState().downloadable);
        }

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            HubRow hubRow = getSelectedRow();
            if (hubRow == null) return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(hubRow.getSpec(), hubRow.getOrganization());
                networkClient.downloadHubDataset(dataSet, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                        refreshAction.actionPerformed(actionEvent);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + hubRow.getSpec(), e);
            }
        }
    }

    public class ReleaseAction extends AbstractAction {

        public ReleaseAction() {
            super("Release ownership of this data set");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EMPTY);
        }

        public void checkEnabled() {
            HubRow hubRow = getSelectedRow();
            this.setEnabled(!hubTableModel.needsFetch && hubRow != null && hubRow.getState() == State.OWNED_BY_YOU);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            HubRow hubRow = getSelectedRow();
            if (hubRow == null) return;
            boolean unlock = sipModel.getFeedback().confirm(
                    "Release",
                    String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                                    "this data set %s, and unlock it so that someone else can access it?",
                            hubRow.getSpec()
                    )
            );
            if (unlock) unlockDataSet(hubRow.getDataSet());
        }

        private void unlockDataSet(final DataSet dataSet) {
            networkClient.unlockHubDataset(dataSet, new UnlockListener() {
                @Override
                public void unlockComplete(boolean successful) {
                    if (successful) {
                        try {
                            sipModel.exec(new Swing() {
                                @Override
                                public void run() {
                                    sipModel.seekReset(); // release the file handle
                                    refreshAction.actionPerformed(null); // no need for an action event
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

    private HubRow getSelectedRow() {
        int rowIndex = hubTable.getSelectedRow();
        return rowIndex < 0 ? null : hubTableModel.getRow(rowIndex);
    }

    private class HubDataSetTableModel extends AbstractTableModel {
        private DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
        private List<HubRow> hubRows = new ArrayList<HubRow>();
        private Map<Integer, Integer> index;
        private boolean needsFetch;
        private String pattern = "";

        private HubDataSetTableModel() {
            createSpecColumn();
            createNameColumn();
            createStateColumn();
            createSchemaVersionsColumn();
            createRecordCountColumn();
            createOwnedColumn();
        }

        public TableColumnModel getColumnModel() {
            return columnModel;
        }

        public void setStateCheckDelay(int delay) { // call only once
            Timer timer = new Timer(delay, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!patternField.getText().equalsIgnoreCase(pattern)) {
                        pattern = patternField.getText().toLowerCase();
                        setPattern();
                    }
                    int row = 0;
                    for (HubRow entry : hubRows) {
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
                for (HubRow hubRow : hubRows) {
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
            needsFetch = list == null;
            List<HubRow> freshHubRows = new ArrayList<HubRow>();
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            if (list != null) {
                for (DataSetEntry incoming : list) {
                    DataSet dataSet = dataSets.get(incoming.getDirectoryName());
                    freshHubRows.add(new HubRow(incoming, dataSet));
                    if (dataSet != null) dataSets.remove(incoming.getDirectoryName()); // remove used ones
                }
            }
            for (DataSet dataSet : dataSets.values()) { // remaining ones
                freshHubRows.add(new HubRow(null, dataSet));
            }
            Collections.sort(freshHubRows);
            hubRows = freshHubRows;
            fireTableStructureChanged();
        }

        public HubRow getRow(int rowIndex) {
            if (index != null) {
                Integer foundRow = index.get(rowIndex);
                if (foundRow != null) rowIndex = foundRow;
            }
            return hubRows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            if (index != null) {
                return index.size();
            }
            else {
                return hubRows.size();
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

        private void createSpecColumn() {
            TableColumn tc = addColumn("Spec", "this is spec");
            tc.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    String spec = (String) value;
                    DataSet dataSet = sipModel.getDataSetModel().getDataSet();
                    if (dataSet != null && dataSet.getSpec().equals(spec))
                        label.setFont(label.getFont().deriveFont(Font.BOLD, 12));
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
                    State state = hubTableModel.needsFetch ? State.NEEDS_FETCH : (State) value;
                    label.setIcon(state.icon);
                    label.setText(state.string);
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
            addColumn("Schemas", "seve_ralsc, schm_versions");
        }

        private void createNameColumn() {
            addColumn("Name", "A big dataset name");
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
            HubRow hubRow = getRow(rowIndex);
            State state = hubRow.getState();
            DataSetEntry entry = hubRow.getDataSetEntry();
            switch (columnIndex) {
                case 0:
                    return hubRow.getSpec();
                case 1:
                    return hubRow.getDataSetName();
                case 2:
                    return state;
                case 3:
                    return schemaVersionsString(hubRow);
                case 4:
                    int recordCount = 0;
                    if (entry != null) {
                        recordCount = entry.recordCount;
                    }
                    else {
                        String recordCountString = hubRow.dataSet.getHints().get("recordCount");
                        if (recordCountString != null) {
                            recordCount = Integer.parseInt(recordCountString);
                        }
                    }
                    return String.format(" %11d ", recordCount);
                case 5:
                    return entry == null || entry.lockedBy == null ? "" : entry.lockedBy;
            }
            throw new RuntimeException();
        }

        private String schemaVersionsString(HubRow hubRow) {
            if (hubRow.getSchemaVersions() == null) return "?";
            StringBuilder out = new StringBuilder();
            Iterator<SchemaVersion> walk = hubRow.getSchemaVersions().iterator();
            while (walk.hasNext()) {
                SchemaVersion schemaVersion = walk.next();
                out.append(schemaVersion.toString());
                out.append(" (").append(hubRow.getDataSetState(schemaVersion)).append(")");
                if (walk.hasNext()) out.append(", ");
            }
            return out.toString();
        }
    }

    private class HubRow implements Comparable<HubRow> {
        private DataSetEntry dataSetEntry;
        private DataSet dataSet;
        private State previousState;

        private HubRow(DataSetEntry dataSetEntry, DataSet dataSet) {
            this.dataSetEntry = dataSetEntry;
            this.dataSet = dataSet;
            this.previousState = getState();
        }

        public String getOrganization() {
            return dataSetEntry != null ? dataSetEntry.orgId : dataSet.getOrganization();
        }

        public String getSpec() {
            return dataSetEntry != null ? dataSetEntry.spec : dataSet.getSpec();
        }

        public DataSetEntry getDataSetEntry() {
            return dataSetEntry;
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        public String getDataSetName() {
            String name;
            if (dataSet != null) {
                name = dataSet.getDataSetFacts().get("name");
            }
            else {
                name = dataSetEntry.name;
            }
            if (name == null) name = "";
            return name;
        }

        public List<SchemaVersion> getSchemaVersions() {
            if (dataSet != null) {
                return dataSet.getSchemaVersions();
            }
            else if (dataSetEntry.schemaVersions != null) {
                List<SchemaVersion> list = new ArrayList<SchemaVersion>();
                for (SchemaVersionTag schemaVersionTag : dataSetEntry.schemaVersions) {
                    list.add(new SchemaVersion(schemaVersionTag.prefix, schemaVersionTag.version));
                }
                return list;
            }
            else {
                return null;
            }
        }

        public State getState() {
            if (isBusy()) {
                return State.BUSY;
            }
            else if (dataSetEntry != null && dataSet != null) {
                if (dataSetEntry.lockedBy == null) {
                    return State.ORPHAN_ARCHIVE;
                }
                else if (isLockedByUser()) {
                    return State.OWNED_BY_YOU;
                }
                else { // locked by somebody else
                    return State.ORPHAN_TAKEN;
                }
            }
            else if (dataSetEntry != null) { // dataSet is null
                if (dataSetEntry.lockedBy == null) {
                    return State.AVAILABLE;
                }
                else if (isLockedByUser()) {
                    return State.ORPHAN_UPDATE;
                }
                else { // locked by somebody else
                    return State.UNAVAILABLE;
                }
            }
            else { // dataSetEntry is null
                return State.ORPHAN_LONELY;
            }
        }

        public String getDataSetState(SchemaVersion schemaVersion) {
            if (dataSet == null) return DataSetState.NO_DATA.toString();
            return dataSet.getState(schemaVersion.getPrefix()).toString();
        }

        @Override
        public int compareTo(HubRow hubRow) {
            return getSpec().compareTo(hubRow.getSpec());
        }

        public boolean hasStateChanged() {
            State newState = getState();
            if (newState == previousState) return false;
            previousState = newState;
            return true;
        }

        public String toString() {
            return String.format("DataSet(%s)", getSpec());
        }

        private boolean isBusy() {
            return sipModel.getWorkModel().isDataSetBusy(getSpec());
        }

        private boolean isLockedByUser() {
            boolean absent = dataSetEntry == null || dataSetEntry.lockedBy == null;
            return !absent && sipModel.getStorage().getUsername().equals(dataSetEntry.lockedBy.username);
        }
    }

    private enum State {
        OWNED_BY_YOU(true, true, "yours", ICON_OWNED),
        AVAILABLE(false, true, "free", ICON_DOWNLOAD),
        UNAVAILABLE(false, true, "taken", ICON_UNAVAILABLE),
        BUSY(false, false, "busy", SwingHelper.ICON_BUSY),
        ORPHAN_TAKEN(true, true, "taken/local", ICON_HUH),
        ORPHAN_LONELY(true, false, "only local", ICON_HUH),
        ORPHAN_UPDATE(false, true, "yours notlocal", ICON_HUH),
        ORPHAN_ARCHIVE(true, true, "free/local", ICON_HUH),
        NEEDS_FETCH(true, false, "fetch", ICON_OWNED);

        private final String string;
        private final Icon icon;
        private final boolean selectable;
        private final boolean downloadable;

        private State(boolean selectable, boolean downloadable, String string, Icon icon) {
            this.selectable = selectable;
            this.downloadable = downloadable;
            this.string = string;
            this.icon = icon;
        }

        public String toString() {
            return String.format("%s: %s", super.toString(), string);
        }
    }
}
