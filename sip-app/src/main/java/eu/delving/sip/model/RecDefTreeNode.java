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


package eu.delving.sip.model;

import eu.delving.metadata.DynOpt;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.Storage;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Collectors;

import static eu.delving.sip.base.SwingHelper.*;

/**
 * Represent an element of a record definition in the tree, including an HTML representation and the associated
 * cell renderer.
 */

public class RecDefTreeNode extends FilterNode {
    private RecDefTreeNode parent;
    private RecDefNode recDefNode;
    private RecDefPath recDefPath;
    private Vector<RecDefTreeNode> children = new Vector<RecDefTreeNode>();
    private String html;

    public static RecDefTreeNode create(RecDefNode recDefNode) {
        return new RecDefTreeNode(null, recDefNode);
    }

    public static RecDefTreeNode create(RecDefTreeNode parent, RecDefNode node) {
        return new RecDefTreeNode(parent, node);
    }

    private RecDefTreeNode(RecDefTreeNode parent, RecDefNode recDefNode) {
        this.parent = parent;
        this.recDefNode = recDefNode;
        if (parent != null) parent.children.add(this);
        for (RecDefNode subRecDefNode : recDefNode.getChildren()) {
            if (!subRecDefNode.isHidden()) new RecDefTreeNode(this, subRecDefNode);
        }
    }

    @Override
    public Object getParent() {
        return parent;
    }

    public RecDefTreeNode getParentNode() {
        return parent;
    }

    @Override
    public boolean isLeaf() {
        return recDefNode.getChildren().isEmpty();
    }

    public Vector<RecDefTreeNode> getChildren() {
        return children;
    }

    @Override
    public String getStringToFilter() {
        if (recDefNode.isRequired()) {
            return recDefNode.getTag() + " <required>";
        }
        return recDefNode.getTag().toString();
    }

    @Override
    public boolean isAttr() {
        return recDefNode.isAttr();
    }

    public boolean isSimple() {
        return recDefNode.isSimple();
    }

    @Override
    public boolean passesFilter() {
        RecDefTreeModel recDefTreeModel = (RecDefTreeModel) filterModel;
        return !(recDefTreeModel.isAttributesHidden() && isAttr()) && super.passesFilter();
    }

    public String toHtml() {
        if (html == null) html = recDefNodeToHTML(recDefNode);
        return html;
    }

    public String toString() {
        String simple = recDefNode.isSimple() ? " simple" : "";
        if (recDefNode.requiresNodeMappings()) {
            return recDefNode.toString() + " <required>" + simple;
        }
        return recDefNode.toString() + simple;
    }

    public boolean hasChildElements() {
        for (RecDefTreeNode kid : children) if (!kid.recDefNode.isAttr()) return true;
        return false;
    }

