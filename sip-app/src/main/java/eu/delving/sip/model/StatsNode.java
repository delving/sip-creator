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

import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.SwingHelper;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * A node of a stats tree, allowing for selection of a field for viewing its statistics.  Also the associated
 * cell renderer is here.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class StatsNode implements TreeNode, Comparable<StatsNode> {
    private StatsNode parent;
    private List<StatsNode> children = new ArrayList<StatsNode>();
    private Tag tag;

    public static StatsNode create(String rootTag) {
        return new StatsNode(rootTag);
    }

    public static StatsNode create(Collection<Path> paths) {
        StatsNode root = createSubtree(paths, Path.create(), null);
        if (root == null) root = new StatsNode("No statistics");
        return root;
    }

    private StatsNode(StatsNode parent, String name) {
        this.parent = parent;
        this.tag = Tag.create(name);
    }

    private StatsNode(String name) {
        this(null, name);
    }

    private StatsNode(StatsNode parent, Tag tag) {
        if ((this.parent = parent) != null) parent.children.add(this);
        this.tag = tag;
    }

    public void getPaths(Set<Path> sourcePaths) {
        sourcePaths.add(getPath(false));
        for (StatsNode child : children) child.getPaths(sourcePaths);
    }

    public TreePath getTreePath() {
        List<StatsNode> list = new ArrayList<StatsNode>();
        compilePathList(list, true);
        return new TreePath(list.toArray());
    }

    public Tag getTag() {
        return tag;
    }

    public Path getPath(boolean fromRoot) {
        List<StatsNode> list = new ArrayList<StatsNode>();
        compilePathList(list, fromRoot);
        Path path = Path.create();
        for (StatsNode node : list) path = path.child(node.getTag());
        return path;
    }

    public Iterable<? extends StatsNode> getChildNodes() {
        return children;
    }

    public void expand(final JTree tree) {
        Timer timer = new Timer(30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                tree.expandPath(getTreePath());
                for (StatsNode sub : children) sub.expand(tree);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void compilePathList(List<StatsNode> list, boolean fromRoot) {
        if (fromRoot) {
            if (parent != null) parent.compilePathList(list, fromRoot);
            list.add(this);
        }
        else if (parent != null) { // only take nodes with parents
            parent.compilePathList(list, fromRoot);
            list.add(this);
        }
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
        return children.indexOf((StatsNode) treeNode);
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public Enumeration children() {
        return new Vector<Object>(children).elements();
    }

    @Override
    public int compareTo(StatsNode other) {
        return getPath(true).compareTo(other.getPath(true));
    }

    public String toString() {
        return tag.toString();
    }

    private static StatsNode createSubtree(Collection<Path> paths, Path path, StatsNode parent) {
        Map<Tag, Set<Path>> pathMap = new TreeMap<Tag, Set<Path>>();
        for (Path pathEntry : paths) {
            Path subPath = pathEntry.takeFirst(path.size());
            if (subPath.equals(path) && pathEntry.size() == path.size() + 1) {
                Tag tag = pathEntry.getTag(path.size());
                if (tag == null) continue;
                Set<Path> set = pathMap.get(tag);
                if (set == null) pathMap.put(tag, set = new TreeSet<Path>());
                set.add(pathEntry);
            }
        }
        if (pathMap.isEmpty()) return null;
        Tag tag = path.peek();
        StatsNode node = tag == null ? null : new StatsNode(parent, tag);
        for (Map.Entry<Tag, Set<Path>> entry : pathMap.entrySet()) {
            Path childPath = path.child(entry.getKey());
            StatsNode child = createSubtree(paths, childPath, node);
            if (child != null) {
                if (node == null) node = child;
            }
            else if (entry.getValue().contains(childPath)) {
                StatsNode fresh = new StatsNode(node, entry.getKey());
                if (node == null) node = fresh;
            }
        }
        return node;
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof StatsNode) {
                StatsNode node = (StatsNode) value;
                if (node.getTag().isAttribute()) {
                    setIcon(SwingHelper.ATTRIBUTE_ICON);
                }
                else if (node.getChildNodes().iterator().hasNext()) {
                    setIcon(SwingHelper.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(SwingHelper.VALUE_ELEMENT_ICON);
                }
            }
            return component;
        }
    }
}
