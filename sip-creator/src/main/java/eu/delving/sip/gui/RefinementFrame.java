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

package eu.delving.sip.gui;

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.FileStore;
import eu.europeana.sip.gui.Utility;
import eu.europeana.sip.model.CompileModel;
import eu.europeana.sip.model.FieldMappingListModel;
import eu.europeana.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RefinementFrame extends FrameBase {
    private JTextArea groovyCodeArea;
    private JTextArea outputArea;
    private JButton removeMappingButton = new JButton("Remove Selected Mapping");
    private JButton dictionaryCreate = new JButton("Create");
    private JButton dictionaryEdit = new JButton("Edit");
    private JButton dictionaryDelete = new JButton("Delete");
    private JList mappingList;

    public RefinementFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Refinement", false);
        mappingList = new JList(sipModel.getFieldMappingListModel());
        mappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mappingList.setCellRenderer(new FieldMappingListModel.CellRenderer());
        dictionaryCreate.setEnabled(false);
        dictionaryEdit.setEnabled(false);
        dictionaryDelete.setEnabled(false);
        groovyCodeArea = new JTextArea(sipModel.getFieldCompileModel().getCodeDocument());
        groovyCodeArea.setTabSize(3);
        groovyCodeArea.setToolTipText(Utility.GROOVY_TOOL_TIP);
        outputArea = new JTextArea(sipModel.getFieldCompileModel().getOutputDocument());
        outputArea.setEditable(false);
        URLLauncher.attach(outputArea);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(createLeftSide());
        split.setRightComponent(createRightSide());
        split.setDividerLocation(0.5);
        add(split, BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    private JPanel createLeftSide() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createFieldMappingListPanel(), BorderLayout.CENTER);
        p.add(removeMappingButton, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createRightSide() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createGroovyPanel());
        p.add(createOutputPanel());
        return p;
    }

    private JPanel createFieldMappingListPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Field Mappings"));
        p.add(scroll(mappingList));
        p.setMaximumSize(new Dimension(200, 2000));
        return p;
    }

    private JPanel createGroovyPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Groovy Code"));
        p.add(scroll(groovyCodeArea), BorderLayout.CENTER);
        p.add(createDictionaryButtons(), BorderLayout.EAST);
        return p;
    }

    private JPanel createDictionaryButtons() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.PAGE_AXIS));
        p.setBorder(BorderFactory.createTitledBorder("Dictionary"));
        p.add(dictionaryCreate);
        p.add(dictionaryEdit);
        p.add(dictionaryDelete);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        p.add(scroll(outputArea), BorderLayout.CENTER);
        p.add(new JLabel("Note: URLs can be launched by double-clicking them.", JLabel.CENTER), BorderLayout.SOUTH);
        return p;
    }

    private void setFieldMapping(FieldMapping fieldMapping) {
        if (fieldMapping != null) {
            sipModel.getFieldCompileModel().setSelectedPath(fieldMapping.getDefinition().path.toString());
            AnalysisTree.Node node = getNode(fieldMapping);
            if (node != null) {
                dictionaryCreate.setEnabled(fieldMapping.dictionary == null && CodeGenerator.isDictionaryPossible(fieldMapping.getDefinition(), node));
            }
            else {
                dictionaryCreate.setEnabled(false);
            }
            dictionaryEdit.setEnabled(fieldMapping.dictionary != null);
            dictionaryDelete.setEnabled(fieldMapping.dictionary != null);
            removeMappingButton.setEnabled(true);
        }
        else {
            sipModel.getFieldCompileModel().setSelectedPath(null);
            removeMappingButton.setEnabled(false);
            dictionaryCreate.setEnabled(false);
            dictionaryEdit.setEnabled(false);
            dictionaryDelete.setEnabled(false);
        }
    }

    private AnalysisTree.Node getNode(FieldMapping fieldMapping) {
        SourceVariable sourceVariable = getSourceVariable(fieldMapping);
        return sourceVariable != null ? sourceVariable.getNode() : null;
    }

    private SourceVariable getSourceVariable(FieldMapping fieldMapping) {
        List<String> variableNames = fieldMapping.getVariableNames();
        SourceVariable found = null;
        if (variableNames.size() == 1) {
            String variableName = variableNames.get(0);
            for (SourceVariable sourceVariable : sipModel.getVariables()) {
                if (sourceVariable.getVariableName().equals(variableName)) {
                    return sourceVariable;
                }
            }
        }
        return found;
    }

    private void wireUp() {
        removeMappingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
                    sipModel.removeFieldMapping(fieldMapping);
                }
            }
        });
        dictionaryCreate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
                    CodeGenerator codeGenerator = new CodeGenerator();
                    SourceVariable sourceVariable = getSourceVariable(fieldMapping);
                    fieldMapping.createDictionary(sourceVariable.getNode().getStatistics().getHistogramValues());
                    codeGenerator.generateCodeFor(fieldMapping, sourceVariable, true);
                    setFieldMapping(fieldMapping);
                }
            }
        });
        dictionaryEdit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
