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

import eu.delving.sip.DataSetInfo;
import eu.europeana.sip.util.GridBagHelper;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

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
 */
public class DataSetWindow extends DesktopWindow {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String TITLE_LABEL = "Data sets";
    private JLabel title = new JLabel(TITLE_LABEL);
    private JTable dataSets;
    private JTextField filter = new JTextField("Filter");
    private JButton select = new JButton("Select");
    private JButton cancel = new JButton("Cancel");
    private DataSetModel<DataSet> dataSetModel;

    public DataSetWindow() {
        setLayout(new GridBagLayout());
        buildLayout();
        addActions();
    }

    private void addActions() {
        select.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        DataSetModel<DataSet> model = dataSetModel;
                        DataSet selectedItem = model.getSelectedItem(dataSets.getSelectedRow());
                        dataSetChangeListener.dataSetIsChanging(selectedItem);
                        int result = JOptionPane.showConfirmDialog(
                                DataSetWindow.this,
                                String.format("<html>You are switching to <b>%s</b>. Are you sure?<br/>Your current workspace will be saved.</html>",
                                        selectedItem.getName()),
                                "Change data set",
                                JOptionPane.YES_NO_OPTION
                        );
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                dataSetChangeListener.dataSetHasChanged(selectedItem);
                                setVisible(false);
                                break;
                            default:
                                dataSetChangeListener.dataSetChangeCancelled(selectedItem);
                                break;
                        }
                    }
                }
        );
    }

    private void buildLayout() {
        GridBagHelper g = new GridBagHelper();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.reset();
        add(title, g);
        dataSetModel = new DataSetModel<DataSet>(createMockData());
        dataSets = new JTable(dataSetModel);
        dataSets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataSets.setDefaultRenderer(Object.class, new ColorRenderer());
        g.line();
        g.gridwidth = 2;
        add(new JScrollPane(dataSets), g);
        g.gridwidth = 1;
        g.line();
        add(cancel, g);
        g.right();
        add(select, g);
    }

    private class DataSetModel<T extends DataSet> extends AbstractTableModel {

        private String[] columnHeaders = {"Collection name", "Records", "Spec", "Status"};
        private List<T> data = new ArrayList<T>();

        private DataSetModel(List<T> data) {
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
                    o = dataSetInfo.getName();
                    break;
                case 1:
                    o = dataSetInfo.getRecordCount();
                    break;
                case 2:
                    o = dataSetInfo.getSpec();
                    break;
                case 3:
                    o = dataSetInfo.getState();
                    break;
                default:
                    o = "-";
                    break;
            }
            return o;
        }
    }

    private class ColorRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object o, boolean selected, boolean hasFocus, int row, int col) {
            Component component = super.getTableCellRendererComponent(table, o, selected, hasFocus, row, col);
            if (selected) {
                component.setBackground(Color.MAGENTA);
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

    // todo: this is mock data, replace it by the actual data from the data store
    private List<DataSet> createMockData() {
        List<DataSet> data = new ArrayList<DataSet>();
        data.add(new DataSetImpl(createDataSetInfo("Frisian Stories", "Description?", "NEW", 234)));
        data.add(new DataSetImpl(createDataSetInfo("TEL Treasures", "Description?", "NEW", 2)));
        data.add(new DataSetImpl(createDataSetInfo("Sample collection", "Description?", "NEW", 4123)));
        data.add(new DataSetImpl(createDataSetInfo("Princessehof", "Description?", "Changed on server", 5346345)));
        data.add(new DataSetImpl(createDataSetInfo("ABC", "Test?", "INDEXED", 3124)));
        return data;
    }

    private DataSetInfo createDataSetInfo(String name, String spec, String state, int recordCount) {
        DataSetInfo dataSetInfo = new DataSetInfo();
        dataSetInfo.name = name;
        dataSetInfo.spec = spec;
        dataSetInfo.state = state;
        dataSetInfo.recordCount = recordCount;
        return dataSetInfo;
    }

    class DataSetImpl implements DataSet {

        private DataSetInfo dataSetInfo;

        DataSetImpl(DataSetInfo dataSetInfo) {
            this.dataSetInfo = dataSetInfo;
        }

        @Override
        public String getName() {
            return dataSetInfo.name;
        }

        @Override
        public String getSpec() {
            return dataSetInfo.spec;
        }

        @Override
        public String getState() {
            return dataSetInfo.state;
        }

        @Override
        public int getRecordCount() {
            return dataSetInfo.recordCount;
        }
    }
}
