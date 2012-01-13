/*
 * Copyright 2010 DELVING BV
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
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.base.Utility;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Represent an element in the JTree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefTreeNode implements TreeNode {
    private RecDefTreeNode parent;
    private RecDefNode recDefNode;
    private RecDefPath recDefPath;
    private Vector<RecDefTreeNode> children = new Vector<RecDefTreeNode>();
    private String html;

    public static TreeNode create(String message) {
        return new DefaultMutableTreeNode(message);
    }

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
        for (RecDefNode subRecDefNode : recDefNode.getChildren()) new RecDefTreeNode(this, subRecDefNode);
    }

    @Override
    public TreeNode getChildAt(int i) {
        return children.get(i);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode treeNode) {
        RecDefTreeNode node = (RecDefTreeNode) treeNode;
        return children.indexOf(node);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return recDefNode.getChildren().isEmpty();
    }

    @Override
    public Enumeration children() {
        return children.elements();
    }

    public Vector<RecDefTreeNode> getChildren() {
        return children;
    }

    public String toHtml() {
        if (html == null) {
            StringTemplate t = Utility.getTemplate(recDefNode.isAttr() ? "recdef-attribute" : "recdef-element");
            t.setAttribute("name", recDefNode.getTag());
            t.setAttribute("doc", recDefNode.getDoc());
            t.setAttribute("options", recDefNode.getOptions());
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
                if ((pathToShow.equals(here) || pathToShow.isAncestorOf(here)) && !tree.isExpanded(getRecDefPath())) {
                    tree.expandPath(getRecDefPath());
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
        if (recDefPath == null)
            recDefPath = parent == null ? new RecDefPath(this) : new RecDefPath(parent.getRecDefPath(), this);
        return recDefPath;
    }

    public RecDefNode getRecDefNode() {
        return recDefNode;
    }

    public NodeMapping addStatsTreeNode(StatsTreeNode statsTreeNode) {
        return this.recDefNode.addNodeMapping(new NodeMapping().setStatsTreeNode(statsTreeNode).setInputPath(statsTreeNode.getPath()));
    }

    public void removeStatsTreeNode(Path path) {
        this.recDefNode.removeNodeMapping(path);
    }

    public static class RecDefPath extends TreePath {

        RecDefPath(RecDefTreeNode elemNode) {
            super(elemNode);
        }

        RecDefPath(TreePath treePath, RecDefTreeNode elemNode) {
            super(treePath, elemNode);
        }

        public Path getTagPath() {
            Path path = Path.empty();
            for (Object object : getPath()) {
                RecDefTreeNode treeNode = (RecDefTreeNode) object;
                path = path.extend(treeNode.getRecDefNode().getTag());
            }
            return path;
        }
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) value;
                if (node.recDefNode.isAttr()) {
                    setIcon(Utility.ATTRIBUTE_ICON);
                }
                else if (node.hasChildElements()) {
                    setIcon(Utility.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(Utility.VALUE_ELEMENT_ICON);
                }
            }
            else {
                setIcon(Utility.COMPOSITE_ELEMENT_ICON);
            }
            return component;
        }
    }


}
