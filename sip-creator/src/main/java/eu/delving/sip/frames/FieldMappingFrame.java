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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.SourceVariable;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.CompileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
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

public class FieldMappingFrame extends FrameBase {
    private JTextArea groovyCodeArea;
    private JTextArea outputArea;
    private JButton dictionaryCreate = new JButton("Create");
    private JButton dictionaryEdit = new JButton("Edit");
    private JButton dictionaryDelete = new JButton("Delete");
    private DictionaryPopup dictionaryPopup;

    public FieldMappingFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Field Mapping", false);
        dictionaryCreate.setEnabled(false);
        dictionaryEdit.setEnabled(false);
        dictionaryDelete.setEnabled(false);
        dictionaryPopup = new DictionaryPopup(this);
        groovyCodeArea = new JTextArea(sipModel.getFieldCompileModel().getCodeDocument());
        groovyCodeArea.setTabSize(3);
        outputArea = new JTextArea(sipModel.getFieldCompileModel().getOutputDocument());
        outputArea.setEditable(false);
        Utility.attachUrlLauncher(outputArea);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        add(createPanel(), BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    @Override
    protected DataSetState getMinDataSetState() {
        return DataSetState.ANALYZED;
    }

    private JPanel createPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createGroovyPanel());
        p.add(createOutputPanel());
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
            AnalysisTree.Node node = getNode(fieldMapping);
            if (node != null) {
                dictionaryCreate.setEnabled(fieldMapping.dictionary == null && CodeGenerator.isDictionaryPossible(fieldMapping.getDefinition(), node));
            }
            else {
                dictionaryCreate.setEnabled(false);
            }
            dictionaryEdit.setEnabled(fieldMapping.dictionary != null);
            dictionaryDelete.setEnabled(fieldMapping.dictionary != null);
        }
        else {
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
            for (SourceVariable sourceVariable : sipModel.getAnalysisModel().getVariables()) {
                if (sourceVariable.getVariableName().equals(variableName)) {
                    return sourceVariable;
                }
            }
        }
        return found;
    }

    private void wireUp() {
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void factChanged() {
            }

            @Override
            public void select(FieldMapping fieldMapping) {
                setFieldMapping(fieldMapping);
            }

            @Override
            public void fieldMappingChanged() {
            }

            @Override
            public void recordMappingChanged(RecordMapping recordMapping) {
            }
        });
        dictionaryCreate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FieldMapping fieldMapping = sipModel.getMappingModel().getSelectedFieldMapping();
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
                final FieldMapping fieldMapping = sipModel.getMappingModel().getSelectedFieldMapping();
                if (fieldMapping != null) {
                    dictionaryPopup.editDictionary(fieldMapping, new Runnable() {
                        @Override
                        public void run() {
                            setFieldMapping(fieldMapping);
                        }
                    });
                }
            }
        });
        dictionaryDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                FieldMapping fieldMapping = sipModel.getMappingModel().getSelectedFieldMapping();
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
        sipModel.getFieldCompileModel().getCodeDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                setCode();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                setCode();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                setCode();
            }

            private void setCode() {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getFieldCompileModel().setCode(groovyCodeArea.getText());
                    }
                });
            }
        });
        groovyCodeArea.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getRecordCompileModel().refreshCode(); // todo: somebody else do this?
                    }
                });
            }
        });
        sipModel.getFieldCompileModel().addListener(new ModelStateListener());
        outputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                outputArea.setCaretPosition(0);
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
            Exec.swing(new Runnable() {

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
}
