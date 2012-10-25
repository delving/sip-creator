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


package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.SwingHelper;
import org.antlr.stringtemplate.StringTemplate;

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

import static eu.delving.sip.base.SwingHelper.HILIGHTED_COLOR;
import static eu.delving.sip.base.SwingHelper.MAPPED_COLOR;

/**
 * Represent an element of a record definition in the tree, including an HTML representation and the associated
 * cell renderer.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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

    @Override
    public boolean isLeaf() {
        return recDefNode.getChildren().isEmpty();
    }

    public Vector<RecDefTreeNode> getChildren() {
        return children;
    }

    @Override
    public String getStringToFilter() {
        return recDefNode.getTag().toString();
    }

    @Override
    public boolean isAttr() {
        return recDefNode.isAttr();
    }

    @Override
    public boolean passesFilter() {
        RecDefTreeModel recDefTreeModel = (RecDefTreeModel) filterModel;
        if (recDefTreeModel.isAttributesHidden() && isAttr()) return false;
        if (recDefNode.isHiddenOpt(recDefTreeModel.getSelectedOpt()) && !recDefNode.hasDescendentNodeMappings()) return false;
        return super.passesFilter();
    }

    public String toHtml() {
        if (html == null) {
            StringTemplate t = SwingHelper.getTemplate(recDefNode.isAttr() ? "recdef-attribute" : "recdef-element");
            t.setAttribute("name", recDefNode.getTag());
            t.setAttribute("doc", recDefNode.getDoc());
            t.setAttribute("optList", recDefNode.getOptList());
            t.setAttribute("nodeMappings", recDefNode.getNodeMappings().values());
            html = t.toString();
        }
        return html;
    }

    public String toString() {
        return recDefNode.toString();
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
                }
                else if ((here.size() <= pathToShow.size() && !here.isAncestorOf(pathToShow)) && !tree.isCollapsed(getRecDefPath())) {
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

    public RecDefTreeNode getRecDefTreeNode(RecDefNode node) {
        if (recDefNode == node) return this;
        for (RecDefTreeNode child : children) {
            RecDefTreeNode found = child.getRecDefTreeNode(node);
            if (found != null) return found;
        }
        return null;
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
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            setOpaque(false);
            setBorder(null);
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) value;
                if (node.recDefNode.isUnmappable()) {
                    setIcon(SwingHelper.ICON_UNMAPPABLE);
                }
                else if (node.recDefNode.isAttr()) {
                    setIcon(SwingHelper.ICON_ATTRIBUTE);
                }
                else if (node.hasChildElements()) {
                    setIcon(SwingHelper.ICON_COMPOSITE);
                }
                else {
                    setIcon(SwingHelper.ICON_VALUE);
                }
                if (!node.recDefNode.getNodeMappings().isEmpty()) {
                    markNodeMappings(sel, node);
                }
            }
            else {
                setIcon(SwingHelper.ICON_COMPOSITE);
            }
            return component;
        }

        private void markNodeMappings(boolean selected, RecDefTreeNode node) {
            Color color = node.isHighlighted() ? HILIGHTED_COLOR : MAPPED_COLOR;
            if (selected) {
                setOpaque(false);
                setBackground(Color.WHITE);
                setForeground(color);
            }
            else {
                setOpaque(true);
                setBackground(color);
                setForeground(Color.BLACK);
            }
//            setBorder(BorderFactory.createEtchedBorder());
            setText(String.format("<html><b>%s</b> &larr; %s", node.toString(), getCommaList(node)));
        }

        private String getCommaList(RecDefTreeNode node) {
            Set<String> tails = new TreeSet<String>();
            for (NodeMapping nodeMapping : node.recDefNode.getNodeMappings().values()) {
                for (Path path : nodeMapping.getInputPaths()) tails.add(path.getTail());
            }
            StringBuilder list = new StringBuilder();
            Iterator<String> walk = tails.iterator();
            while (walk.hasNext()) {
                list.append(walk.next());
                if (walk.hasNext()) list.append(", ");
            }
            return list.toString();
        }

    }


}
