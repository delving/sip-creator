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

package eu.delving.sip.base;

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This panel provides an interface for building and testing global functions
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionPanel extends JPanel {
    private static final Pattern FUNCTION_NAME = Pattern.compile("[a-z]+[a-zA-z]*");
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 16);
    private SipModel sipModel;
    private FunctionListModel functionListModel = new FunctionListModel();
    private JList functionList;
    private DefaultListModel factsModel = new DefaultListModel();
    private JList factsList = new JList(factsModel);
    private JTextArea inputArea = new JTextArea();
    private JTextArea docArea = new JTextArea();
    private JTextArea codeArea = new JTextArea();
    private JTextArea outputArea = new JTextArea();

    public FunctionPanel(SipModel sipModel) {
        super(new BorderLayout());
        functionList = new JList(functionListModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int index = functionList.locationToIndex(evt.getPoint());
                MappingFunction mappingFunction = (MappingFunction) functionListModel.getElementAt(index);
                return (mappingFunction.documentation == null ? "No documentation" : mappingFunction.documentation);
            }

        };
        inputArea.setFont(MONOSPACED);
        codeArea.setFont(MONOSPACED);
        docArea.setFont(MONOSPACED);
        outputArea.setFont(MONOSPACED);
        factsList.setFont(MONOSPACED);
        this.sipModel = sipModel;
        JPanel center = new JPanel(new GridLayout(0, 1));
        center.add(createInputPanel());
        center.add(createCodePanel());
        center.add(createOutputPanel());
        add(center, BorderLayout.CENTER);
        add(createListPanel(), BorderLayout.WEST);
        wireUp();
    }

    private void wireUp() {
        functionList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        functionList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                if (listSelectionEvent.getValueIsAdjusting()) return;
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        MappingFunction function = (MappingFunction) functionList.getSelectedValue();
                        if (function != null) sipModel.getFunctionCompileModel().setFunction(function);
                    }
                });
            }
        });
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
                        functionListModel.setList(mappingModel.getRecMapping());
                        factsModel.clear();
                        if (mappingModel.hasRecMapping()) {
                            for (String fact : mappingModel.getRecMapping().getFacts().keySet()) {
                                factsModel.addElement(String.format("  %s  ", fact));
                            }
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
                        if (mappingModel.getRecMapping().getFunctions().size() != functionListModel.getSize()) {
                            functionListModel.setList(mappingModel.getRecMapping());
                        }
                        int newIndex = functionListModel.indexOf(function.name);
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
        sipModel.getFunctionCompileModel().addListener(new ModelStateListener());
    }

    private JPanel createListPanel() {
        JPanel bp = new JPanel(new GridLayout(0, 1));
        bp.add(new JButton(new CreateAction()));
        bp.add(new JButton(new RemoveAction()));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Functions"));
        p.add(Utility.scrollV(functionList));
        p.add(bp, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createInputPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(Utility.scrollVH("Input Lines", inputArea), BorderLayout.CENTER);
        p.add(Utility.scrollV("Available Facts", factsList), BorderLayout.EAST);
        return p;
    }
    
    private JPanel createCodePanel() {
        JPanel p = new JPanel(new GridLayout(1,0));
        p.setBorder(BorderFactory.createTitledBorder("Function Code"));
        p.add(Utility.scrollVH("Function Code", codeArea));
        p.add(Utility.scrollV("Documentation", docArea));
        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Lines"));
        p.add(Utility.scrollVH(outputArea));
        return p;
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

    private class FunctionListModel extends AbstractListModel {
        private List<MappingFunction> functions = new ArrayList<MappingFunction>();

        public void setList(RecMapping recMapping) {
            if (!functions.isEmpty()) {
                int size = getSize();
                functions.clear();
                fireIntervalRemoved(this, 0, size);
            }
            if (recMapping != null) functions.addAll(recMapping.getFunctions());
            if (!functions.isEmpty()) fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return functions.size();
        }

        @Override
        public Object getElementAt(int i) {
            return functions.get(i);
        }

        public boolean contains(String name) {
            return indexOf(name) >= 0;
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

    private class ModelStateListener implements FunctionCompileModel.Listener {

        @Override
        public void stateChanged(final FunctionCompileModel.State state) {
            Exec.swing(new Runnable() {

                @Override
                public void run() {
                    switch (state) {
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
