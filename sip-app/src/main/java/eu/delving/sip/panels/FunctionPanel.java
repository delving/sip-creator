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

package eu.delving.sip.panels;

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.FunctionCompileModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This panel provides an interface for building and testing global functions
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionPanel extends JPanel {
    private static Color LIBRARY_COLOR = new Color(255,255,200);
    private static final Pattern FUNCTION_NAME = Pattern.compile("[a-z]+[a-zA-z]*");
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 12);
    private SipModel sipModel;
    private FunctionListModel libraryListModel = new FunctionListModel();
    private JList libraryList = new MappingFunctionJList(libraryListModel);
    private FunctionListModel functionModel = new FunctionListModel();
    private JList functionList = new MappingFunctionJList(functionModel);
    private DefaultListModel factsModel = new DefaultListModel();
    private JList factsList = new JList(factsModel);
    private JTextArea inputArea = new JTextArea();
    private JTextArea docArea = new JTextArea();
    private JTextArea codeArea = new JTextArea();
    private JTextArea outputArea = new JTextArea();
    private ModelStateListener modelStateListener = new ModelStateListener();

    public FunctionPanel(SipModel sipModel) {
        super(new BorderLayout());
        this.sipModel = sipModel;
        inputArea.setFont(MONOSPACED);
        codeArea.setFont(MONOSPACED);
        docArea.setFont(MONOSPACED);
        docArea.setLineWrap(true);
        docArea.setWrapStyleWord(true);
        outputArea.setFont(MONOSPACED);
        factsList.setFont(MONOSPACED);
        libraryList.setBackground(LIBRARY_COLOR);
        add(createCenter(), BorderLayout.CENTER);
        add(createFunctionPanels(), BorderLayout.WEST);
        wireUp();
        try {
            fetchFunctionList();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JPanel createCenter() {
        JPanel center = new JPanel(new GridLayout(0, 1));
        center.add(createInputPanel());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Groovy Code", Utility.scrollVH(codeArea));
        tabs.addTab("Documentation", Utility.scrollVH(docArea));
        center.add(tabs);
        center.add(createOutputPanel());
        return center;
    }

    private void fetchFunctionList() throws IOException {
        URL functionFile = getClass().getResource("/mapping-functions.xml");
        MappingFunction.FunctionList functionList = MappingFunction.read(functionFile.openStream());
        libraryListModel.setList(functionList.functions);
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
        p.add(Utility.scrollV(functionList));
        p.add(bp, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createFunctionLibPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Library"));
        p.add(Utility.scrollV(libraryList));
        p.add(new JButton(new CopyAction()), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createInputPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(Utility.scrollVH("Input Lines", inputArea), BorderLayout.CENTER);
        p.add(Utility.scrollV("Available Facts", factsList), BorderLayout.EAST);
        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Lines"));
        p.add(Utility.scrollVH(outputArea));
        return p;
    }

    private void wireUp() {
        functionList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        functionList.getSelectionModel().addListSelectionListener(new FunctionSelection(false, functionList));
        libraryList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libraryList.getSelectionModel().addListSelectionListener(new FunctionSelection(true, libraryList));
        inputArea.setDocument(sipModel.getFunctionCompileModel().getInputDocument());
        codeArea.setDocument(sipModel.getFunctionCompileModel().getCodeDocument());
        docArea.setDocument(sipModel.getFunctionCompileModel().getDocDocument());
        outputArea.setDocument(sipModel.getFunctionCompileModel().getOutputDocument());
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(final MappingModel mappingModel) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        if (mappingModel.hasRecMapping()) {
                            functionModel.setList(mappingModel.getRecMapping().getFunctions());
                        }
                        factsModel.clear();
                        if (mappingModel.hasRecMapping()) {
                            for (String fact : mappingModel.getRecMapping().getFacts().keySet()) {
                                factsModel.addElement(String.format("  %s  ", fact));
                            }
                            factsModel.addElement("  _uniqueIdentifier   ");
                        }
                    }
                });
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(final MappingModel mappingModel, final MappingFunction function) {
                Exec.swing(new Runnable() {
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
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }
        });
        sipModel.getFunctionCompileModel().addListener(modelStateListener);
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
            final MappingFunction function = (MappingFunction) list.getSelectedValue();
            if (function == null) return;
            if (library) {
                functionList.clearSelection();
                final int index = functionModel.indexOf(function.name);
                if (index >= 0) {
                    Exec.swingLater(new Runnable() {
                        @Override
                        public void run() {
                            functionList.setSelectedIndex(index);
                            libraryList.clearSelection();
                        }
                    });
                }
            }
            else {
                libraryList.clearSelection();
            }
            docArea.setEditable(!library);
            inputArea.setEditable(!library);
            codeArea.setEditable(!library);
            outputArea.setEditable(!library);
            modelStateListener.setLibrary(this.library);
            if (modelStateListener.library) codeArea.setBackground(LIBRARY_COLOR);
            docArea.setBackground(library ? LIBRARY_COLOR : Color.WHITE);
            inputArea.setBackground(library ? LIBRARY_COLOR : Color.WHITE);
            outputArea.setBackground(library ? LIBRARY_COLOR : Color.WHITE);
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    sipModel.getFunctionCompileModel().setFunction(function);
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
            final String name = JOptionPane.showInputDialog(FunctionPanel.this, "Please enter the function name");
            if (name != null) {
                if (!FUNCTION_NAME.matcher(name).matches()) {
                    JOptionPane.showMessageDialog(FunctionPanel.this, "Sorry, the name must be of the form 'aaaaa' or 'aaaaAaaa'");
                    return;
                }
                final RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
                if (recMapping.hasFunction(name)) {
                    JOptionPane.showMessageDialog(FunctionPanel.this, "Sorry, but this function name already exists");
                    return;
                }
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        MappingFunction mappingFunction = recMapping.createFunction(name);
                        sipModel.getMappingModel().notifyFunctionChanged(mappingFunction);
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
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().getRecMapping().removeFunction(selected);
                        sipModel.getMappingModel().notifyFunctionChanged(selected);
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
            final MappingFunction selected = (MappingFunction) libraryList.getSelectedValue();
            if (selected != null) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getMappingModel().getRecMapping().addFunction(selected);
                        sipModel.getMappingModel().notifyFunctionChanged(selected);
                    }
                });
            }
        }
    }

    private class FunctionListModel extends AbstractListModel {
        private List<MappingFunction> functions = new ArrayList<MappingFunction>();
        
        public void setList(Collection<MappingFunction> functions) {
            if (!this.functions.isEmpty()) {
                int size = getSize();
                this.functions.clear();
                fireIntervalRemoved(this, 0, size);
            }
            if (functions != null) this.functions.addAll(functions);
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

        public int indexOf(String name) {
            int index = 0;
            for (MappingFunction function : functions) {
                if (function.name.equals(name)) return index;
                index++;
            }
            return -1;
        }
    }

    private class MappingFunctionJList extends JList {

        private MappingFunctionJList(FunctionListModel functionListModel) {
            super(functionListModel);
        }

        @Override
        public String getToolTipText(MouseEvent evt) {
            int index = this.locationToIndex(evt.getPoint());
            MappingFunction mappingFunction = (MappingFunction) getModel().getElementAt(index);
            return mappingFunction.getDocumentation();
        }
    }

    private class ModelStateListener implements FunctionCompileModel.Listener {
        
        private boolean library;

        public void setLibrary(boolean library) {
            this.library = library;
        }

        @Override
        public void stateChanged(final FunctionCompileModel.State state) {
            final boolean lib = this.library;
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    if (lib) {
                        codeArea.setBackground(LIBRARY_COLOR);
                    }
                    else switch (state) {
                        case ORIGINAL:
                            codeArea.setBackground(new Color(1.0f, 1.0f, 1.0f));
                            break;
                        case EDITED:
                            codeArea.setBackground(new Color(1.0f, 1.0f, 0.9f));
                            break;
                        case ERROR:
                            codeArea.setBackground(new Color(1.0f, 0.9f, 0.9f));
                            break;
                    }
                }
            });
        }
    }


}
