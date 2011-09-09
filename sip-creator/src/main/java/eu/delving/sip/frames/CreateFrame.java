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

package eu.delving.sip.frames;

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.FieldListModel;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    private JButton createObviousMappingButton = new JButton("Create obvious mappings");
    private JTextField constantField = new JTextField("?");
    private JList variablesList;
    private JList targetFieldList;
    private ObviousMappingsPopup obviousMappingsPopup;

    public CreateFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Create", false);
        obviousMappingsPopup = new ObviousMappingsPopup(this);
        variablesList = new JList(sipModel.getAnalysisModel().getVariablesListWithCountsModel());
        targetFieldList = new JList(sipModel.getUnmappedFieldListModel());
        targetFieldList.setCellRenderer(new FieldListModel.CellRenderer());
        targetFieldList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createPanel(), BorderLayout.CENTER);
        content.add(new JButton(new CreateMappingAction()), BorderLayout.SOUTH);
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
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                // todo: implement
            }
        });
        createObviousMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                obviousMappingDialog.setVisible(true);
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
                    sipModel.getAnalysisModel().selectStatistics(sourceVariable.getStatistics());
                    constantField.setText("?");
                }
            }
        });
    }

    private JComponent createPanel() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createVariableMappingPanel(), createTargetPanel());
        split.setDividerLocation(0.5);
        split.setResizeWeight(0.5);
        return split;
    }

    private JPanel createTargetPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Unmapped Output Fields"));
        p.add(scroll(targetFieldList), BorderLayout.CENTER);
        return p;
    }

    private JPanel createVariableMappingPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createVariableMappingTop(), BorderLayout.CENTER);
        p.add(new JButton(obviousMappingsPopup.getAction()), BorderLayout.SOUTH);
        return p;
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
        p.setBorder(BorderFactory.createTitledBorder("Input Field Source"));
        p.add(scroll(variablesList), BorderLayout.CENTER);
        return p;
    }

    private JPanel createConstantFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Constant Source"));
        p.add(constantField);
        return p;
    }

    private class CreateMappingAction extends AbstractAction {

        private CreateMappingAction() {
            super("<html><table cellpadding=10><tr><td>Select from the input fields or fill in a constant and select an output field.</td><td><h2>Create Mapping</h2></td></table>");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            FieldDefinition fieldDefinition = (FieldDefinition) targetFieldList.getSelectedValue();
            if (fieldDefinition != null) {
                CodeGenerator generator = new CodeGenerator();
                final FieldMapping fieldMapping = new FieldMapping(fieldDefinition);
                generator.generateCodeFor(fieldMapping, createSelectedVariableList(), getConstantFieldValue());
                Exec.work(
                        new Runnable() {
                            @Override
                            public void run() {
                                sipModel.getMappingModel().addMapping(fieldMapping);
                            }
                        }
                );
            }
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


    private void prepareCreateMappingButtons() {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                CodeGenerator codeGenerator = new CodeGenerator();
                List<FieldMapping> obvious = codeGenerator.createObviousMappings(
                        sipModel.getUnmappedFields(),
                        sipModel.getAnalysisModel().getVariables(),
                        sipModel.getDataSetModel().getFactDefinitions()
                );
                if (obvious.isEmpty()) {
                    if (obviousMappingsPopup.isVisible()) {
                        obviousMappingsPopup.closeFrame();
                    }
                    createObviousMappingButton.setEnabled(false);
                }
                else {
                    createObviousMappingButton.setEnabled(true);
                }
            }
        });
    }
}