//                    DictionaryDialog dialog = new DictionaryDialog(parent, fieldMapping, new Runnable() {
//                        @Override
//                        public void run() {
//                            setFieldMapping(fieldMapping);
//                        }
//                    });
//                    dialog.setVisible(true);
                }
            }
        });
        dictionaryDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                if (fieldMapping != null) {
                    if (fieldMapping.dictionary == null) {
                        throw new RuntimeException("No dictionary to delete!");
                    }
                    int nonemptyEntries = 0;
                    for (String value : fieldMapping.dictionary.values()) {
                        if (!value.trim().isEmpty()) {
                            nonemptyEntries++;
                        }
                    }
                    if (nonemptyEntries > 0) {
                        int response = JOptionPane.showConfirmDialog(
                                parent,
                                String.format(
                                        "Are you sure that you want to discard the %d entries set?",
                                        nonemptyEntries
                                ),
                                "Delete Dictionary",
                                JOptionPane.OK_CANCEL_OPTION
                        );
                        if (response != JOptionPane.OK_OPTION) {
                            return;
                        }
                    }
                    fieldMapping.dictionary = null;
                    CodeGenerator codeGenerator = new CodeGenerator();
                    SourceVariable sourceVariable = getSourceVariable(fieldMapping);
                    if (sourceVariable != null) {
                        codeGenerator.generateCodeFor(fieldMapping, sourceVariable, false);
                    }
                    setFieldMapping(fieldMapping);
                }
            }
        });
        mappingList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) return;
                FieldMapping fieldMapping = (FieldMapping) mappingList.getSelectedValue();
                setFieldMapping(fieldMapping);
            }
        });
        sipModel.addUpdateListener(new SipModel.UpdateListener() {
            @Override
            public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
            }

            @Override
            public void updatedStatistics(FieldStatistics fieldStatistics) {

            }

            @Override
            public void updatedRecordRoot(Path recordRoot, int recordCount) {
            }

            @Override
            public void normalizationMessage(boolean complete, String message) {
            }
        });
        sipModel.getFieldCompileModel().getCodeDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sipModel.getFieldCompileModel().setCode(groovyCodeArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sipModel.getFieldCompileModel().setCode(groovyCodeArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sipModel.getFieldCompileModel().setCode(groovyCodeArea.getText());
            }
        });
        groovyCodeArea.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                sipModel.getRecordCompileModel().refreshCode(); // todo: somebody else do this?
            }
        });
        sipModel.getFieldCompileModel().addListener(new ModelStateListener());
        outputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        outputArea.setCaretPosition(0);
                    }
                });
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });

    }

    private class ModelStateListener implements CompileModel.Listener {

        @Override
        public void stateChanged(final CompileModel.State state) {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    switch (state) {
                        case PRISTINE:
                        case UNCOMPILED:
                            groovyCodeArea.setBackground(new Color(1.0f, 1.0f, 1.0f));
                            break;
                        case EDITED:
                            groovyCodeArea.setBackground(new Color(1.0f, 1.0f, 0.9f));
                            break;
                        case ERROR:
                            groovyCodeArea.setBackground(new Color(1.0f, 0.9f, 0.9f));
                            break;
                        case COMMITTED:
                            groovyCodeArea.setBackground(new Color(0.9f, 1.0f, 0.9f));
                            break;
                    }
                }
            });
        }
    }


    // todo: dictionary mapping popup

}
