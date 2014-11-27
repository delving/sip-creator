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
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;

import static eu.delving.sip.base.SwingHelper.ICON_DOWNLOAD;
import static eu.delving.sip.base.SwingHelper.ICON_OWNED;
import static eu.delving.sip.files.Storage.NARTHEX_API_KEY;
import static eu.delving.sip.files.Storage.NARTHEX_URL;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class DownloadTableModel extends AbstractTableModel {
    private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.dateTime();
    private final SipModel sipModel;
    private final NetworkClient networkClient;
    private DefaultTableColumnModel columnModel = new DefaultTableColumnModel();
    private List<DownloadDatasetRow> rows = new ArrayList<DownloadDatasetRow>();
    private Map<Integer, Integer> index;
    private boolean fetchNeeded;
    private JTextField patternField = new JTextField(6);
    private DownloadDatasetRow selectedRow = null;

    public DownloadTableModel(SipModel sipModel, NetworkClient networkClient) {
        this.sipModel = sipModel;
        this.networkClient = networkClient;
        createSpecNameColumn();
        addColumn("Generated", "2014-11-26T14:39:17");
        createDownloadableColumn();
        addColumn("Dataset State", "state string");
        addColumn("Download time", "2014-11-26T14:39:17");
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
            for (DownloadDatasetRow hubRow : rows) {
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

    public void setNarthexEntries(NetworkClient.SipZips sipZips) {
        fetchNeeded = sipZips == null;
        List<DownloadDatasetRow> freshRows = new ArrayList<DownloadDatasetRow>();
        Map<String, DataSet> dataSets = sipModel.getStorage().getDataSets();
        if (sipZips != null) {
            for (NetworkClient.SipEntry incoming : sipZips.available) {
                DataSet dataSet = dataSets.get(incoming.dataset);
                freshRows.add(new DownloadDatasetRow(sipModel, incoming, dataSet));
                if (dataSet != null) dataSets.remove(incoming.dataset); // remove used ones
            }
        }
        for (DataSet dataSet : dataSets.values()) { // remaining ones
            freshRows.add(new DownloadDatasetRow(sipModel, null, dataSet));
        }
        rows = freshRows;
        fireTableStructureChanged();
    }

    public DownloadDatasetRow getRow(int rowIndex) {
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
                String spec = (String) value;
                if (spec == null) {
                    label.setText("<html><b>?</b>");
                }
                else {
                    DataSet dataSet = sipModel.getDataSetModel().getDataSet();
                    boolean isCurrentDataset = dataSet != null && dataSet.getSpec().equals(spec);
                    label.setText(String.format(
                            "<html>%s<b>%s</b>",
                            isCurrentDataset ? ">>>" : "", spec
                    ));
                }
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
                    label.setText("Local");
                }
                return label;
            }
        });

    }

    private void createStateColumn() {
        TableColumn tc = addColumn("Dataset State", "state string");
//        tc.setCellRenderer(new DefaultTableCellRenderer() {
//            @Override
//            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
//                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//                label.setText(value.toString());
//                return label;
//            }
//        });
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
        DownloadDatasetRow row = getRow(rowIndex);
        switch (columnIndex) {
            case 0:
                return row.getSpec();
            case 1:
                return row.getGeneratedTime();
            case 2:
                return row.isDownloadable();
            case 3:
                return row.getDataSetState();
            case 4:
                return row.getDownloadTime();
        }
        throw new RuntimeException();
    }

    public final RefreshAction REFRESH_ACTION = new RefreshAction();

    private class RefreshAction extends AbstractAction {

        private RefreshAction() {
            super("Refresh list from Narthex");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_FETCH_LIST);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            Preferences preferences = sipModel.getPreferences();
            Map<String, String> fields = new TreeMap<String, String>();
            fields.put(NARTHEX_URL, preferences.get(NARTHEX_URL, ""));
            fields.put(NARTHEX_API_KEY, preferences.get(NARTHEX_API_KEY, ""));
            if (sipModel.getFeedback().getNarthexCredentials(fields)) {
                preferences.put(NARTHEX_URL, fields.get(NARTHEX_URL));
                preferences.put(NARTHEX_API_KEY, fields.get(NARTHEX_API_KEY));
                fetchList(fields.get(NARTHEX_URL), fields.get(NARTHEX_API_KEY));
            }
        }

        public void fetchList(String narthexUrl, String narthexApiKey) {
            networkClient.fetchNarthexSipList(
                    narthexUrl,
                    narthexApiKey,
                    new NetworkClient.NarthexListListener() {
                        @Override
                        public void listReceived(NetworkClient.SipZips sipZips) {
                            setNarthexEntries(sipZips);
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

    public void fetchList() {
        Preferences preferences = sipModel.getPreferences();
        REFRESH_ACTION.fetchList(preferences.get(NARTHEX_URL, ""), preferences.get(NARTHEX_API_KEY, ""));
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
                DataSet dataSet = sipModel.getStorage().createDataSet(selectedRow.getSpec());
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
            this.setEnabled(selectedRow != null && selectedRow.getDataSet() != null);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (selectedRow == null) return;
            sipModel.setDataSet(selectedRow.getDataSet(), new Swing() {
                @Override
                public void run() {
                    setEnabled(true);
                    sipModel.getViewSelector().selectView(AllFrames.View.QUICK_MAPPING);
                }
            });
        }
    }


}
