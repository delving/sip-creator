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

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;

/**
 * Render the record definition as a JTree
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TargetFrame extends FrameBase {
    private JTree recDefTree;
    private JTextField filterField = new JTextField();
    private JPanel treePanel;
    private OptListModel optListModel = new OptListModel();
    private JCheckBox hideAttributes = new JCheckBox("Hide Attributes");
    private JCheckBox autoFoldBox = new JCheckBox("Auto-Fold");
    private JComboBox optCombo = new JComboBox(optListModel);
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object model = recDefTree.getModel();
            if (model instanceof RecDefTreeModel) {
                RecDefTreeModel ftm = (RecDefTreeModel) model;
                ftm.setFilter(filterField.getText().trim());
            }
        }
    });

    public TargetFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.TARGET, desktop, sipModel, "Target", false);
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getPrefix() : "?"));
            }
        });
        createRecDefTree(sipModel);
        timer.setRepeats(false);
        optCombo.setPrototypeDisplayValue("anelement[withanoptinbrackets]");
        treePanel = new JPanel(new BorderLayout());
        treePanel.add(Utility.scrollVH("Record Definition", recDefTree));
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createNorthPanel(), BorderLayout.NORTH);
        content.add(treePanel, BorderLayout.CENTER);
    }

    private JPanel createNorthPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(createNorthCenterPanel(), BorderLayout.CENTER);
        p.add(createNorthEastPanel(), BorderLayout.EAST);
        return p;
    }

    private JPanel createNorthEastPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(hideAttributes);
        p.add(autoFoldBox);
        return p;
    }

    private JPanel createNorthCenterPanel() {
        JPanel fp = new JPanel(new GridLayout(1, 0));
        fp.setBorder(BorderFactory.createTitledBorder("Filter"));
        fp.add(filterField);
        JPanel op = new JPanel(new GridLayout(1, 0));
        op.setBorder(BorderFactory.createTitledBorder("Option to show"));
        op.add(optCombo);
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.add(fp);
        p.add(op);
        return p;
    }

    private void wireUp() {
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }
        });
        filterField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
                String text = filterField.getText();
                filterField.setSelectionStart(0);
                filterField.setSelectionEnd(text.length());
            }
        });
        hideAttributes.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (recDefTree.getModel().getRoot() instanceof RecDefTreeNode) {
                    RecDefTreeModel model = (RecDefTreeModel) recDefTree.getModel();
                    model.setAttributesHidden(hideAttributes.isSelected());
                    showPath((RecDefTreeNode) model.getRoot());
                }
            }
        });
        optCombo.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                OptListModel.OptChoice choice = (OptListModel.OptChoice) itemEvent.getItem();
                if (choice != null && choice.getPath() != null) {
                    RecDefTreeModel model = (RecDefTreeModel) recDefTree.getModel();
                    model.setSelectedOpt(choice.getOpt());
                    RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
                    root.showPath(recDefTree, choice.getPath());
                }
            }
        });
    }

    private class RecDefSelection implements TreeSelectionListener, Runnable {

        private Object nodeObject;

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            nodeObject = event.getPath().getLastPathComponent();
            Exec.work(this);
        }

        @Override
        public void run() {
            if (nodeObject instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) nodeObject;
                if (autoFoldBox.isSelected()) showPath(node);
                if (node.getRecDefNode().isUnmappable()) return;
                sipModel.getCreateModel().setRecDefTreeNode(node);
            }
            else {
                sipModel.getCreateModel().setRecDefTreeNode(null);
            }
        }
    }

    private void showPath(RecDefTreeNode node) {
        RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
        root.showPath(recDefTree, node.getRecDefPath().getTagPath());
    }

    private void createRecDefTree(SipModel sipModel) {
        recDefTree = new JTree(new RecDefTreeModel(FilterNode.createMessageNode("Empty"))) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = recDefTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((RecDefTreeNode) treePath.getLastPathComponent()).toHtml() : "";
            }
        };
        recDefTree.setToolTipText("?");
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.setDragEnabled(false);
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(sipModel.getNodeTransferHandler());
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refreshRecDefTreeNode(mappingModel, node);
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                refreshRecDefTreeNode(mappingModel, node);
            }

            private void refreshRecDefTreeNode(final MappingModel mappingModel, final RecDefNode node) {
                mappingModel.getRecDefTreeRoot().getRecDefTreeNode(node).fireChanged();
            }
        });
    }

    private class TreeUpdater implements Runnable {
        private String prefix;

        private TreeUpdater(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void run() {
            RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
            if (root != null) {
                RecDefTreeModel model = new RecDefTreeModel(root);
                recDefTree.setModel(model);
                model.setAttributesHidden(hideAttributes.isSelected());
                RecDef recDef = sipModel.getMappingModel().getRecMapping().getRecDefTree().getRecDef();
                optListModel.setList(recDef);
            }
            else {
                recDefTree.setModel(new RecDefTreeModel(FilterNode.createMessageNode("No record definition")));
            }
            treePanel.removeAll();
            treePanel.add(Utility.scrollVH(String.format("Record Definition for \"%s\"", prefix.toUpperCase()), recDefTree));
        }
    }

}
