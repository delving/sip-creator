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
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.FunctionPanel;
import eu.delving.sip.base.Utility;
import eu.delving.sip.menus.EditHistory;
import eu.delving.sip.model.CreateModel;
import eu.delving.sip.model.MappingCompileModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FieldMappingFrame extends FrameBase {
    private JTextArea groovyCodeArea;
    private JTextArea outputArea;
    private JEditorPane helpView;
    private ContextVarListModel contextVarModel = new ContextVarListModel();
    private JComboBox contextVarBox = new JComboBox(contextVarModel);
    private DictionaryPanel dictionaryPanel;
    private FunctionPanel functionPanel;
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
        dictionaryPanel = new DictionaryPanel(sipModel.getCreateModel());
        functionPanel = new FunctionPanel(sipModel);
        groovyCodeArea = new JTextArea(sipModel.getFieldCompileModel().getCodeDocument());
        groovyCodeArea.setFont(new Font("Monospaced", Font.BOLD, 12));
        groovyCodeArea.setTabSize(3);
        groovyCodeArea.getDocument().addUndoableEditListener(editHistory);
        groovyCodeArea.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
                editHistory.setTarget(groovyCodeArea);
            }

            @Override
            public void focusLost(FocusEvent focusEvent) {
                editHistory.setTarget(null);
            }
        });
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
        add(createTabs(), BorderLayout.CENTER);
    }

    private JComponent createTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Field", createCodeOutputPanel());
        tabs.addTab("Functions", functionPanel);
        tabs.addTab("Dictionary", dictionaryPanel);
        tabs.addTab("Help", Utility.scroll(helpView));
        return tabs;
    }

    private JPanel createCodeOutputPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createCodePanel());
        p.add(createOutputPanel());
        return p;
    }

    private JPanel createCodePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Groovy Code"));
        JPanel north = new JPanel(new BorderLayout());
        north.add(new JLabel("Context variables:"), BorderLayout.WEST);
        north.add(contextVarBox, BorderLayout.CENTER);
        north.add(new JButton(new RevertAction()), BorderLayout.EAST);
        p.add(north, BorderLayout.NORTH);
        p.add(Utility.scroll(groovyCodeArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output Record"));
        p.add(Utility.scroll(outputArea), BorderLayout.CENTER);
        p.add(new JLabel("Note: URLs can be launched by double-clicking them.", JLabel.CENTER), BorderLayout.SOUTH);
        return p;
    }

    private void wireUp() {
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void statsTreeNodeSet(CreateModel createModel) {
            }

            @Override
            public void recDefTreeNodeSet(CreateModel createModel) {
            }

            @Override
            public void nodeMappingSet(CreateModel createModel) {
                contextVarModel.setList(createModel.getNodeMapping());
                sipModel.getFieldCompileModel().setNodeMapping(createModel.getNodeMapping());
                groovyCodeArea.setEditable(createModel.getNodeMapping() != null && createModel.getNodeMapping().isUserCodeEditable());
            }

            @Override
            public void nodeMappingChanged(CreateModel createModel) {
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

    private class ContextVarListModel extends AbstractListModel implements ComboBoxModel {
        private List<String> vars = new ArrayList<String>();
        private String selected;

        public void setList(NodeMapping nodeMapping) {
            int size = getSize();
            vars.clear();
            if (size > 0) {
                fireIntervalRemoved(this, 0, size);
            }
            if (nodeMapping != null) vars.addAll(nodeMapping.getContextVariables());
            size = getSize();
            if (size > 0) {
                selected = vars.get(0);
                fireIntervalAdded(this, 0, size);
            }
        }

        @Override
        public int getSize() {
            return vars.size();
        }

        @Override
        public Object getElementAt(int i) {
            return vars.get(i);
        }

        @Override
        public void setSelectedItem(Object item) {
            this.selected = (String) item;
        }

        @Override
        public Object getSelectedItem() {
            return selected;
        }
    }

    private class ModelStateListener implements MappingCompileModel.Listener {

        @Override
        public void stateChanged(final MappingCompileModel.State state) {
            Exec.swing(new Runnable() {

                @Override
                public void run() {
                    switch (state) {
                        case ORIGINAL:
                            editHistory.discardAllEdits();
                            // fall through
                        case SAVED:
                            groovyCodeArea.setBackground(new Color(1.0f, 1.0f, 1.0f));
                            break;
                        case EDITED:
                            groovyCodeArea.setBackground(new Color(1.0f, 1.0f, 0.9f));
                            break;
                        case ERROR:
                            groovyCodeArea.setBackground(new Color(1.0f, 0.9f, 0.9f));
                            break;
                    }
                }
            });
        }
    }
    
    private class RevertAction extends AbstractAction {
        private RevertAction() {
            super("Revert to Original");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            int answer = JOptionPane.showConfirmDialog(FieldMappingFrame.this, "Discard edited code and revert to the original?", "", JOptionPane.OK_CANCEL_OPTION);
            if (answer == JOptionPane.OK_OPTION) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().revertToOriginal();
                        sipModel.getFieldCompileModel().setNodeMapping(sipModel.getCreateModel().getNodeMapping());
                    }
                });
            }
        }
    }

}
