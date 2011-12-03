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

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.SourceVariable;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateFrame extends FrameBase {

    private final static String SELECT_CAPTION = "<html><center>Select from the input fields<br>or fill in a constant and select an output field.</html>";
    private final static String CREATE_CAPTION = "<html><b>Create Mapping</b> (Click here or press &lt;space>)</html>";
    private JTextField constantField = new JTextField("?");
    private JList variablesList;
    private final CreateMappingAction createMappingAction = new CreateMappingAction();

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Create", false);
        createMappingAction.setEnabled(false);
        variablesList = new JList(sipModel.getStatsModel().getVariablesListModel());
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createPanel(), BorderLayout.CENTER);
        JButton button = new JButton(createMappingAction);
        button.setPreferredSize(new Dimension(50, 80));
        content.add(button, BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private void wireUp() {
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {

            @Override
            public void dataSetChanged(DataSet dataSet) {
                variablesList.clearSelection();
            }

            @Override
            public void dataSetRemoved() {
                variablesList.clearSelection();
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                // todo: implement
            }
        });
        constantField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                variablesList.clearSelection();
            }

            @Override
            public void focusLost(FocusEvent e) {
            }
        });
        variablesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                final SourceVariable sourceVariable = (SourceVariable) variablesList.getSelectedValue();
                if (sourceVariable != null && sourceVariable.hasStatistics()) {
                    sipModel.getStatsModel().selectStatistics(sourceVariable.getStatistics());
                    constantField.setText("?");
                }
            }
        });
        getActionMap().put(createMappingAction.getValue(Action.NAME), createMappingAction);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(' '), createMappingAction.getValue(Action.NAME));
    }

    private JComponent createPanel() {
        return createVariableMappingPanel();
    }

    private JPanel createVariableMappingPanel() {
        return createVariableMappingTop();
    }

    private JPanel createVariableMappingTop() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEtchedBorder());
        p.add(createVariableListPanel(), BorderLayout.CENTER);
        p.add(createConstantFieldPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createVariableListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Variables"));
        p.add(scroll(variablesList), BorderLayout.CENTER);
        variablesList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                enableCreateMapping();
            }
        });
        return p;
    }

    /**
     * Activate createMappingAction when:
     * <ul>
     * <li>Target field is selected</li>
     * <li>Source field is selected OR constant field is defined</li>
     * </ul>
     */
    private void enableCreateMapping() {
        boolean source = !variablesList.isSelectionEmpty();
        boolean constant = !constantField.getText().equals("?") && !StringUtils.isEmpty(constantField.getText());
        boolean enabled = (source || constant);
        createMappingAction.putValue("Name", enabled ? CREATE_CAPTION : SELECT_CAPTION);
        createMappingAction.setEnabled(enabled);
    }

    private JPanel createConstantFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Constant Source"));
        constantField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                enableCreateMapping();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                insertUpdate(documentEvent);
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                insertUpdate(documentEvent);
            }
        });
        p.add(constantField);
        return p;
    }

    private class CreateMappingAction extends AbstractAction {

        private CreateMappingAction() {
            super(SELECT_CAPTION);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getFeedback().alert("Under construction");
//            FieldDefinition fieldDefinition = (FieldDefinition) targetFieldList.getSelectedValue();
//            if (fieldDefinition != null) {
//                CodeGenerator generator = new CodeGenerator();
//                final FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
//                generator.generateCodeFor(fieldMapping, createSelectedVariableList(), getConstantFieldValue());
//                Exec.work(new Runnable() {
//                    @Override
//                    public void run() {
//                        sipModel.getMappingModel().addMapping(fieldMapping);
//                    }
//                });
//            }
            clear();
        }

        private void clear() {
            variablesList.clearSelection();
            constantField.setText("");
        }

        private String getConstantFieldValue() {
            return constantField.getText();
        }

        private List<SourceVariable> createSelectedVariableList() {
            List<SourceVariable> list = new ArrayList<SourceVariable>();
            for (Object sourceVariable : variablesList.getSelectedValues()) {
                list.add((SourceVariable) sourceVariable);
            }
            return list;
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(600, 440);
    }
}
