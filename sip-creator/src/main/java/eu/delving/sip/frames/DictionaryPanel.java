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

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.model.CreateModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dictionary editing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DictionaryPanel extends JPanel {
    public static final RecDef.Opt COPY_VERBATIM = new RecDef.Opt().setContent("<<< empty >>>");
    public static final String ASSIGN_SELECTED = "Assign Selected";
    public static final String ASSIGN_ALL = "Assign All";
    public static final String NO_DICTIONARY = "No dictionary for this mapping";
    private CreateModel createModel;
    private JCheckBox showSet = new JCheckBox("Show Assigned");
    private JLabel statusLabel = new JLabel(NO_DICTIONARY, JLabel.CENTER);
    private DictionaryModel dictionaryModel = new DictionaryModel(statusLabel);
    private JTextField patternField = new JTextField(6);
    private JButton assign = new JButton(ASSIGN_ALL);
    private ValueModel valueModel = new ValueModel();
    private JComboBox valueBox = new JComboBox(valueModel);
    private JTable table = new JTable(dictionaryModel, createTableColumnModel());
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setSelectionPattern();
        }
    });

    public DictionaryPanel(CreateModel createModel) {
        super(new BorderLayout(5, 5));
        this.createModel = createModel;
        this.timer.setRepeats(false);
        add(createNorth(), BorderLayout.NORTH);
        add(new JScrollPane(createTable()), BorderLayout.CENTER);
        add(createSouth(), BorderLayout.SOUTH);
        wireUp();
    }

    private JPanel createSouth() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(statusLabel);
        DELETE_ACTION.setEnabled(false);
        p.add(new JButton(DELETE_ACTION));
        CREATE_ACTION.setEnabled(false);
        p.add(new JButton(CREATE_ACTION));
        return p;
    }

    private JPanel createNorth() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(createNorthWest());
        p.add(createNorthEast());
        return p;
    }

    private JPanel createNorthWest() {
        JLabel label = new JLabel("Filter:", JLabel.RIGHT);
        label.setLabelFor(patternField);
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Show"));
        p.add(label);
        p.add(patternField);
        p.add(showSet);
        return p;
    }

    private JPanel createNorthEast() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Assign"));
        valueBox.setRenderer(new OptRenderer());
        p.add(valueBox);
        p.add(assign);
        return p;
    }

    private String getChosenValue() {
        RecDef.Opt value = (RecDef.Opt) valueBox.getSelectedItem();
        if (value == COPY_VERBATIM) return "";
        return value.content; // todo: key?
    }

    private JTable createTable() {
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int[] selectedRows = table.getSelectedRows();
                assign.setText(selectedRows.length > 0 ? ASSIGN_SELECTED : ASSIGN_ALL);
            }
        });
        return table;
    }

    private TableColumnModel createTableColumnModel() {
        TableColumn left = new TableColumn(0, 60);
        left.setWidth(400);
        left.setMinWidth(100);
        TableColumn right = new TableColumn(1, 60);
        right.setWidth(400);
        right.setMinWidth(100);
        right.setCellRenderer(new ValueRenderer());
        DefaultTableColumnModel tcm = new DefaultTableColumnModel();
        tcm.addColumn(left);
        tcm.addColumn(right);
        return tcm;
    }

    private static class DictionaryModel extends AbstractTableModel {
        private NodeMapping nodeMapping;
        private List<String[]> rows = new ArrayList<String[]>();
        private Map<Integer, Integer> index;
        private JLabel statusLabel;

        private DictionaryModel(JLabel statusLabel) {
            this.statusLabel = statusLabel;
        }

        public void setNodeMapping(NodeMapping nodeMapping) {
            this.nodeMapping = nodeMapping;
            this.index = null;
            int size = rows.size();
            rows.clear();
            if (size > 0) {
                fireTableRowsDeleted(0, size);
            }
            if (nodeMapping != null && nodeMapping.dictionary != null) {
                for (Map.Entry<String, String> entry : nodeMapping.dictionary.entrySet()) {
                    rows.add(new String[]{entry.getKey(), entry.getValue()});
                }
                fireTableRowsInserted(0, rows.size());
            }
        }

        public void setPattern(String pattern, boolean showSetValues) {
            String sought = pattern.toLowerCase();
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            int actual = 0, virtual = 0, assigned = 0;
            for (String[] row : rows) {
                if (!row[1].isEmpty()) {
                    assigned++;
                }
                if (row[0].toLowerCase().contains(sought) && (showSetValues || row[1].isEmpty())) {
                    map.put(virtual, actual);
                    virtual++;
                }
                actual++;
            }
            index = map;
            statusLabel.setText(String.format("Assigned: %d/%d  Visible: %d", assigned, actual, virtual));
            fireTableStructureChanged();
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
            return 2;
        }

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return "Input";
                case 1:
                    return "Output";
            }
            throw new RuntimeException();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (index != null) {
                Integer foundRow = index.get(rowIndex);
                if (foundRow != null) {
                    rowIndex = foundRow;
                }
            }
            return rows.get(rowIndex)[columnIndex];
        }

        @Override
        public void setValueAt(Object valueObject, int rowIndex, int columnIndex) {
            if (columnIndex != 1) throw new RuntimeException();
            if (valueObject == null) {
                valueObject = "";
            }
            int mappedRow = rowIndex;
            if (index != null) {
                Integer foundRow = index.get(rowIndex);
                if (foundRow != null) {
                    mappedRow = foundRow;
                }
            }
            nodeMapping.dictionary.put(rows.get(mappedRow)[0], rows.get(mappedRow)[1] = (String) valueObject);
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    private void wireUp() {
        createModel.addListener(new CreateModel.Listener() {
            @Override
            public void statsTreeNodeSet(CreateModel createModel) {
                handleEnablement();
            }

            @Override
            public void recDefTreeNodeSet(final CreateModel createModel) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        valueModel.setRecDefNode(createModel.getRecDefTreeNode().getRecDefNode());
                    }
                });
                handleEnablement();
            }

            @Override
            public void nodeMappingSet(final CreateModel createModel) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        dictionaryModel.setNodeMapping(createModel.getNodeMapping());
                    }
                });
                handleEnablement();
            }

            @Override
            public void nodeMappingChanged(CreateModel createModel) {
                handleEnablement();
            }

            void handleEnablement() {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        CREATE_ACTION.setEnabled(createModel.isDictionaryPossible());
                        DELETE_ACTION.setEnabled(createModel.isDictionaryPresent());
                        if (createModel.isDictionaryPresent()) {
                            setSelectionPattern();
                        }
                        else {
                            statusLabel.setText(NO_DICTIONARY);
                        }
                    }
                });
            }
        });
        patternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }
        });
        showSet.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                timer.restart();
            }
        });
        assign.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String value = getChosenValue();
                int[] selectedRows = table.getSelectedRows();
                if (selectedRows.length > 0) {
                    for (int row : selectedRows) table.getModel().setValueAt(value, row, 1);
                }
                else {
                    for (int row = 0; row < table.getModel().getRowCount(); row++) table.getModel().setValueAt(value, row, 1);
                }
                // todo: notify so that the mapping is saved at least
                patternField.setText("");
                patternField.requestFocus();
                timer.restart();
            }
        });
    }

    private void setSelectionPattern() {
        dictionaryModel.setPattern(patternField.getText(), showSet.isSelected());
    }

    private class OptRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            RecDef.Opt opt = (RecDef.Opt)value;
            return super.getListCellRendererComponent(list, opt.content, index, isSelected, cellHasFocus);
        }
    }

    private class ValueRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table,Object value,boolean isSelected,boolean hasFocus,int row,int column) {
            if (((String) value).isEmpty()) value = COPY_VERBATIM;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    }

    private static class ValueModel extends AbstractListModel implements ComboBoxModel {
        private List<RecDef.Opt> values = new ArrayList<RecDef.Opt>();
        private Object selectedItem = COPY_VERBATIM;

        public void setRecDefNode(RecDefNode recDefNode) {
            int size = values.size();
            values.clear();
            fireIntervalRemoved(this, 0, size);
            if (recDefNode != null && recDefNode.hasOptions()) {
                values.add(0, COPY_VERBATIM);
                values.addAll(recDefNode.getOptions());
                fireIntervalAdded(this, 0, values.size());
            }
        }

        @Override
        public void setSelectedItem(Object item) {
            selectedItem = item;
        }

        @Override
        public Object getSelectedItem() {
            return selectedItem;
        }

        @Override
        public int getSize() {
            return values.size();
        }

        @Override
        public Object getElementAt(int index) {
            return values.get(index);
        }
    }

    private final Action CREATE_ACTION = new AbstractAction("Create") {
        
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            createModel.createDictionary();
        }
    };
    
    // todo: an update action for when new stats have been generated?

    private final Action DELETE_ACTION = new AbstractAction("Delete") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int nonempty = createModel.countNonemptyDictionaryEntries();
            if (nonempty > 0) {
                int response = JOptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(DictionaryPanel.this),
                        String.format("Are you sure that you want to discard the %d entries set?", nonempty),
                        "Delete Dictionary",
                        JOptionPane.OK_CANCEL_OPTION
                );
                if (response != JOptionPane.OK_OPTION) return;
            }
            createModel.removeDictionary();
        }
    };
}
