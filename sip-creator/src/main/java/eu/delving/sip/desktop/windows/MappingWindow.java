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

import eu.delving.metadata.Facts;
import eu.delving.sip.desktop.GridBagHelper;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This window consists of two tabs, a mapping tab and a constants tab.
 * Map the source fields to the target fields.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class MappingWindow extends DesktopWindow {

    private JTabbedPane tabbedPane = new JTabbedPane();
    private JTable table = new JTable();

    public MappingWindow(SipModel sipModel) {
        super(sipModel);
        tabbedPane.addTab("Dynamic fields", new MappingPanel());
        tabbedPane.addTab("Constant fields", new ConstantsPanel());
        tabbedPane.setPreferredSize(getPreferredSize());
        add(tabbedPane);
    }

    private class MappingPanel extends JPanel {

        private JList sourceFields;
        private JList targetFields;
        private JButton button = new JButton("Create mapping");

        {
            setLayout(new GridBagLayout());
            sourceFields = new JList();
            sourceFields.setListData(new String[]{"sample.afield", "sample.bfield"});
            targetFields = new JList();
            targetFields.setListData(new String[]{"europeana:type", "europeana:rights"});
            buildLayout();
        }

        private void buildLayout() {
            GridBagHelper g = new GridBagHelper();
            g.reset();
            add(createScrollPane("Source Fields", sourceFields), g);
            g.right();
            add(createScrollPane("Target Fields", targetFields), g);
            g.line();
            g.right();
            add(button, g);
        }

        private JScrollPane createScrollPane(String title, JList list) {
            JScrollPane sourceScrollPane = new JScrollPane(list);
            sourceScrollPane.setBorder(BorderFactory.createTitledBorder(title));
            return sourceScrollPane;
        }
    }

    private class ConstantsPanel extends JPanel {

        {
            setLayout(new BorderLayout());
            add(new JLabel("Facts"), BorderLayout.NORTH);
            add(table, BorderLayout.CENTER);
            add(new JButton("Save"), BorderLayout.SOUTH);
        }
    }

    private class MapTableModel extends AbstractTableModel {

        private String[] columnHeaders = {"Key", "Value"};
        private Map<String, String> data = new HashMap<String, String>();
        private List<Constant> constants = new ArrayList<Constant>();

        private MapTableModel(Facts facts) {
            setData(facts);
        }

        private class Constant {
            String key;
            String value;

            private Constant(String key, String value) {
                this.key = key;
                this.value = value;
            }
        }

        private void setData(Facts facts) {
            this.data = facts.getMap();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                constants.add(new Constant(entry.getKey(), entry.getValue()));
            }
            fireTableDataChanged();
        }

        public String getSelectedItem(String key) {
            return data.get(key);
        }

        @Override
        public boolean isCellEditable(int i, int i1) {
            return true;
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
            Object o;
            switch (col) {
                case 0:
                    o = constants.get(row).key;
                    break;
                case 1:
                    o = constants.get(row).value;
                    break;
                default:
                    o = "-";
                    break;
            }
            return o;
        }
    }
}
