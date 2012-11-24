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
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.menus.ShowOptionMenu;
import eu.delving.sip.model.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.*;

import static eu.delving.sip.base.KeystrokeHelper.MENU_E;
import static eu.delving.sip.base.KeystrokeHelper.configAction;
import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * Render the record definition as a tree, with a filter function for navigating in large trees.  There are also
 * menus attached to this frame which can be used to open parts of the record definition (related to opt-lists)
 * which are normally hidden so that something can be mapped there.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TargetFrame extends FrameBase {
    public static final FilterNode EMPTY_NODE = FilterNode.createMessageNode("No record definition");
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

    public TargetFrame(SipModel sipModel) {
        super(Which.TARGET, sipModel, "Target");
        createRecDefTree(sipModel);
        timer.setRepeats(false);
        recDefTree.setDropMode(DropMode.ON);
        treePanel = new JPanel(new BorderLayout());
        treePanel.add(scrollVH("Record Definition", recDefTree));
        ShowOptionMenu showOptionMenu = new ShowOptionMenu(sipModel, new ShowOptionMenu.Listener() {
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
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                exec(new TreeUpdater(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getPrefix() : "?"));
            }
        });
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                switch (transition) {
                    case COMPLETE_TO_COMPLETE:
                    case NOTHING_TO_COMPLETE:
                        exec(new Swing() {
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
        recDefTree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) return;
                TreePath path = recDefTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                if (!path.equals(recDefTree.getSelectionPath())) {
                    recDefTree.setSelectionPath(path);
                }
                RecDefTreeNode node = (RecDefTreeNode) recDefTree.getSelectionPath().getLastPathComponent();
                if (!node.getRecDefNode().isDuplicatePossible()) return;
                Rectangle rect = recDefTree.getUI().getPathBounds(recDefTree, path);
                if (rect != null && rect.contains(e.getX(), e.getY())) {
                    JPopupMenu menu = new JPopupMenu();
                    menu.add(new DuplicateElementAction());
                    menu.show(recDefTree, rect.x + rect.width, rect.y + rect.height / 2);
                }
            }
        });
    }

    private class RecDefSelection implements TreeSelectionListener, Work {

        private RecDefTreeNode recDefTreeNode;

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            TreePath path = recDefTree.getSelectionPath();
            if (path == null) return;
            if (path.getLastPathComponent() instanceof RecDefTreeNode) {
                recDefTreeNode = (RecDefTreeNode) path.getLastPathComponent();
                exec(this);
            }
        }

        @Override
        public void run() {
            if (autoFold.isSelected()) showPath(recDefTreeNode);
            if (recDefTreeNode.getRecDefNode().isUnmappable()) return;
            sipModel.getCreateModel().setTarget(recDefTreeNode);
        }

        @Override
        public Job getJob() {
            return Job.SELECT_REC_DEF_SET_TARGET;
        }
    }

    private void showPath(RecDefTreeNode node) {
        RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
        root.showPath(recDefTree, node.getRecDefPath().getTagPath());
    }

    private void createRecDefTree(SipModel sipModel) {
        recDefTree = new JTree(new RecDefTreeModel(EMPTY_NODE)) {
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
            public void lockChanged(MappingModel mappingModel, boolean locked) {
                setFrameLocked(locked);
            }

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

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
                mappingModel.getRecDefTreeRoot().getRecDefTreeNode(node).fireChanged();
            }

            private void refreshRecDefTreeNode(final MappingModel mappingModel, final RecDefNode node) {
                mappingModel.getRecDefTreeRoot().getRecDefTreeNode(node).fireChanged();
            }
        });
    }

    private class ExpandRootAction extends AbstractAction {

        private ExpandRootAction() {
            configAction(this, "Expand what is mapped", null, MENU_E);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
            if (root != null) showPopulated(root);
        }

        public void showPopulated(final RecDefTreeNode node) {
            Timer timer = new Timer(30, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    boolean pathShouldShow = node.getRecDefNode().isPopulated();
                    if (pathShouldShow) {
                        if (!recDefTree.isExpanded(node.getRecDefPath())) recDefTree.expandPath(node.getRecDefPath());
                    }
                    else if (!recDefTree.isCollapsed(node.getRecDefPath())) {
                        recDefTree.collapsePath(node.getRecDefPath());
                    }
                    for (RecDefTreeNode sub : node.getChildren()) if (!sub.getRecDefNode().isAttr()) showPopulated(sub);
                }
            });
            timer.setRepeats(false);
            timer.start();
        }


    }

    private class DuplicateElementAction extends AbstractAction implements Work {

        private RecDefTreeNode recDefTreeNode;
        private DynOpt dynOpt;

        private DuplicateElementAction() {
            super("Create duplicate element");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TreePath path = recDefTree.getSelectionPath();
            if (path == null) return;
            if (path.getLastPathComponent() instanceof RecDefTreeNode) {
                recDefTreeNode = (RecDefTreeNode) path.getLastPathComponent();
                if (!recDefTreeNode.getRecDefNode().isDuplicatePossible()) return;
                String answer = sipModel.getFeedback().ask("Please enter a discriminator for the new element");
                if (answer == null) return;
                answer = answer.trim();
                for (RecDefTreeNode node : recDefTreeNode.getParentNode().getChildren()) {
                    if (!node.getRecDefNode().getTag().withOpt(null).equals(recDefTreeNode.getRecDefNode().getTag().withOpt(null))) continue;
                    DynOpt existingDynOpt = node.getRecDefNode().getDynOpt();
                    if (existingDynOpt == null) continue;
                    if (answer.equals(existingDynOpt.value)) {
                        sipModel.getFeedback().alert(String.format("The name '%s' already exists", answer));
                        return;
                    }
                }
                dynOpt = new DynOpt();
                dynOpt.path = recDefTreeNode.getRecDefPath().getTagPath();
                dynOpt.value = answer;
                exec(this);
            }
        }

        @Override
        public void run() {
            recDefTreeNode = recDefTreeNode.createDynOptSibling(dynOpt);
            sipModel.exec(new Swing() {
                @Override
                public void run() {
                    recDefTree.setSelectionPath(recDefTreeNode.getRecDefPath());
                    showPath(recDefTreeNode);
                }
            });
        }

        @Override
        public Job getJob() {
            return Job.DUPLICATE_ELEMENT;
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
                recDefTree.setModel(new RecDefTreeModel(EMPTY_NODE));
            }
            treePanel.removeAll();
            treePanel.add(scrollVH(String.format("Record Definition for \"%s\"", prefix.toUpperCase()), recDefTree));
            treePanel.validate();
        }
    }

}
