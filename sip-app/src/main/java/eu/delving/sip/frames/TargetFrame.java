/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.metadata.*;
import eu.delving.sip.base.*;
import eu.delving.sip.menus.ShowOptionMenu;
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
    private JCheckBoxMenuItem hideAttributes = new JCheckBoxMenuItem("Hide Attributes");
    private JCheckBoxMenuItem autoFold = new JCheckBoxMenuItem("Auto-Fold");
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
                Exec.run(new TreeUpdater(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getPrefix() : "?"));
            }
        });
        createRecDefTree(sipModel);
        timer.setRepeats(false);
        recDefTree.setDropMode(DropMode.ON);
        treePanel = new JPanel(new BorderLayout());
        treePanel.add(SwingHelper.scrollVH("Record Definition", recDefTree));
        ShowOptionMenu showOptionMenu = new ShowOptionMenu(new ShowOptionMenu.Listener() {
            @Override
            public void optSelected(OptList.Opt opt) {
                RecDefTreeModel model = (RecDefTreeModel) recDefTree.getModel();
                model.setSelectedOpt(opt);
                RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
                Path optPath = opt.parent.path.parent().child(opt.parent.path.peek().withOpt(opt.key));
                root.showPath(recDefTree, optPath);
            }
        });
        sipModel.getMappingModel().addSetListener(showOptionMenu);
        JMenuBar bar = new JMenuBar();
        bar.add(showOptionMenu);
        JMenu expand = new JMenu("View");
        expand.add(new ExpandRootAction());
        expand.add(hideAttributes);
        expand.add(autoFold);
        bar.add(expand);
        setJMenuBar(bar);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createNorthPanel(), BorderLayout.NORTH);
        content.add(treePanel, BorderLayout.CENTER);
    }

    private JPanel createNorthPanel() {
        JPanel p = new JPanel(new GridLayout(1, 0));
        p.setBorder(BorderFactory.createTitledBorder("Filter"));
        p.add(filterField);
        return p;
    }

    private void wireUp() {
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                switch (transition) {
                    case COMPLETE_TO_COMPLETE:
                    case NOTHING_TO_COMPLETE:
                        Exec.run(new Swing() {
                            @Override
                            public void run() {
                                recDefTree.clearSelection();
                            }
                        });
                        break;
                }
            }
        });
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
    }

    private class RecDefSelection implements TreeSelectionListener, Work {

        private Object nodeObject;

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            TreePath path = recDefTree.getSelectionPath();
            nodeObject = path != null ? path.getLastPathComponent() : null;
            Exec.run(this);
        }

        @Override
        public void run() {
            if (nodeObject != null && nodeObject instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) nodeObject;
                if (autoFold.isSelected()) showPath(node);
                if (node.getRecDefNode().isUnmappable()) return;
                sipModel.getCreateModel().setTarget(node);
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
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
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

    private class ExpandRootAction extends AbstractAction {

        private ExpandRootAction() {
            super("Expand All");
            putValue(
                    Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(KeyEvent.VK_E, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
            );
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
            if (root != null) showPath(root);
        }
    }

    private class TreeUpdater implements Swing {
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
            }
            else {
                recDefTree.setModel(new RecDefTreeModel(FilterNode.createMessageNode("No record definition")));
            }
            treePanel.removeAll();
            treePanel.add(SwingHelper.scrollVH(String.format("Record Definition for \"%s\"", prefix.toUpperCase()), recDefTree));
        }
    }

}
