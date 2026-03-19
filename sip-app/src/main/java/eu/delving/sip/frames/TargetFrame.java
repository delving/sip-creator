/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.metadata.*;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static eu.delving.sip.base.KeystrokeHelper.*;
import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * Render the record definition as a tree, with a filter function for navigating in large trees.  There are also
 * menus attached to this frame which can be used to open parts of the record definition (related to opt-lists)
 * which are normally hidden so that something can be mapped there.
 *
 *
 */

public class TargetFrame extends FrameBase {
    private final static String CREATE_DUPLICATE_ELEMENT = "Create duplicate element";
    private final static String REMOVE_DUPLICATE_ELEMENT = "Remove duplicate element";
    public static final FilterNode EMPTY_NODE = FilterNode.createMessageNode("No record definition");
    private JTree recDefTree;
    private JTree templatesTree;
    private JTextField filterField = new JTextField();
    private JTabbedPane tabbedPane;
    private JPanel treePanel;
    private JPanel templatesPanel;
    private JCheckBoxMenuItem hideAttributes = new JCheckBoxMenuItem("Hide Attributes");
    private JCheckBoxMenuItem autoFold = new JCheckBoxMenuItem("Auto-Fold");
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object model = recDefTree.getModel();
            if (model instanceof RecDefTreeModel) {
                RecDefTreeModel ftm = (RecDefTreeModel) model;
                String sought = filterField.getText().trim();
                ftm.setFilter(sought);
                if (!sought.isEmpty()) {
                    showPath((RecDefTreeNode) ftm.getRoot());
                }
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
        templatesTree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("No templates")));
        templatesTree.setCellRenderer(new TemplateTreeRenderer());
        templatesPanel = new JPanel(new BorderLayout());
        templatesPanel.add(scrollVH("Templates", templatesTree));
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Elements", treePanel);
        tabbedPane.addTab("Templates", templatesPanel);
        JMenu view = new JMenu("View");
        view.add(new ExpandRootAction());
        view.add(hideAttributes);
        view.add(autoFold);
        JMenuBar bar = new JMenuBar();
        bar.add(view);
        setJMenuBar(bar);
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createNorthPanel(), BorderLayout.NORTH);
        content.add(tabbedPane, BorderLayout.CENTER);
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
                    DuplicateElementAction duplicateElementAction = new DuplicateElementAction(recDefTree);
                    if(duplicateElementAction.isValid()) {
                        JPopupMenu menu = new JPopupMenu();
                        menu.add(duplicateElementAction);
                        menu.show(recDefTree, e.getX(), e.getY());
                    }
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
        recDefTree = new JTree(new RecDefTreeModel(EMPTY_NODE));
