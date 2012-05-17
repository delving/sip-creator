/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.CreateTransition;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static eu.delving.metadata.NodeMappingChange.DICTIONARY;
import static eu.delving.sip.base.DictionaryHelper.*;

/**
 * Dictionary editing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DictionaryPanel extends JPanel {
    public static final String COPY_VERBATIM = "<<< verbatim >>>";
    public static final String ASSIGN_SELECTED = "Assign Selected";
    public static final String ASSIGN_ALL = "Assign All";
    public static final String NO_DICTIONARY = "No dictionary for this mapping";
    private SipModel sipModel;
    private CreateModel createModel;
    private JCheckBox showAssigned = new JCheckBox("Show Assigned");
    private JLabel statusLabel = new JLabel(NO_DICTIONARY, JLabel.CENTER);
    private DictionaryModel dictionaryModel = new DictionaryModel(statusLabel);
    private JTextField patternField = new JTextField(6);
    private JButton assign = new JButton(ASSIGN_ALL);
    private ValueModel valueModel = new ValueModel();
    private JList valueList = new JList(valueModel);
    private JTable table = new JTable(dictionaryModel, createTableColumnModel());
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setSelectionPattern();
        }
    });

    public DictionaryPanel(SipModel sipModel) {
        super(new BorderLayout(5, 5));
        this.sipModel = sipModel;
        this.createModel = sipModel.getCreateModel();
        this.timer.setRepeats(false);
        valueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        valueList.setPrototypeCellValue("somelongoptionvaluename");
        add(createCenter(), BorderLayout.CENTER);
        add(createEast(), BorderLayout.EAST);
        add(createSouth(), BorderLayout.SOUTH);
        wireUp();
    }

    private JPanel createCenter() {
        JPanel p = new JPanel(new BorderLayout());
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int[] selectedRows = table.getSelectedRows();
                assign.setText(selectedRows.length > 0 ? ASSIGN_SELECTED : ASSIGN_ALL);
            }
        });
        p.add(createNorth(), BorderLayout.NORTH);
        p.add(SwingHelper.scrollV("Dictionary Entries", table), BorderLayout.CENTER);
        return p;
    }

    private JPanel createSouth() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createTitledBorder("Dictionary"));
        p.add(statusLabel);
        DELETE_ACTION.setEnabled(false);
        p.add(new JButton(DELETE_ACTION));
        p.add(new JButton(REFRESH_ACTION));
        return p;
    }

    private JPanel createNorth() {
        JLabel label = new JLabel("Filter:", JLabel.RIGHT);
        label.setLabelFor(patternField);
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Show"));
        p.add(label);
        p.add(patternField);
        p.add(showAssigned);
        return p;
    }

    private JPanel createEast() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(SwingHelper.scrollV("Target Values", valueList), BorderLayout.CENTER);
        p.add(assign, BorderLayout.SOUTH);
        return p;
    }

    private String getChosenValue() {
        String value = (String) valueList.getSelectedValue();
        return value == null || COPY_VERBATIM.equals(value) ? "" : value;
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
            public void transition(final CreateModel createModel, CreateTransition transition) {
                if (!transition.nodeMappingChanged) return;
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        boolean isDictionary = createModel.hasNodeMapping() && createModel.getNodeMapping().dictionary != null;
                        DELETE_ACTION.setEnabled(isDictionary);
                        if (isDictionary) {
                            valueModel.setRecDefTreeNode(createModel.getRecDefTreeNode());
                            dictionaryModel.setNodeMapping(createModel.getNodeMapping());
                            setSelectionPattern();
                        }
                        else {
                            statusLabel.setText(NO_DICTIONARY);
                            valueModel.setRecDefTreeNode(null);
                            dictionaryModel.setNodeMapping(null);
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
        showAssigned.addItemListener(new ItemListener() {
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
                    for (int row = 0; row < table.getModel().getRowCount(); row++)
                        table.getModel().setValueAt(value, row, 1);
                }
                patternField.setText("");
                patternField.requestFocus();
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        if (createModel.hasNodeMapping()) createModel.getNodeMapping().notifyChanged(DICTIONARY);
                    }
                });
                timer.restart();
            }
        });
    }

    private void setSelectionPattern() {
        dictionaryModel.setPattern(patternField.getText(), showAssigned.isSelected());
    }

    private class ValueRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (((String) value).trim().isEmpty()) value = COPY_VERBATIM;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    }

    private static class ValueModel extends AbstractListModel {
        private List<String> values = new ArrayList<String>();

        public void setRecDefTreeNode(RecDefTreeNode recDefTreeNode) {
            int size = values.size();
            values.clear();
            fireIntervalRemoved(this, 0, size);
            if (recDefTreeNode != null && recDefTreeNode.getRecDefNode().getOptList() != null) {
                values.add(0, COPY_VERBATIM);
                values.addAll(recDefTreeNode.getRecDefNode().getOptList().getValues());
                fireIntervalAdded(this, 0, values.size());
            }
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

    private final Action REFRESH_ACTION = new AbstractAction("Refresh Dictionary") {

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    if (isDictionaryPossible(createModel.getNodeMapping())) refreshDictionary(createModel.getNodeMapping());
                }
            });
        }
    };

    private final Action DELETE_ACTION = new AbstractAction("Delete") {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int nonempty = countNonemptyDictionaryEntries(createModel.getNodeMapping());
            if (nonempty > 0) {
                boolean confirm =  sipModel.getFeedback().confirm(
                        "Delete Dictionary",
                        String.format("Are you sure that you want to discard the %d entries set?", nonempty)
                ) ;
                if (!confirm) return;
            }
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    removeDictionary(createModel.getNodeMapping());
                }
            });
        }
    };
}