    public void showPath(final JTree tree, final Path pathToShow) {
        final Path here = getRecDefPath().getTagPath();
        Timer timer = new Timer(30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                boolean pathShouldShow = pathToShow.equals(here) || pathToShow.isAncestorOf(here);
                if (pathShouldShow) {
                    if (!tree.isExpanded(getRecDefPath())) tree.expandPath(getRecDefPath());
                } else if ((here.size() <= pathToShow.size() && !here.isAncestorOf(pathToShow)) && !tree.isCollapsed(getRecDefPath())) {
                    tree.collapsePath(getRecDefPath());
                }
                for (RecDefTreeNode sub : children) if (!sub.recDefNode.isAttr()) sub.showPath(tree, pathToShow);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    public RecDefPath getRecDefPath() {
        if (recDefPath == null) {
            recDefPath = parent == null ? new RecDefPath(this) : new RecDefPath(parent.getRecDefPath(), this);
        }
        return recDefPath;
    }

    public RecDefNode getRecDefNode() {
        return recDefNode;
    }

    public void addNodeMapping(NodeMapping nodeMapping) {
        this.recDefNode.addNodeMapping(nodeMapping);
    }

    public void removeNodeMapping(NodeMapping nodeMapping) {
        this.recDefNode.removeNodeMapping(nodeMapping.inputPath);
    }

    public RecDefTreeNode getRecDefTreeNode(RecDefNode node) {
        if (recDefNode == node) return this;
        for (RecDefTreeNode child : children) {
            RecDefTreeNode found = child.getRecDefTreeNode(node);
            if (found != null) return found;
        }
        return null;
    }

    public RecDefTreeNode createDynOptSibling(DynOpt dynOpt) {
        RecDefNode sibling = recDefNode.addSibling(dynOpt);
        int index = parent.children.indexOf(this);
        if (index < 0) throw new RuntimeException("Unable to find self among children of parent");
        RecDefTreeNode siblingTreeNode = create(sibling);
        siblingTreeNode.parent = parent;
        siblingTreeNode.setFilterModel(filterModel);
        parent.children.add(index + 1, siblingTreeNode);
        refresh();
        return siblingTreeNode;
    }

    public static class RecDefPath extends TreePath {

        RecDefPath(RecDefTreeNode elemNode) {
            super(elemNode);
        }

        RecDefPath(TreePath treePath, RecDefTreeNode elemNode) {
            super(treePath, elemNode);
        }

        public Path getTagPath() {
            Path path = Path.create();
            for (Object object : getPath()) {
                RecDefTreeNode treeNode = (RecDefTreeNode) object;
                path = path.child(treeNode.getRecDefNode().getTag());
            }
            return path;
        }
    }

    public static class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            setOpaque(false);
            setBorder(null);
            Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) value;
                if (node.recDefNode.isUnmappable()) {
                    setIcon(SwingHelper.ICON_UNMAPPABLE);
                } else if (node.recDefNode.isAttr()) {
                    setIcon(SwingHelper.ICON_ATTRIBUTE);
                } else if (node.hasChildElements()) {
                    setIcon(SwingHelper.ICON_COMPOSITE);
                } else {
                    setIcon(SwingHelper.ICON_VALUE);
                }
                if (node.recDefNode.isPopulated()) {
                    setColor(selected, node);
                }
                if (!node.recDefNode.getNodeMappings().isEmpty()) {
                    markNodeMappings(node);
                }
            } else {
                setIcon(SwingHelper.ICON_COMPOSITE);
            }
            return component;
        }

        private void setColor(boolean selected, RecDefTreeNode node) {
            Color color = node.isHighlighted() ? HIGHLIGHTED_COLOR : MAPPED_COLOR;

            if (selected) {
                setOpaque(false);
                setBackground(Color.WHITE);
                setForeground(color);
            } else if (node.recDefNode.requiresNodeMappings()) {
                setOpaque(true);
                setBackground(Color.YELLOW);
                setForeground(Color.BLACK);
            } else if (node.recDefNode.inputPathMissing) {
                setOpaque(true);
                setBackground(Color.RED);
                setForeground(Color.WHITE);
            } else {
                setOpaque(true);
                setBackground(color);
                setForeground(Color.BLACK);
            }
        }

        private void markNodeMappings(RecDefTreeNode node) {
            String text = String.format("<html><b>%s</b> &larr; %s", node.toString(), getCommaList(node));
            if (node.recDefNode.requiresNodeMappings()) {
                setText(text + " [required]");
            } else {
                setText(text);
            }
        }

        private String getCommaList(RecDefTreeNode node) {
            Set<String> inputStrings = new TreeSet<String>();
            for (NodeMapping nodeMapping : node.recDefNode.getNodeMappings().values()) {
                for (Path path : nodeMapping.getInputPaths()) {
                    if (path.getTag(0).getLocalName().equals(Storage.CONSTANT_TAG)) {
                        inputStrings.add(String.format("\"%s\"", nodeMapping.getConstantValue()));
                    } else {
                        inputStrings.add(path.getTail());
                    }
                }
            }
            StringBuilder list = new StringBuilder();
            Iterator<String> walk = inputStrings.iterator();
            while (walk.hasNext()) {
                list.append(walk.next());
                if (walk.hasNext()) list.append(", ");
            }
            return list.toString();
        }

    }


}
