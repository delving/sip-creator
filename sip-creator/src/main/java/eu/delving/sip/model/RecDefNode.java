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

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.AnalysisTree;
import eu.delving.sip.base.Utility;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * Represent an element in the JTree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefNode implements TreeNode {
    private RecDefNode parent;
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private List<RecDefNode> elements = new ArrayList<RecDefNode>();
    private Vector<RecDefNode> kids = new Vector<RecDefNode>();
    private boolean leaf;
    private RecDefPath path;

    public static RecDefNode create(RecDef recDef) {
        return new RecDefNode(null, recDef.root);
    }

    public static RecDefNode create(RecDefNode parent, RecDef.Ref ref) {
        if (ref.attr != null) {
            return new RecDefNode(parent, ref.attr);
        }
        else {
            return new RecDefNode(parent, ref.elem);
        }
    }

    private RecDefNode(RecDefNode parent, RecDef.Elem elem) {
        this.parent = parent;
        this.elem = elem;
        for (RecDef.Elem e : elem.elemList) elements.add(new RecDefNode(this, e));
        for (RecDef.Attr attr : elem.attrList) kids.add(new RecDefNode(this, attr));
        kids.addAll(elements);
        this.leaf = kids.isEmpty();
    }

    private RecDefNode(RecDefNode parent, RecDef.Attr attr) {
        this.parent = parent;
        this.attr = attr;
        this.leaf = true;
    }

    @Override
    public TreeNode getChildAt(int i) {
        return kids.get(i);
    }

    @Override
    public int getChildCount() {
        return kids.size();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode treeNode) {
        return kids.indexOf(treeNode);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return leaf;
    }

    @Override
    public Enumeration children() {
        return kids.elements();
    }

    public Vector<RecDefNode> getKids() {
        return kids;
    }

    public String toString() {
        return isAttr() ? attr.tag.toString().substring(1) : elem.tag.toString();
    }

    public boolean isAttr() {
        return attr != null;
    }

    public RecDef.Elem getElem() {
        return elem;
    }

    public RecDef.Attr getAttr() {
        return attr;
    }

    public boolean hasChildElements() {
        return !elem.elemList.isEmpty();
    }

    public void showPath(final JTree tree, final Path pathToShow) {
        final Path here = getPath().getOurPath();
        Timer timer = new Timer(30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if ((pathToShow.equals(here) || pathToShow.isAncestorOf(here)) && !tree.isExpanded(getPath())) {
                    tree.expandPath(getPath());
                }
                else if ((here.size() <= pathToShow.size() && !here.isAncestorOf(pathToShow)) && !tree.isCollapsed(getPath())) {
                    tree.collapsePath(getPath());
                }
                for (RecDefNode sub : elements) sub.showPath(tree, pathToShow);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    public RecDefPath getPath() {
        if (path == null) path = parent == null ? new RecDefPath(this) : new RecDefPath(parent.getPath(), this);
        return path;
    }

    public Tag getTag() {
        return isAttr() ? attr.tag : elem.tag;
    }

    public Path getOurPath() {
        return getPath().getOurPath();
    }

    public RecDef.Doc getDoc() {
        return isAttr() ? attr.doc : elem.doc;
    }

    public List<RecDef.Opt> getOptions() {
        return isAttr() ? null : elem.options;
    }

    public void setNode(AnalysisTree.Node node) {
//        if (isAttr()) {
//            attr.node = node;
//        }
//        else {
//            elem.node = node;
//        }
    }

    public AnalysisTree.Node getNode() {
//        return isAttr()? attr.node : elem.node;
        return null;
    }

    public static class RecDefPath extends TreePath {

        RecDefPath(RecDefNode elemNode) {
            super(elemNode);
        }

        RecDefPath(TreePath treePath, RecDefNode elemNode) {
            super(treePath, elemNode);
        }

        public Path getOurPath() {
            Path path = new Path();
            for (Object object : getPath()) {
                RecDefNode node = (RecDefNode) object;
                path = node.attr == null ? path.extend(node.elem.tag) : path.extend(node.attr.tag);
            }
            return path;
        }
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            RecDefNode node = (RecDefNode) value;
            if (node.isAttr()) {
                setIcon(Utility.ATTRIBUTE_ICON);
            }
            else if (node.hasChildElements()) {
                setIcon(Utility.COMPOSITE_ELEMENT_ICON);
            }
            else {
                setIcon(Utility.VALUE_ELEMENT_ICON);
            }
            return component;
        }
    }


}
