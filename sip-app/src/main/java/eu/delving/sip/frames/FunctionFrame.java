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
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.FunctionCompileModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static eu.delving.sip.base.KeystrokeHelper.*;
import static eu.delving.sip.base.SwingHelper.*;

/**
 * This panel provides an interface for building global functions in the context of tests which demonstrate how
 * input is turned into output using the function.  A list of library functions is gathered here as well, so that
 * frequently used functions can be copied in and modified.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionFrame extends FrameBase {
    private static final Pattern FUNCTION_NAME = Pattern.compile("[a-z]+[a-zA-z]*");
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 14);
    private final Action UNDO_ACTION = new UndoAction();
    private final Action REDO_ACTION = new RedoAction();
    private FunctionListModel libraryListModel = new FunctionListModel();
    private JList libraryList = new MappingFunctionJList(libraryListModel);
    private FunctionListModel functionModel = new FunctionListModel();
    private JList functionList = new MappingFunctionJList(functionModel);
    private DefaultListModel factsModel = new DefaultListModel();
    private JList factsList = new JList(factsModel);
    private JTextArea inputArea;
    private JTextArea docArea;
    private JTextArea codeArea;
    private JTextArea outputArea;
    private ModelStateListener modelStateListener = new ModelStateListener();
    private UndoManager undoManager = new UndoManager();

    public FunctionFrame(SipModel sipModel) {
        super(Which.FUNCTIONS, sipModel, "Functions");
        inputArea = new JTextArea(sipModel.getFunctionCompileModel().getInputDocument());
        inputArea.setFont(MONOSPACED);
        docArea = new JTextArea(sipModel.getFunctionCompileModel().getDocDocument());
        docArea.setFont(MONOSPACED);
        docArea.setLineWrap(true);
        docArea.setWrapStyleWord(true);
        codeArea = new JTextArea(sipModel.getFunctionCompileModel().getCodeDocument());
        codeArea.setFont(MONOSPACED);
        outputArea = new JTextArea(sipModel.getFunctionCompileModel().getOutputDocument());
        outputArea.setFont(MONOSPACED);
        factsList.setFont(MONOSPACED);
        libraryList.setFont(MONOSPACED);
        libraryList.setBackground(UNEDITABLE_BG);
        MappingFunction function = new MappingFunction("thisIsAVeryLongFunctionNameIndeed()");
        FunctionEntry entry = new FunctionEntry(function, false);
        libraryList.setPrototypeCellValue(entry);
        functionList.setFont(MONOSPACED);
        wireUp();
        attachAccelerator(UNDO_ACTION, codeArea);
        attachAccelerator(REDO_ACTION, codeArea);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createCenter(), BorderLayout.CENTER);
        content.add(createFunctionPanels(), BorderLayout.WEST);
    }

    private JPanel createCenter() {
        JPanel center = new JPanel(new GridLayout(0, 1));
        center.add(createCodeDoc());
        center.add(createInputOutput());
        return center;
    }

    private JTabbedPane createCodeDoc() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Groovy Code", createCodePanel());
        tabs.addTab("Documentation", scrollVH(docArea));
        return tabs;
    }

    private JPanel createCodePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scrollVH("Groovy Code", codeArea), BorderLayout.CENTER);
        p.add(createSidePanel(), BorderLayout.EAST);
        return p;
    }

    private JPanel createSidePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scrollV("Available Facts", factsList), BorderLayout.CENTER);
        p.add(createBesideCode(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createBesideCode() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(createActionButton(UNDO_ACTION));
        p.add(createActionButton(REDO_ACTION));
        return p;
    }

    private JButton createActionButton(Action action) {
        KeyStroke stroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
        JButton button = new JButton(action);
        button.setText(button.getText() + " " + KeyEvent.getKeyModifiersText(stroke.getModifiers()) + KeyEvent.getKeyText(stroke.getKeyCode()));
        return button;
    }

    private JPanel createInputOutput() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(scrollVH("Input Lines", inputArea));
        p.add(scrollVH("Output Lines", outputArea));
        return p;
    }

    private JPanel createFunctionPanels() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(createFunctionsPanel());
        p.add(createFunctionLibPanel());
        return p;
    }

    private JPanel createFunctionsPanel() {
        JPanel bp = new JPanel(new GridLayout(0, 1));
        bp.add(new JButton(new CreateAction()));
        bp.add(new JButton(new RemoveAction()));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Functions"));
        p.add(scrollV(functionList));
        p.add(bp, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createFunctionLibPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Library"));
        p.add(scrollV(libraryList));
        p.add(new JButton(new CopyAction()), BorderLayout.SOUTH);
        return p;
    }

    private void wireUp() {
        handleEnablement();
        functionList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        functionList.getSelectionModel().addListSelectionListener(new FunctionSelection(false, functionList));
        libraryList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libraryList.getSelectionModel().addListSelectionListener(new FunctionSelection(true, libraryList));
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
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(final MappingModel mappingModel) {
                fetchFunctionList();
                exec(new Swing() {
                    @Override
                    public void run() {
                        factsModel.clear();
                        if (mappingModel.hasRecMapping()) {
                            functionModel.setList(mappingModel.getRecMapping().getFunctions());
                            for (String fact : mappingModel.getRecMapping().getFacts().keySet()) {
                                factsModel.addElement(String.format("  %s  ", fact));
                            }
                            factsModel.addElement("  _uniqueIdentifier   ");
                        }
                        codeArea.setText("");
                        outputArea.setText("");
                        inputArea.setText("");
                    }
                });
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
                setFrameLocked(locked);
            }

            @Override
            public void functionChanged(final MappingModel mappingModel, final MappingFunction function) {
                exec(new Swing() {
                    @Override
                    public void run() {
                        if (mappingModel.hasRecMapping()) {
                            if (mappingModel.getRecMapping().getFunctions().size() != functionModel.getSize()) {
                                functionModel.setList(mappingModel.getRecMapping().getFunctions());
                            }
                        }
                        int newIndex = functionModel.indexOf(function.name);
                        int currentIndex = functionList.getSelectedIndex();
                        if (newIndex != currentIndex) functionList.setSelectedIndex(newIndex);
                    }
                });
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
        sipModel.getFunctionCompileModel().addListener(modelStateListener);
    }

    private void fetchFunctionList() {
        try {
            final List<FunctionEntry> entries = new ArrayList<FunctionEntry>();
            URL functionFile = getClass().getResource("/templates/global-mapping-functions.xml");
            final MappingFunction.FunctionList functionList = MappingFunction.read(functionFile.openStream());
            for (MappingFunction function : functionList.functions) entries.add(new FunctionEntry(function, false));
            SortedSet<MappingFunction> hintFunctions = sipModel.getMappingHintsModel().getFunctions();
            if (hintFunctions != null) {
                for (MappingFunction function : hintFunctions) entries.add(new FunctionEntry(function, false));
            }
            List<MappingFunction> recDefFunctions = sipModel.getMappingModel().getRecMapping().getRecDefTree().getRecDef().functions;
            if (recDefFunctions != null) {
                for (MappingFunction function : recDefFunctions) entries.add(new FunctionEntry(function, true));
            }
            exec(new Swing() {
                @Override
                public void run() {
                    libraryListModel.setList(entries);
                }
            });
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleEnablement() {
        UNDO_ACTION.setEnabled(undoManager.canUndo());
        REDO_ACTION.setEnabled(undoManager.canRedo());
    }

    private class FunctionSelection implements ListSelectionListener {

        private final boolean library;
        private final JList list;

        private FunctionSelection(boolean library, JList list) {
            this.library = library;
            this.list = list;
        }

        @Override
        public void valueChanged(final ListSelectionEvent listSelectionEvent) {
            if (listSelectionEvent.getValueIsAdjusting()) return;
            final FunctionEntry entry = (FunctionEntry) list.getSelectedValue();
            if (entry == null) return;
            if (library) {
                functionList.clearSelection();
                final int index = functionModel.indexOf(entry.mappingFunction.name);
                if (index >= 0) {
                    functionList.setSelectedIndex(index);
                    libraryList.clearSelection();
                }
            }
            else {
                libraryList.clearSelection();
            }
            modelStateListener.setLibrary(this.library);
            setEditable(codeArea, !library);
            setEditable(docArea, !library);
            setEditable(inputArea, !library);
            setEditable(outputArea, !library);
            exec(new Work() {
                @Override
                public void run() {
                    sipModel.getFunctionCompileModel().setFunction(entry.mappingFunction);
                    exec(new Swing() {
                        @Override
                        public void run() {
                            undoManager.discardAllEdits();
                            handleEnablement();
                        }
                    });
                }

                @Override
                public Job getJob() {
                    return Job.FUNCTION_SELECT;
                }
            });
        }
    }

    private class CreateAction extends AbstractAction {

        private CreateAction() {
            super("Create a new function");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            final String name = sipModel.getFeedback().ask("Please enter the function name");
            if (name != null) {
                if (!FUNCTION_NAME.matcher(name).matches()) {
                    sipModel.getFeedback().alert("Sorry, the name must be of the form 'aaaaa' or 'aaaaAaaa'");
                    return;
                }
                final RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
                if (recMapping.hasFunction(name)) {
                    sipModel.getFeedback().alert("Sorry, but this function name already exists");
                    return;
                }
                exec(new Work() {
                    @Override
                    public void run() {
                        MappingFunction mappingFunction = recMapping.createFunction(name);
                        sipModel.getMappingModel().notifyFunctionChanged(mappingFunction);
                    }

                    @Override
                    public Job getJob() {
                        return Job.NOTIFY_FUNCTION_CHANGED;
                    }
                });
            }
        }
    }

    private class RemoveAction extends AbstractAction {
        private RemoveAction() {
            super("Remove selected function");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            final MappingFunction selected = (MappingFunction) functionList.getSelectedValue();
            if (selected != null) {
                exec(new Work() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().getRecMapping().removeFunction(selected);
                        sipModel.getMappingModel().notifyFunctionChanged(selected);
                    }

                    @Override
                    public Job getJob() {
                        return Job.FUNCTION_REMOVE;
                    }
                });
            }
        }
    }

    private class CopyAction extends AbstractAction {
        private CopyAction() {
            super("Copy selected function");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            final FunctionEntry selected = (FunctionEntry) libraryList.getSelectedValue();
            if (selected != null) {
                if (selected.fromRecDef) {
                    sipModel.getFeedback().alert("Functions from the record definition need not be copied");
                }
                else {
                    exec(new Work() {
                        @Override
                        public void run() {
                            sipModel.getMappingModel().getRecMapping().addFunction(selected.mappingFunction);
                            sipModel.getMappingModel().notifyFunctionChanged(selected.mappingFunction);
                        }

                        @Override
                        public Job getJob() {
                            return Job.FUNCTION_COPY_FROM_LIBRARY;
                        }
                    });
                }
            }
        }
    }

    private class UndoAction extends AbstractAction {
        private UndoAction() {
            configAction(this, "Undo", null, MENU_Z);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (undoManager.canUndo()) undoManager.undo();
        }
    }

    private class RedoAction extends AbstractAction {
        private RedoAction() {
            configAction(this, "Redo", null, SH_MENU_Z);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (undoManager.canRedo()) undoManager.redo();
        }
    }

    private class FunctionListModel extends AbstractListModel {
        private List<FunctionEntry> entries = new ArrayList<FunctionEntry>();

        public void setList(List<FunctionEntry> functions) {
            if (!this.entries.isEmpty()) {
                int size = getSize();
                this.entries.clear();
                fireIntervalRemoved(this, 0, size);
            }
            if (functions != null) {
                Collections.sort(functions);
                this.entries.addAll(functions);
            }
            if (!this.entries.isEmpty()) fireIntervalAdded(this, 0, getSize());
        }

        public void setList(Collection<MappingFunction> functions) {
            List<FunctionEntry> fresh = new ArrayList<FunctionEntry>();
            for (MappingFunction function : functions) fresh.add(new FunctionEntry(function, false));
            setList(fresh);
        }

        @Override
        public int getSize() {
            return entries.size();
        }

        @Override
        public Object getElementAt(int i) {
            if (i < 0) return null;
            return entries.get(i);
        }

        public int indexOf(String name) {
            int index = 0;
            for (FunctionEntry entry : entries) {
                if (entry.mappingFunction.name.equals(name)) return index;
                index++;
            }
            return -1;
        }
    }

    private class FunctionEntry implements Comparable<FunctionEntry> {
        final MappingFunction mappingFunction;
        final boolean fromRecDef;

        private FunctionEntry(MappingFunction mappingFunction, boolean fromRecDef) {
            this.mappingFunction = mappingFunction;
            this.fromRecDef = fromRecDef;
        }

        public String toString() {
            return mappingFunction.toString();
        }

        @Override
        public int compareTo(FunctionEntry o) {
            return mappingFunction.name.compareTo(o.mappingFunction.name);
        }
    }

    private class FunctionCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            FunctionEntry functionEntry = (FunctionEntry) value;
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            component.setForeground(functionEntry.fromRecDef ? Color.GRAY : Color.BLACK);
            return component;
        }
    }

    private class MappingFunctionJList extends JList {

        private MappingFunctionJList(FunctionListModel functionListModel) {
            super(functionListModel);
            setCellRenderer(new FunctionCellRenderer());
        }

        @Override
        public String getToolTipText(MouseEvent evt) {
            int index = this.locationToIndex(evt.getPoint());
            FunctionEntry entry = (FunctionEntry) getModel().getElementAt(index);
            return entry == null ? "" : entry.fromRecDef ? "Built in to Record Definition" : entry.mappingFunction.getDocumentation();
        }
    }

    private class ModelStateListener implements FunctionCompileModel.Listener {

        private boolean library;

        public void setLibrary(boolean library) {
            this.library = library;
        }

        @Override
        public void stateChanged(final CompileState state) {
            final boolean lib = this.library;
            exec(new Swing() {
                @Override
                public void run() {
                    if (state == CompileState.ORIGINAL) undoManager.discardAllEdits();
                    if (lib) {
                        setEditable(codeArea, false);
                    }
                    else {
                        state.setBackgroundOf(codeArea);
                    }
                }
            });
        }
    }


}