//        {
//            @Override
//            public String getToolTipText(MouseEvent evt) {
//                TreePath treePath = recDefTree.getPathForLocation(evt.getX(), evt.getY());
//                return treePath != null ? ((RecDefTreeNode) treePath.getLastPathComponent()).toHtml() : "";
//            }
//        };
//        recDefTree.setToolTipText("?");
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

    private RecDefTreeNode getRecDefTreeNode(JTree recDefTree) {
        TreePath path = recDefTree.getSelectionPath();
        if (path != null && path.getLastPathComponent() instanceof RecDefTreeNode) {
            return (RecDefTreeNode) path.getLastPathComponent();
        }
        return null;
    }

    private String createDuplicateActionName(JTree recDefTree) {
        RecDefTreeNode recDefTreeNode = getRecDefTreeNode(recDefTree);
        if (recDefTreeNode != null) {
            RecDefNode recDefNode = recDefTreeNode.getRecDefNode();
            if (recDefNode.getDynOpt() != null) {
                return REMOVE_DUPLICATE_ELEMENT;
            } else if(recDefNode.isDuplicatePossible()) {
                return CREATE_DUPLICATE_ELEMENT;
            }
        }
        return null;
    }

    private class DuplicateElementAction extends AbstractAction implements Work {

        private final TreePath path;
        private final String name;
        private RecDefTreeNode recDefTreeNode;
        private DynOpt dynOpt;

        private DuplicateElementAction(JTree recDefTree) {
            super(createDuplicateActionName(recDefTree));
            path = recDefTree.getSelectionPath();
            name = getValue(NAME).toString(); // get the name from the action
            recDefTreeNode = getRecDefTreeNode(recDefTree);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (CREATE_DUPLICATE_ELEMENT.equals(name)) {
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
            } else if(REMOVE_DUPLICATE_ELEMENT.equals(name)) {
                recDefTreeNode.getRecDefNode().remove();
                RecDefTreeModel treeModel = (RecDefTreeModel) recDefTree.getModel();
                treeModel.removeNode((FilterNode) path.getLastPathComponent());
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

        public boolean isValid() {
            return name != null;
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
            updateTemplatesTree(prefix);
        }
    }

    private void updateTemplatesTree(String prefix) {
        MappingModel mappingModel = sipModel.getMappingModel();
        if (!mappingModel.hasRecMapping()) {
            templatesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No templates")));
            return;
        }
        RecDef recDef = mappingModel.getRecMapping().getRecDefTree().getRecDef();
        List<RecDef.Elem> templates = recDef.templates;
        if (templates == null || templates.isEmpty()) {
            templatesTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No templates defined")));
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                String.format("Templates (%s) — %d", prefix.toUpperCase(), templates.size()));
        for (RecDef.Elem template : templates) {
            root.add(buildTemplateNode(template));
        }
        templatesTree.setModel(new DefaultTreeModel(root));
        templatesPanel.removeAll();
        templatesPanel.add(scrollVH(String.format("Templates for \"%s\"", prefix.toUpperCase()), templatesTree));
        templatesPanel.validate();
    }

    private DefaultMutableTreeNode buildTemplateNode(RecDef.Elem elem) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(elem);
        // Use subattrs (raw XStream data) since templates are not resolved
        if (elem.subattrs != null) {
            for (RecDef.Attr attr : elem.subattrs) {
                node.add(new DefaultMutableTreeNode(attr));
            }
        }
        // Also show resolved attrs if available (for templates that were resolved)
        if (elem.attrList != null && !elem.attrList.isEmpty() && (elem.subattrs == null || elem.subattrs.isEmpty())) {
            for (RecDef.Attr attr : elem.attrList) {
                node.add(new DefaultMutableTreeNode(attr));
            }
        }
        // Use subelements (raw XStream data) since templates are not resolved via resolve()
        if (elem.subelements != null) {
            for (RecDef.Elem child : elem.subelements) {
                node.add(buildTemplateNode(child));
            }
        }
        // Fallback to resolved elemList if subelements is empty
        if (elem.elemList != null && !elem.elemList.isEmpty() && (elem.subelements == null || elem.subelements.isEmpty())) {
            for (RecDef.Elem child : elem.elemList) {
                node.add(buildTemplateNode(child));
            }
        }
        return node;
    }

    private static class TemplateTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof RecDef.Elem) {
                    RecDef.Elem elem = (RecDef.Elem) userObject;
                    String text = elem.tag.toString();
                    if (elem.label != null && !elem.label.isEmpty()) {
                        text += "  —  " + elem.label;
                    }
                    if (elem.attrs != null && !elem.attrs.isEmpty()) {
                        text += "  [@" + elem.attrs + "]";
                    }
                    if (elem.target != null && !elem.target.isEmpty()) {
                        text += "  → " + elem.target;
                    }
                    setText(text);
                    boolean hasChildren = !elem.elemList.isEmpty() || !elem.attrList.isEmpty()
                            || (elem.subelements != null && !elem.subelements.isEmpty())
                            || (elem.subattrs != null && !elem.subattrs.isEmpty())
                            || (elem.target != null && !elem.target.isEmpty());
                    setIcon(hasChildren ? SwingHelper.ICON_COMPOSITE : SwingHelper.ICON_VALUE);
                } else if (userObject instanceof RecDef.Attr) {
                    RecDef.Attr attr = (RecDef.Attr) userObject;
                    setText("@" + attr.tag.toString());
                    setIcon(SwingHelper.ICON_ATTRIBUTE);
                }
            }
            return component;
        }
    }

}
