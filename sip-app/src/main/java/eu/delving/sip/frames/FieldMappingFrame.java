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

import eu.delving.metadata.*;
import eu.delving.sip.base.*;
import eu.delving.sip.model.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static eu.delving.metadata.NodeMappingChange.OPERATOR;
import static eu.delving.metadata.StringUtil.toGroovyFirstIdentifier;
import static eu.delving.metadata.StringUtil.toGroovyIdentifier;
import static eu.delving.sip.base.SwingHelper.*;

/**
 * Refining the mapping interactively involves editing the mapping code for an individual NodeMapping.  This involves
 * a code window or alternatively a dictionary, and choices about which operator to use.  Here you can also
 * revert to the default generated mapping code, and some lists of available functions and variables are provided.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FieldMappingFrame extends FrameBase {
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 16);
    private final Action REVERT_ACTION = new RevertAction();
    private final Action UNDO_ACTION = new UndoAction();
    private final Action REDO_ACTION = new RedoAction();
    private JTextArea codeArea;
    private JTextArea docArea;
    private JTextArea outputArea;
    private boolean operatorBoxSetting = false;
    private JComboBox operatorBox = new JComboBox(Operator.values());
    private FunctionListModel functionModel = new FunctionListModel();
    private JList functionList = new MappingFunctionJList(functionModel);
    private ContextVarListModel contextVarModel = new ContextVarListModel();
    private JList contextVarList = new ContextVarJList(contextVarModel);
    private DictionaryPanel dictionaryPanel;
    private UndoManager undoManager = new UndoManager();
    private JTabbedPane mainTab = new JTabbedPane();

    public FieldMappingFrame(SipModel sipModel) {
        super(Which.FIELD_MAPPING, sipModel, "Field Mapping");
        dictionaryPanel = new DictionaryPanel(sipModel);
        docArea = new JTextArea(sipModel.getFieldCompileModel().getDocDocument());
        docArea.setFont(MONOSPACED);
        docArea.setTabSize(3);
        docArea.setLineWrap(true);
        docArea.setWrapStyleWord(true);
        codeArea = new JTextArea(sipModel.getFieldCompileModel().getCodeDocument());
        codeArea.setFont(MONOSPACED);
        codeArea.setTabSize(3);
        outputArea = new JTextArea(sipModel.getFieldCompileModel().getOutputDocument());
        outputArea.setWrapStyleWord(true);
        mainTab.addTab("Code", createCodeOutputPanel());
        mainTab.addTab("Dictionary", dictionaryPanel);
        mainTab.addTab("Documentation", scrollVH(docArea));
        attachAction(UNDO_ACTION);
        attachAction(REDO_ACTION);
        new URLLauncher(sipModel, outputArea, sipModel.getFeedback());
        wireUp();
        handleEnablement();
    }

    private void attachAction(Action action) {
        codeArea.getInputMap().put((KeyStroke) action.getValue(Action.ACCELERATOR_KEY), action.getValue(Action.NAME));
        codeArea.getActionMap().put(action.getValue(Action.NAME), action);
    }

    private void handleEnablement() {
        UNDO_ACTION.setEnabled(undoManager.canUndo());
        REDO_ACTION.setEnabled(undoManager.canRedo());
    }

    @Override
    protected void onOpen(boolean opened) {
        sipModel.getFieldCompileModel().setEnabled(opened);
    }

    @Override
    protected void buildContent(Container content) {
        add(mainTab, BorderLayout.CENTER);
    }

    private JPanel createCodeOutputPanel() {
        JPanel center = new JPanel(new GridLayout(0, 1, 5, 5));
        center.add(scrollVH("Groovy Code", codeArea));
        center.add(createOutputPanel());
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(center, BorderLayout.CENTER);
        p.add(createBesideCodeOutput(), BorderLayout.EAST);
        return p;
    }

    private JPanel createBesideCodeOutput() {
        JPanel pp = new JPanel(new GridLayout(0, 1, 5, 5));
        pp.add(operatorBox);
        pp.add(createActionButton(UNDO_ACTION));
        pp.add(createActionButton(REDO_ACTION));
        pp.add(new JButton(REVERT_ACTION));
        JPanel p = new JPanel(new BorderLayout());
        p.add(pp, BorderLayout.NORTH);
        p.add(createContextPanel(), BorderLayout.CENTER);
        return p;
    }

    private JComponent createContextPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(scrollV("Variables", contextVarList));
        p.add(scrollV("Functions", functionList));
        return p;
    }

    private JButton createActionButton(Action action) {
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        JButton button = new JButton(action);
        button.setText(button.getText() + " " + KeyEvent.getKeyModifiersText(stroke.getModifiers()) + KeyEvent.getKeyText(stroke.getKeyCode()));
        return button;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        p.add(scrollVH(outputArea), BorderLayout.CENTER);
        p.add(new JLabel("Note: URLs can be launched by double-clicking them.", JLabel.CENTER), BorderLayout.SOUTH);
        return p;
    }

    private void wireUp() {
        codeArea.getDocument().addUndoableEditListener(undoManager);
        codeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                handleEnablement();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                handleEnablement();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                handleEnablement();
            }
        });
        operatorBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (operatorBoxSetting) return;
                final NodeMapping nodeMapping = sipModel.getCreateModel().getNodeMapping();
                if (nodeMapping != null) {
                    exec(new Work() {
                        @Override
                        public void run() {
                            nodeMapping.operator = (Operator) operatorBox.getSelectedItem();
                            nodeMapping.notifyChanged(OPERATOR);
                        }

                        @Override
                        public Job getJob() {
                            return Job.SET_OPERATOR;
                        }
                    });
                }
            }
        });
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                if (!transition.nodeMappingChanged) return;
                if (createModel.hasNodeMapping()) {
                    final NodeMapping nodeMapping = createModel.getNodeMapping();
                    contextVarModel.setList(nodeMapping);
                    sipModel.getFieldCompileModel().setNodeMapping(nodeMapping);
                    exec(new Swing() {
                        @Override
                        public void run() {
                            setEditable(codeArea, nodeMapping.isUserCodeEditable());
                            operatorBoxSetting = true;
                            operatorBox.setSelectedIndex(nodeMapping.getOperator().ordinal());
                            operatorBoxSetting = false;
                            mainTab.setSelectedIndex(nodeMapping.hasDictionary() ? 1 : 0);
                        }
                    });
                }
                else {
                    contextVarModel.setList(null);
                    sipModel.getFieldCompileModel().setNodeMapping(null);
                    exec(new Swing() {
                        @Override
                        public void run() {
                            setEditable(codeArea, false);
                            operatorBoxSetting = true;
                            operatorBox.setSelectedIndex(0);
                            operatorBoxSetting = false;
                        }
                    });
                }
            }
        });
        sipModel.getFieldCompileModel().addListener(new MappingCompileModel.Listener() {
            @Override
            public void stateChanged(final CompileState state) {
                exec(new Swing() {
                    @Override
                    public void run() {
                        if (state == CompileState.ORIGINAL) {
                            undoManager.discardAllEdits();
                            handleEnablement();
                        }
                        state.setBackgroundOf(codeArea);
                    }
                });
            }

            @Override
            public void codeCompiled(MappingCompileModel.Type type, String code) {
                // nothing
            }
        });
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                functionModel.refresh();
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
                setFrameLocked(locked);
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
                functionModel.refresh();
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
            }
        });
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

    private class FunctionListModel extends AbstractListModel {
        private List<MappingFunction> functions = new ArrayList<MappingFunction>();

        public void refresh() {
            MappingModel mappingModel = sipModel.getMappingModel();
            List<MappingFunction> mappingFunctions = new ArrayList<MappingFunction>();
            if (mappingModel.hasRecMapping()) {
                List<MappingFunction> fromRecDef = mappingModel.getRecMapping().getRecDefTree().getRecDef().functions;
                if (fromRecDef != null) mappingFunctions.addAll(fromRecDef);
                mappingFunctions.addAll(mappingModel.getRecMapping().getFunctions());
            }
            setList(mappingFunctions);
        }

        private void setList(Collection<MappingFunction> functions) {
            if (!this.functions.isEmpty()) {
                int size = getSize();
                this.functions.clear();
                fireIntervalRemoved(this, 0, size);
            }
            this.functions.addAll(functions);
            if (!this.functions.isEmpty()) fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return functions.size();
        }

        @Override
        public Object getElementAt(int i) {
            return functions.get(i);
        }
    }

    private class ContextVarListModel extends AbstractListModel {
        private List<String> vars = new ArrayList<String>();

        public void setList(NodeMapping nodeMapping) {
            int size = getSize();
            vars.clear();
            if (size > 0) fireIntervalRemoved(this, 0, size);
            if (nodeMapping != null) vars.addAll(getContextVariables(nodeMapping));
            size = getSize();
            if (size > 0) fireIntervalAdded(this, 0, size);
        }

        @Override
        public int getSize() {
            return vars.size();
        }

        @Override
        public Object getElementAt(int i) {
            return vars.get(i);
        }

        public List<String> getContextVariables(NodeMapping nodeMapping) {
            List<String> variables = new ArrayList<String>();
            Path back = nodeMapping.inputPath;
            while (!back.isEmpty()) {
                variables.add(0, toGroovyIdentifier(back.peek()));
                back = back.parent();
            }
            SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleSourceTreeNode();
            if (sourceTreeNode != null) {
                for (SourceTreeNode treeNode : sourceTreeNode.getChildren()) {
                    if (treeNode.getTag().isAttribute()) continue;
                    variables.add(String.format(
                            "%s.%s",
                            toGroovyIdentifier(nodeMapping.inputPath.peek()),
                            toGroovyFirstIdentifier(treeNode.getTag()))
                    );
                }
            }
            variables.add(0, "_uniqueIdentifier");
            return variables;
        }
    }

    private class RevertAction extends AbstractAction {
        private RevertAction() {
            super("Revert to Original");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            boolean discard = sipModel.getFeedback().confirm("Discard", "Discard edited code and revert to the original?");
            if (discard) {
                exec(new Work() {
                    @Override
                    public void run() {
                        if (sipModel.getCreateModel().hasNodeMapping()) {
                            sipModel.getCreateModel().getNodeMapping().revertToGenerated();
                        }
                        sipModel.getFieldCompileModel().setNodeMapping(sipModel.getCreateModel().getNodeMapping());
                    }

                    @Override
                    public Job getJob() {
                        return Job.REVERT_NODE_MAPPING;
                    }
                });
            }
        }
    }

    private class UndoAction extends AbstractAction {
        private UndoAction() {
            super("Undo");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (undoManager.canUndo()) undoManager.undo();
        }
    }

    private class RedoAction extends AbstractAction {
        private RedoAction() {
            super("Redo");
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (undoManager.canRedo()) undoManager.redo();
        }
    }

    private class ContextVarJList extends JList {

        private Timer timer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String contextVar = (String) getSelectedValue();
                if (contextVar != null) {
                    int start = codeArea.getCaretPosition();
                    try {
                        Document doc = codeArea.getDocument();
                        doc.insertString(start, contextVar, null);
                    }
                    catch (BadLocationException e) {
                        throw new RuntimeException("What?", e);
                    }
                }
                clearSelection();
            }
        });

        private ContextVarJList(ContextVarListModel listModel) {
            super(listModel);
            timer.setRepeats(false);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent listSelectionEvent) {
                    if (listSelectionEvent.getValueIsAdjusting() || getSelectedValue() == null) return;
                    timer.restart();
                }
            });
            setPrototypeCellValue("alongvariablenamehere");
            setToolTipText("Clicking here will insert a variable name into the code\n" +
                    "at the current cursor position");
        }
    }

    private class MappingFunctionJList extends JList {

        private Timer timer = new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String selectedText = codeArea.getSelectedText();
                MappingFunction mappingFunction = (MappingFunction) getSelectedValue();
                if (selectedText != null && mappingFunction != null) {
                    int start = codeArea.getSelectionStart();
                    try {
                        if (selectedText.endsWith("\n")) {
                            selectedText = selectedText.substring(0, selectedText.length() - 1);
                        }
                        Document doc = codeArea.getDocument();
                        doc.remove(start, selectedText.length());
                        doc.insertString(start, String.format("%s(%s)", mappingFunction.name, selectedText), null);
                    }
                    catch (BadLocationException e) {
                        throw new RuntimeException("What?", e);
                    }
                }
                clearSelection();
            }
        });

        private MappingFunctionJList(FunctionListModel functionListModel) {
            super(functionListModel);
            timer.setRepeats(false);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent listSelectionEvent) {
                    if (listSelectionEvent.getValueIsAdjusting() || getSelectedValue() == null) return;
                    timer.restart();
                }
            });
            setPrototypeCellValue("thisIsAVeryLongFunctionNameIndeed()");
            setToolTipText("Selecting code and clicking here will insert\n" +
                    "a function call around the selected code");
        }
    }

}
