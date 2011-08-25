/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.desktop.windows;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contains the following elements:
 *
 * <ul>
 * <li>List of data sets</li>
 * <li>Search component for datasets</li>
 * <li>Change log</li>
 * <ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 *         todo: Filter/search is not implemented yet
 */
public class DataSetWindow extends DesktopWindow {

    private static final Logger LOG = Logger.getRootLogger();
    private JTable dataSets;
    private JButton select = new JButton("Select");
    private JButton cancel = new JButton("Cancel");
    private DataSetModel<FileStore.DataSetStore> dataSetModel;

    public DataSetWindow(SipModel sipModel) {
        super(sipModel);
        setSize(new Dimension(600, 400));
        setLayout(new BorderLayout());
        buildLayout();
        addActions();
    }

    private SipModel.UpdateListener updateListener = new SipModel.UpdateListener() {

        @Override
        public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
            LOG.info("Updated data set store " + dataSetStore);
        }

        @Override
        public void updatedStatistics(FieldStatistics fieldStatistics) {
            LOG.info("Updated field statistics " + fieldStatistics);
        }

        @Override
        public void updatedRecordRoot(Path recordRoot, int recordCount) {
            LOG.info("Updated record root " + recordRoot + " " + recordCount);
        }

        @Override
        public void normalizationMessage(boolean complete, String message) {
            LOG.info("Normalization : " + complete + " " + message);
        }
    };

    private void addActions() {
        sipModel.addUpdateListener(updateListener);
        select.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        DataSetModel<FileStore.DataSetStore> model = dataSetModel;
                        FileStore.DataSetStore selectedItem = model.getSelectedItem(dataSets.getSelectedRow());
                        LOG.info(selectedItem.getSourceFile() + " exists? " + selectedItem.getSourceFile().exists());
                        int result = JOptionPane.showConfirmDialog(
                                DataSetWindow.this,
                                String.format("<html>You are switching to <b>%s</b>. Are you sure?<br/>Your current workspace will be saved.</html>",
                                        selectedItem.getSpec()),
                                "Change data set",
                                JOptionPane.YES_NO_OPTION
                        );
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                sipModel.setDataSetStore(selectedItem);
                                setVisible(false);
                                break;
                            default:
                                break;
                        }
                    }
                }
        );
    }

    private void buildLayout() {
        dataSetModel = new DataSetModel<FileStore.DataSetStore>(fetchDataSets());
        dataSets = new JTable(dataSetModel);
        dataSets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSets.setDefaultRenderer(Object.class, new ColorRenderer());
        JScrollPane pane = new JScrollPane(dataSets);
        pane.setPreferredSize(new Dimension(700, 400));
        add(pane, BorderLayout.CENTER);
        add(select, BorderLayout.SOUTH);
    }

    private class DataSetModel<T extends FileStore.DataSetStore> extends AbstractTableModel {

        private String[] columnHeaders = {"Collection", "Cached", "Count", "Validated"};
        private List<T> data = new ArrayList<T>();

        public DataSetModel(List<T> data) {
            this.data = data;
        }

        public void setData(List<T> data) {
            this.data = data;
        }

        public T getSelectedItem(int i) {
            return data.get(i);
        }

        @Override
        public String getColumnName(int i) {
            return columnHeaders[i];
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.length;
        }

        @Override
        public Object getValueAt(int row, int col) {
            T dataSetInfo = data.get(row);
            Object o;
            switch (col) {
                case 0:
                    o = dataSetInfo.getSpec();
                    break;
                case 1:
//                    o = dataSetInfo.hasSource();
                    o = false;
                    break;
                case 2:
                    o = StringUtils.isEmpty(dataSetInfo.getFacts().getRecordCount()) ? "-" : dataSetInfo.getFacts().getRecordCount();
                    break;
                case 3:
                    o = dataSetInfo.getFacts().isValid();
                    break;
                default:
                    o = "-";
                    break;
            }
            return o;
        }
    }

    public static class ColorRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object o, boolean selected, boolean hasFocus, int row, int col) {
            Component component = super.getTableCellRendererComponent(table, o, selected, hasFocus, row, col);
            if (selected) {
                component.setBackground(Color.GRAY);
                return component;
            }
            if (row % 2 == 0) {
                component.setBackground(Color.decode("0xCCF5FF"));
            }
            else {
                component.setBackground(Color.WHITE);
            }
            return component;
        }
    }

    // todo: fetch them from the server, compare with local, set "cached" status
    private List<FileStore.DataSetStore> fetchDataSets() {
        List<FileStore.DataSetStore> data = new ArrayList<FileStore.DataSetStore>();
        Map<String, FileStore.DataSetStore> dataSetStores = sipModel.getFileStore().getDataSetStores();
        for (Map.Entry<String, FileStore.DataSetStore> entry : dataSetStores.entrySet()) {
            FileStore.DataSetStore dataSetStore = entry.getValue();
            data.add(dataSetStore);
        }
        return data;
    }
}
