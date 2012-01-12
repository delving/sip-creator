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
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.base.Utility;
import eu.delving.sip.menus.EditHistory;
import eu.delving.sip.model.CompileModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FieldMappingFrame extends FrameBase {
    private JTextArea groovyCodeArea;
    private JTextArea outputArea;
    private JEditorPane helpView;
    private JButton dictionaryCreate = new JButton("Create");
    private JButton dictionaryEdit = new JButton("Edit");
    private JButton dictionaryDelete = new JButton("Delete");
    private DictionaryPopup dictionaryPopup;
    private EditHistory editHistory;

    public FieldMappingFrame(JDesktopPane desktop, SipModel sipModel, final EditHistory editHistory) {
        super(desktop, sipModel, "Field Mapping", false);
        this.editHistory = editHistory;
        try {
            helpView = new JEditorPane(getClass().getResource("/groovy-help.html"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        dictionaryCreate.setEnabled(false);
        dictionaryEdit.setEnabled(false);
        dictionaryDelete.setEnabled(false);
        dictionaryPopup = new DictionaryPopup(this);
        groovyCodeArea = new JTextArea(sipModel.getFieldCompileModel().getCodeDocument());
        groovyCodeArea.setTabSize(3);
        groovyCodeArea.getDocument().addUndoableEditListener(editHistory);
        groovyCodeArea.addFocusListener(
                new FocusListener() {
                    @Override
                    public void focusGained(FocusEvent focusEvent) {
                        editHistory.setTarget(groovyCodeArea);
                    }

                    @Override
                    public void focusLost(FocusEvent focusEvent) {
                        editHistory.setTarget(null);
                    }
                }
        );
        outputArea = new JTextArea(sipModel.getFieldCompileModel().getOutputDocument());
        Utility.attachUrlLauncher(outputArea);
        wireUp();
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getFieldCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        add(createPanel(), BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    private JPanel createPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createGroovyPanel());
        p.add(createOutputPanel());
        return p;
    }

    private JComponent createGroovyPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Code", createCodePanel());
        tabs.addTab("Help", scroll(helpView));
        return tabs;
    }

    private JComponent createCodePanel() {
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

    private void setNodeMapping(NodeMapping nodeMapping) {
        if (nodeMapping != null) {
            StatsTreeNode node = null; // todo: where to get it?
            if (node != null) {
                dictionaryCreate.setEnabled(nodeMapping.dictionary == null);// todo && CodeGenerator.isDictionaryPossible(fieldMapping.getDefinition(), node));
            }
            else {
                dictionaryCreate.setEnabled(false);
            }
            dictionaryEdit.setEnabled(nodeMapping.dictionary != null);
            dictionaryDelete.setEnabled(nodeMapping.dictionary != null);
        }
        else {
            dictionaryCreate.setEnabled(false);
            dictionaryEdit.setEnabled(false);
            dictionaryDelete.setEnabled(false);
        }
    }

//    private StatsTreeNode getRecDefTreeNode(FieldMapping fieldMapping) {
//        SourceVariable sourceVariable = getSourceVariable(fieldMapping);
//        return sourceVariable != null ? sourceVariable.getRecDefTreeNode() : null;
//        return null;
//    }

    private void wireUp() {
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                // todo: implement
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
                // todo: implement
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                // todo: implement
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                // todo: implement
            }

        });
        dictionaryCreate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
//                FieldMapping fieldMapping = sipModel.getMappingModel().getNodeMapping();
//                if (fieldMapping != null) {
//                    throw new RuntimeException("implement!");
//                    CodeGenerator codeGenerator = new CodeGenerator();
//                    Statistics statistics = null; // todo: get them from somewhere
//                    fieldMapping.createDictionary(statistics.getHistogramValues());
//                    codeGenerator.generateCodeFor(fieldMapping, sourceVariable, true);
//                    setFieldMapping(fieldMapping);
//                }
            }
        });
        dictionaryEdit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                final FieldMapping fieldMapping = sipModel.getMappingModel().getSelectedRecDefNode();
//                if (fieldMapping != null) {
//                    dictionaryPopup.editDictionary(fieldMapping, new Runnable() {
//                        @Override
//                        public void run() {
//                            setFieldMapping(fieldMapping);
//                        }
//                    });
//                }
            }
        });
        dictionaryDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                NodeMapping nodeMapping = sipModel.getCreateModel().getNodeMapping();
                if (nodeMapping != null) {
                    if (nodeMapping.dictionary == null) {
                        throw new RuntimeException("No dictionary to delete!");
                    }
                    int nonemptyEntries = 0;
                    for (String value : nodeMapping.dictionary.values()) {
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
                    nodeMapping.dictionary = null;
//                    SourceVariable sourceVariable = getSourceVariable(fieldMapping);
//                    if (sourceVariable != null) {
//                        codeGenerator.generateCodeFor(fieldMapping, sourceVariable, false);
//                    }
//                    setFieldMapping(fieldMapping);
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
                        case ORIGINAL:
                            editHistory.discardAllEdits();
                            // fall through
                        case SAVED:
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

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(400, 250);
    }
}
