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
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static eu.delving.sip.base.KeystrokeHelper.SPACE;
import static eu.delving.sip.base.KeystrokeHelper.addKeyboardAction;
import static eu.delving.sip.base.SwingHelper.*;

/**
 * Show the datasets both local and on the server, so all info about their status is unambiguous.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DataSetHubFrame extends FrameBase {
    private CultureHubClient cultureHubClient;
    private DataSetTableModel tableModel = new DataSetTableModel();
    private JTable dataSetTable;
    private EditAction editAction = new EditAction();
    private DownloadAction downloadAction = new DownloadAction();
    private ReleaseAction releaseAction = new ReleaseAction();
    private DataSetHubFrame.RefreshAction refreshAction = new RefreshAction();
    private JTextField patternField = new JTextField(6);

    public DataSetHubFrame(final SipModel sipModel, CultureHubClient cultureHubClient) {
        super(Which.DATA_SET, sipModel, "Data Sets");
        this.cultureHubClient = cultureHubClient;
        this.dataSetTable = new JTable(tableModel, tableModel.getColumnModel());
        this.dataSetTable.setFont(this.dataSetTable.getFont().deriveFont(Font.PLAIN, 14));
        this.dataSetTable.setRowHeight(25);
        this.dataSetTable.setIntercellSpacing(new Dimension(12, 4));
        this.dataSetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.dataSetTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                downloadAction.checkEnabled();
                editAction.checkEnabled();
                releaseAction.checkEnabled();
            }
        });
        this.dataSetTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && editAction.isEnabled()) {
                    if (dataSetTable.getSelectedRow() != dataSetTable.rowAtPoint(e.getPoint())) return;
                    editAction.actionPerformed(null);
                }
            }
        });
        tableModel.setStateCheckDelay(500);
        editAction.checkEnabled();
        downloadAction.checkEnabled();
        releaseAction.checkEnabled();
    }

    @Override
    protected void onOpen(boolean opened) {
        if (opened) Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                dataSetTable.requestFocus();
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Data Sets", dataSetTable), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    private JPanel createSouth() {
        JPanel p = new JPanel(new GridLayout(1, 0, 10, 10));
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(refreshAction));
        p.add(createFilter());
        p.add(button(editAction));
        addKeyboardAction(editAction, SPACE, (JComponent) getContentPane());
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
        p.add(patternField, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        if (SwingHelper.isDevelopmentMode()) {
            tableModel.setHubEntries(null);
        }
        else {
            refreshAction.actionPerformed(null);
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
            cultureHubClient.fetchDataSetList(new CultureHubClient.ListReceiveListener() {
                @Override
                public void listReceived(List<CultureHubClient.DataSetEntry> entries) {
                    tableModel.setHubEntries(entries);
                    patternField.setText(null);
                    setEnabled(true);
                }

                @Override
                public void failed(Exception e) {
                    sipModel.getFeedback().alert("Unable to fetch data set list", e);
                    tableModel.setHubEntries(null);
                    patternField.setText(null);
                    setEnabled(true);
                }
            });
        }
    }

    private class EditAction extends AbstractAction {

        private EditAction() {
            super("Select this data set for editing");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
        }

        public void checkEnabled() {
            Row row = getSelectedRow();
            this.setEnabled(row != null && row.getState().selectable);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Row row = getSelectedRow();
            if (row == null) return;
            List<SchemaVersion> schemaVersions = row.getSchemaVersions();
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
            sipModel.setDataSetPrefix(row.dataSet, prefix, new Swing() {
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
            Row row = getSelectedRow();
            this.setEnabled(!tableModel.needsFetch && row != null && row.getState().downloadable);
        }

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            Row row = getSelectedRow();
            if (row == null) return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(row.getSpec(), row.getOrganization());
                cultureHubClient.downloadDataSet(dataSet, new Swing() {
                    @Override
                    public void run() {
                        setEnabled(true);
                        refreshAction.actionPerformed(actionEvent);
                    }
                });
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + row.getSpec(), e);
            }
        }
    }

    public class ReleaseAction extends AbstractAction {

        public ReleaseAction() {
            super("Release ownership of this data set");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EMPTY);
        }

        public void checkEnabled() {
            Row row = getSelectedRow();
            this.setEnabled(!tableModel.needsFetch && row != null && row.getState() == State.OWNED_BY_YOU);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Row row = getSelectedRow();
            if (row == null) return;
            boolean unlock = sipModel.getFeedback().confirm(
                    "Release",
                    String.format("<html>Are you sure that you want to delete your local copy of<br>" +
                            "this data set %s, and unlock it so that someone else can access it?",
                            row.getSpec()
                    )
            );
            if (unlock) unlockDataSet(row.getDataSet());
        }

        private void unlockDataSet(final DataSet dataSet) {
            cultureHubClient.unlockDataSet(dataSet, new CultureHubClient.UnlockListener() {
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

    private Row getSelectedRow() {
        int rowIndex = dataSetTable.getSelectedRow();
        return rowIndex < 0 ? null : tableModel.getRow(rowIndex);
    }

    private class DataSetTableModel extends AbstractTableModel {
        private DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
        private List<Row> rows = new ArrayList<Row>();
        private Map<Integer, Integer> index;
        private boolean needsFetch;
        private String pattern = "";

        private DataSetTableModel() {
            createColumnModel();
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
                    for (Row entry : rows) {
                        if (entry.hasStateChanged()) fireTableRowsUpdated(row, row);
                        row++;
                    }
                    ListSelectionModel selection = dataSetTable.getSelectionModel();
                    if (tableModel.getRowCount() > 0 && selection.isSelectionEmpty()) {
                        selection.setSelectionInterval(0, 0);
                        dataSetTable.requestFocus();
                        openFrame();
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
                for (Row row : rows) {
                    if (row.getSpec().contains(pattern)) {
                        map.put(virtual, actual);
                        virtual++;
                    }
                    actual++;
                }
                index = map;
                fireTableStructureChanged();
            }
        }

        public void setHubEntries(List<CultureHubClient.DataSetEntry> list) {
            needsFetch = list == null;
            List<Row> freshRows = new ArrayList<Row>();
            Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
            if (list != null) {
                for (CultureHubClient.DataSetEntry incoming : list) {
                    DataSet dataSet = dataSets.get(incoming.getDirectoryName());
                    freshRows.add(new Row(incoming, dataSet));
                    if (dataSet != null) dataSets.remove(incoming.getDirectoryName()); // remove used ones
                }
            }
            for (DataSet dataSet : dataSets.values()) { // remaining ones
                freshRows.add(new Row(null, dataSet));
            }
            Collections.sort(freshRows);
            rows = freshRows;
            fireTableStructureChanged();
        }

        public Row getRow(int rowIndex) {
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

        private void createSpecColumn() {
            TableColumn tc = addColumn("Spec", "how long can a spec name actually be?");
            tc.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    String spec = (String) value;
                    DataSet dataSet = sipModel.getDataSetModel().getDataSet();
                    if (dataSet != null && dataSet.getSpec().equals(spec)) label.setFont(label.getFont().deriveFont(Font.BOLD, 18));
                    return label;
                }
            });
        }

        private void createStateColumn() {
            TableColumn tc = addColumn("State", State.NEEDS_FETCH.string + "extra");
            tc.setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    State state = tableModel.needsFetch ? State.NEEDS_FETCH : (State) value;
                    label.setIcon(state.icon);
                    label.setText(state.string);
                    return label;
                }
            });
            tc.setHeaderValue("State");
            tc.setMinWidth(100);
        }

        private void createRecordCountColumn() {
            TableColumn tc = addColumn("Record Count", "   manyrecords   ");
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
            addColumn("Owner", "Somebody's long name <andtheirbig@email.address>");
        }

        private void createSchemaVersionsColumn() {
            addColumn("Schemas", "seve_ralsc, schm_versions");
        }

        private void createNameColumn() {
            addColumn("Name", "This is a very long dataset name");
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

        private void createColumnModel() {
            createSpecColumn();
            createNameColumn();
            createStateColumn();
            createSchemaVersionsColumn();
            createRecordCountColumn();
            createOwnedColumn();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = getRow(rowIndex);
            State state = row.getState();
            CultureHubClient.DataSetEntry entry = row.getDataSetEntry();
            switch (columnIndex) {
                case 0:
                    return row.getSpec();
                case 1:
                    return row.getDataSetName();
                case 2:
                    return state;
                case 3:
                    return toString(row.getSchemaVersions());
                case 4:
                    return entry == null ? "" : String.format("   %11d   ", entry.recordCount);
                case 5:
                    return entry == null || entry.lockedBy == null ? "" : entry.lockedBy;
            }
            throw new RuntimeException();
        }

        private String toString(List<SchemaVersion> schemaVersions) {
            if (schemaVersions == null) return "?";
            StringBuilder out = new StringBuilder();
            Iterator<SchemaVersion> walk = schemaVersions.iterator();
            while (walk.hasNext()) {
                SchemaVersion schemaVersion = walk.next();
                out.append(schemaVersion.toString());
                if (walk.hasNext()) out.append(", ");
            }
            return out.toString();
        }
    }

    private class Row implements Comparable<Row> {
        private CultureHubClient.DataSetEntry dataSetEntry;
        private DataSet dataSet;
        private State previousState;

        private Row(CultureHubClient.DataSetEntry dataSetEntry, DataSet dataSet) {
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

        public CultureHubClient.DataSetEntry getDataSetEntry() {
            return dataSetEntry;
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        public String getDataSetName() {
            if (dataSet == null) return "";
            String name = dataSet.getDataSetFacts().get("name");
            if (name == null) name = "";
            return name;
        }

        public List<SchemaVersion> getSchemaVersions() {
            return dataSet != null ? dataSet.getSchemaVersions() : null;
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

        @Override
        public int compareTo(Row row) {
            return getSpec().compareTo(row.getSpec());
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
        OWNED_BY_YOU(true, false, "yours", ICON_OWNED),
        AVAILABLE(false, true, "downloadable", ICON_DOWNLOAD),
        UNAVAILABLE(false, false, "taken", ICON_UNAVAILABLE),
        BUSY(false, false, "busy", SwingHelper.ICON_BUSY),
        ORPHAN_TAKEN(true, false, "taken but present locally", ICON_HUH),
        ORPHAN_LONELY(true, false, "only present locally", ICON_HUH),
        ORPHAN_UPDATE(false, true, "yours but absent locally)", ICON_HUH),
        ORPHAN_ARCHIVE(true, true, "free but present locally", ICON_HUH),
        NEEDS_FETCH(true, false, "refresh to fetch culture hub info", ICON_OWNED);

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
