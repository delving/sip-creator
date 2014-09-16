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
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.delving.sip.base.SwingHelper.ICON_DOWNLOAD;
import static eu.delving.sip.base.SwingHelper.ICON_OWNED;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class NarthexDataSetTableModel extends AbstractTableModel {
    private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.dateTime();
    private final SipModel sipModel;
    private final NetworkClient networkClient;
    private DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
    private List<NarthexDataSetTableRow> rows = new ArrayList<NarthexDataSetTableRow>();
    private Map<Integer, Integer> index;
    private boolean fetchNeeded;
    private JTextField patternField = new JTextField(6);
    private NarthexDataSetTableRow selectedRow = null;

    public NarthexDataSetTableModel(SipModel sipModel, NetworkClient networkClient) {
        this.sipModel = sipModel;
        this.networkClient = networkClient;
        createSpecNameColumn();
        createDownloadableColumn();
        createSchemaVersionsColumn();
        createUploadedColumn();
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

    public void setPattern() {
        String pattern = "";
        if (pattern.isEmpty()) {
            if (index != null) {
                index = null;
                fireTableStructureChanged();
            }
        }
        else {
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int actual = 0, virtual = 0;
            for (NarthexDataSetTableRow hubRow : rows) {
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

    public void setNarthexEntries(List<NetworkClient.Sip> list) {
        fetchNeeded = list == null;
        List<NarthexDataSetTableRow> freshRows = new ArrayList<NarthexDataSetTableRow>();
        Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets(true);
        if (list != null) {
            for (NetworkClient.Sip incoming : list) {
                DataSet dataSet = dataSets.get(incoming.file);
                freshRows.add(new NarthexDataSetTableRow(sipModel, incoming, dataSet));
                if (dataSet != null) dataSets.remove(incoming.file); // remove used ones
            }
        }
        for (DataSet dataSet : dataSets.values()) { // remaining ones
            freshRows.add(new NarthexDataSetTableRow(sipModel, null, dataSet));
        }
        Collections.sort(freshRows);
        rows = freshRows;
        fireTableStructureChanged();
    }

    public NarthexDataSetTableRow getRow(int rowIndex) {
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

    private void createUploadedColumn() {
        TableColumn tc = addColumn("Uploaded", "long timestamp string is the widest");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String whenWho = (String) value;
                String[] part = whenWho.split(":::");
                String when = part[0];
                String who = part[1];
                label.setText(String.format(
                        "<html>%s<br/>%s",
                        when, who
                ));
                return label;
            }
        });
    }

    private void createDownloadableColumn() {
        TableColumn tc = addColumn("Downloadable", "boolean with icon");
        tc.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Boolean downloadable = (Boolean) value;
                if (downloadable) {
                    label.setIcon(ICON_DOWNLOAD);
                    label.setText("Downloadable");
                }
                else {
                    label.setIcon(ICON_OWNED);
                    label.setText("Yours");
                }
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
        NarthexDataSetTableRow row = getRow(rowIndex);
        switch (columnIndex) {
            case 0:
                return row.getDataSetName() + ":::" + row.getSpec();
            case 1:
                return row.isDownloadable();
            case 2:
                List<SchemaVersion> schemaVersions = row.getSchemaVersions();
                if (schemaVersions == null) return new SchemaVersion[0];
                SchemaVersion[] array = new SchemaVersion[schemaVersions.size()];
                return schemaVersions.toArray(array);
            case 3:
                return row.getUploadedOn() + ":::" + row.getUploadedBy();
        }
        throw new RuntimeException();
    }

    public final Action REFRESH_ACTION = new RefreshAction();

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh list from Narthex");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            if (sipModel.getFeedback().getNarthexCredentials()) {
                String narthexUrl = sipModel.getPreferences().get(Storage.NARTHEX_URL, "");
                String narthexApiKey = sipModel.getPreferences().get(Storage.NARTHEX_API_KEY, "");
                fetchList(narthexUrl, narthexApiKey);
            }
        }

        private void fetchList(String narthexUrl, String narthexApiKey) {
            networkClient.fetchNarthexSipList(
                    narthexUrl,
                    narthexApiKey,
                    new NetworkClient.NarthexListListener() {
                        @Override
                        public void listReceived(List<NetworkClient.Sip> entries) {
                            setNarthexEntries(entries);
                            setEnabled(true);
                        }

                        @Override
                        public void failed(Exception e) {
                            sipModel.getFeedback().alert("Unable to fetch Narthex sip-zip list", e);
                            setNarthexEntries(null);
                            getPatternField().setText(null);
                            setEnabled(true);
                        }
                    }
            );
        }
    }

    public final DownloadAction DOWNLOAD_ACTION = new DownloadAction();

    public class DownloadAction extends AbstractAction {

        private DownloadAction() {
            super("Download from Narthex");
            putValue(Action.SMALL_ICON, ICON_DOWNLOAD);
        }

        public void checkEnabled() {
            this.setEnabled(!isFetchNeeded() && selectedRow != null && selectedRow.isDownloadable());
        }

        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            if (selectedRow == null) return;
            setEnabled(false);
            try {
                DataSet dataSet = sipModel.getStorage().createDataSet(true, selectedRow.getSpec(), selectedRow.getOrganization());
                networkClient.downloadNarthexDataset(
                        selectedRow.getFileName(),
                        dataSet,
                        sipModel.getPreferences().get(Storage.NARTHEX_URL, ""),
                        sipModel.getPreferences().get(Storage.NARTHEX_API_KEY, ""),
                        new Swing() {
                            @Override
                            public void run() {
                                setEnabled(true);
                                REFRESH_ACTION.actionPerformed(actionEvent);
                            }
                        }
                );
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create data set called " + selectedRow.getSpec(), e);
            }
        }
    }


    public final EditAction EDIT_ACTION = new EditAction();

    public class EditAction extends AbstractAction {

        private EditAction() {
            super("Select this data set for editing");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_EDIT);
        }

        public void checkEnabled() {
            this.setEnabled(selectedRow != null && !selectedRow.isDownloadable());
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


}
