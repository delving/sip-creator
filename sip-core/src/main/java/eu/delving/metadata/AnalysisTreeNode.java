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

package eu.delving.metadata;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.*;

/**
 * A node of the analysis tree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class AnalysisTreeNode implements AnalysisTree.Node, Serializable {
    private static final long serialVersionUID = -8362212829296408316L;
    private AnalysisTreeNode parent;
    private List<AnalysisTreeNode> children = new ArrayList<AnalysisTreeNode>();
    private Tag tag;
    private Path path;
    private boolean recordRoot, uniqueElement;
    private FieldStatistics fieldStatistics;

    AnalysisTreeNode(Tag tag) {
        this.tag = tag;
    }

    AnalysisTreeNode(AnalysisTreeNode parent, Tag tag) {
        this.parent = parent;
        this.tag = tag;
    }

    AnalysisTreeNode(AnalysisTreeNode parent, FieldStatistics fieldStatistics) {
        this.parent = parent;
        this.fieldStatistics = fieldStatistics;
        this.tag = fieldStatistics.getPath().peek();
    }

    public void setStatistics(FieldStatistics fieldStatistics) {
        this.fieldStatistics = fieldStatistics;
    }

    public List<AnalysisTreeNode> getChildren() {
        return children;
    }

    public boolean hasStatistics() {
        return fieldStatistics != null;
    }

    @Override
    public FieldStatistics getStatistics() {
        return fieldStatistics;
    }

    @Override
    public TreePath getTreePath() {
        List<AnalysisTreeNode> list = new ArrayList<AnalysisTreeNode>();
        compilePathList(list);
        return new TreePath(list.toArray());
    }

    @Override
    public Tag getTag() {
        return tag;
    }

    @Override
    public Path getPath() {
        if (path == null) {
            List<AnalysisTreeNode> list = new ArrayList<AnalysisTreeNode>();
            compilePathList(list);
            path = new Path();
            for (AnalysisTreeNode node : list) {
                path.push(node.getTag());
            }
        }
        return path;
    }

    @Override
    public boolean setRecordRoot(Path recordRoot) {
        boolean oldValue = this.recordRoot;
        this.recordRoot = recordRoot != null && getPath().equals(recordRoot);
        return this.recordRoot || this.recordRoot != oldValue;
    }

    @Override
    public boolean setUniqueElement(Path uniqueElement) {
        boolean oldValue = this.uniqueElement;
        this.uniqueElement = uniqueElement != null && getPath().equals(uniqueElement);
        return this.uniqueElement != oldValue;
    }

    @Override
    public boolean isRecordRoot() {
        return recordRoot;
    }

    @Override
    public boolean isUniqueElement() {
        return uniqueElement;
    }

    @Override
    public Iterable<? extends AnalysisTree.Node> getChildNodes() {
        return children;
    }

    @Override
    public boolean couldBeRecordRoot() {
        return fieldStatistics != null && !fieldStatistics.hasValues();
    }

    @Override
    public boolean couldBeUniqueElement() {
        if (couldBeRecordRoot()) return false;
        AnalysisTreeNode walk = parent;
        while (walk != null) { // ancestor must be record root
            if (walk.isRecordRoot()) return true;
            walk = walk.parent;
        }
        return false;
    }

    @Override
    public String getVariableName() {
        if (tag.isAttribute()) throw new RuntimeException("Should never ask an attribute for its variable name");
        List<AnalysisTreeNode> path = new ArrayList<AnalysisTreeNode>();
        AnalysisTreeNode node = this;
        while (node != null && !node.isRecordRoot()) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        StringBuilder out = new StringBuilder("input.");
        Iterator<AnalysisTreeNode> nodeWalk = path.iterator();
        while (nodeWalk.hasNext()) {
            String nodeName = nodeWalk.next().tag.toString();
            out.append(SourceVariable.Filter.tagToVariable(nodeName));
            if (nodeWalk.hasNext()) {
                out.append('.');
            }
        }
        return out.toString();
    }

    @Override
    public void showPath(final JTree tree, final Path pathToShow) {
        final Path here = getPath();
        Timer timer = new Timer(30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if ((pathToShow.equals(here) || pathToShow.isAncestorOf(here)) && !tree.isExpanded(getTreePath())) {
                    tree.expandPath(getTreePath());
                }
                else if ((here.size() <= pathToShow.size() && !here.isAncestorOf(pathToShow)) && !tree.isCollapsed(getTreePath())) {
                    tree.collapsePath(getTreePath());
                }
                for (AnalysisTree.Node sub : children) sub.showPath(tree, pathToShow);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void compilePathList(List<AnalysisTreeNode> list) {
        if (parent != null) {
            parent.compilePathList(list);
        }
        list.add(this);
    }

    public void add(AnalysisTreeNode child) {
        children.add(child);
    }

    @Override
    public TreeNode getChildAt(int index) {
        return children.get(index);
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
        AnalysisTreeNode qNameNode = (AnalysisTreeNode) treeNode;
        return children.indexOf(qNameNode);
    }

    @Override
    public boolean getAllowsChildren() {
        return !children.isEmpty();
    }

    @Override
    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public Enumeration children() {
        return new Vector<AnalysisTreeNode>(children).elements();
    }

    @Override
    public int compareTo(AnalysisTree.Node other) {
        return getVariableName().compareTo(other.getVariableName());
    }

    public String toString() {
        if (tag == null) {
            return "?";
        }
        else if (fieldStatistics != null) {
            return String.format("%s (%d)", tag.toString(), fieldStatistics.getTotal());
        }
        else {
            return tag.toString();
        }
    }
}
