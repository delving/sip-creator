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
import eu.delving.metadata.Tag;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.Storage;
import eu.delving.stats.Stats;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.*;

/**
 * A node of the analysis tree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceTreeNode extends FilterNode implements Comparable<SourceTreeNode> {
    private SourceTreeNode parent;
    private List<SourceTreeNode> children = new ArrayList<SourceTreeNode>();
    private Tag tag;
    private boolean recordRoot, uniqueElement;
    private Stats.ValueStats valueStats;
    private String htmlToolTip, htmlDetails;
    private Set<NodeMapping> nodeMappings = new HashSet<NodeMapping>();

    public static SourceTreeNode create(String rootTag) {
        return new SourceTreeNode(rootTag, "<h3>Root</h3>");
    }

    public static SourceTreeNode create(Map<Path, Stats.ValueStats> pathMap, Map<String, String> facts) {
        SourceTreeNode root = createSubtree(pathMap, Path.create(), null);
        if (root == null) {
            root = new SourceTreeNode("No statistics", "<h3>No statistics</h3>");
        }
        else if (root.getTag().toString().equals(Storage.ENVELOPE_TAG)) {
            SourceTreeNode factNode = new SourceTreeNode(root, Storage.FACTS_TAG, "<h3>Select a fact from here</h3>");
            root.getChildren().add(0, factNode);
            for (Map.Entry<String, String> entry : facts.entrySet()) new SourceTreeNode(factNode, entry);
            root.getChildren().add(0, new SourceTreeNode(root, Storage.CONSTANT_TAG, "<html>Constant value which you can adjust in the code snippet"));
        }
        return root;
    }

    private SourceTreeNode(SourceTreeNode parent, String name, String htmlChunk) {
        this.parent = parent;
        this.tag = Tag.create(name);
        this.htmlToolTip = this.htmlDetails = htmlChunk;
    }

    private SourceTreeNode(String name, String htmlChunk) {
        this(null, name, htmlChunk);
    }

    private SourceTreeNode(SourceTreeNode parent, Tag tag) {
        if ((this.parent = parent) != null) parent.children.add(this);
        this.tag = tag;
    }

    private SourceTreeNode(SourceTreeNode parent, Map.Entry<String, String> entry) {
        this(parent, Tag.create(entry.getKey()));
        StringTemplate template = SwingHelper.getTemplate("fact-brief");
        template.setAttribute("fact", entry);
        this.htmlToolTip = this.htmlDetails = template.toString();
    }

    private SourceTreeNode(SourceTreeNode parent, Tag tag, Stats.ValueStats valueStats) {
        this(parent, tag);
        setStats(valueStats);
    }

    public void setStats(Stats.ValueStats valueStats) {
        this.valueStats = valueStats;
        StringTemplate template = SwingHelper.getTemplate("stats-tooltip");
        template.setAttribute("path", getPath(true));
        template.setAttribute("stats", valueStats);
        this.htmlToolTip = template.toString();
        template = SwingHelper.getTemplate("stats-detail");
        template.setAttribute("path", getPath(true));
        template.setAttribute("stats", valueStats);
        this.htmlDetails = template.toString();
    }

    public List<SourceTreeNode> getChildren() {
        return children;
    }

    public void getPaths(Set<Path> sourcePaths) {
        sourcePaths.add(getPath(false));
        for (SourceTreeNode child : children) child.getPaths(sourcePaths);
    }

    public Stats.ValueStats getStats() {
        return valueStats;
    }

    public Set<NodeMapping> getNodeMappings() {
        return nodeMappings;
    }

    public TreePath getTreePath() {
        List<SourceTreeNode> list = new ArrayList<SourceTreeNode>();
        compilePathList(list, true);
        return new TreePath(list.toArray());
    }

    public Tag getTag() {
        return tag;
    }

    public Path getPath(boolean fromRoot) {
        List<SourceTreeNode> list = new ArrayList<SourceTreeNode>();
        compilePathList(list, fromRoot);
        Path path = Path.create();
        for (SourceTreeNode node : list) path = path.child(node.getTag());
        return path;
    }

    public int setRecordRoot(Path recordRoot) {
        boolean oldValue = this.recordRoot;
        Path ourPath = getPath(true);
        this.recordRoot = recordRoot != null && ourPath.equals(recordRoot);
        if (this.recordRoot || this.recordRoot != oldValue) fireChanged();
        int childTotal = 0;
        for (SourceTreeNode child : children) {
            int subtotal = child.setRecordRoot(recordRoot);
            if (subtotal > 0) childTotal = subtotal;
        }
        return this.recordRoot ? getStats().total : childTotal;
    }

    public void setUniqueElement(Path uniqueElement) {
        boolean oldValue = this.uniqueElement;
        this.uniqueElement = uniqueElement != null && getPath(true).equals(uniqueElement);
        if (this.uniqueElement != oldValue) fireChanged();
        for (SourceTreeNode child : children) child.setUniqueElement(uniqueElement);
    }

    public boolean isRecordRoot() {
        return recordRoot;
    }

    public boolean isUniqueElement() {
        return uniqueElement;
    }

    public boolean couldBeRecordRoot() {
        return valueStats != null && !valueStats.hasValues();
    }

    public boolean couldBeUniqueElement() {
        if (valueStats == null || !valueStats.unique) return false;
        SourceTreeNode walk = parent;
        while (walk != null) { // ancestor must be record root
            if (walk.isRecordRoot()) return true;
            walk = walk.parent;
        }
        return false;
    }

    public void showPath(final JTree tree, final Path pathToShow) {
        final Path here = getPath(true);
        Timer timer = new Timer(30, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if ((pathToShow.equals(here) || pathToShow.isAncestorOf(here)) && !tree.isExpanded(getTreePath())) {
                    tree.expandPath(getTreePath());
                }
                else if ((here.size() <= pathToShow.size() && !here.isAncestorOf(pathToShow)) && !tree.isCollapsed(getTreePath())) {
                    tree.collapsePath(getTreePath());
                }
                for (SourceTreeNode sub : children) sub.showPath(tree, pathToShow);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    public String getStringToFilter() {
        return tag.toString();
    }

    @Override
    public boolean isAttr() {
        return tag.isAttribute();
    }

    private void compilePathList(List<SourceTreeNode> list, boolean fromRoot) {
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
    public Object getParent() {
        return parent;
    }

    @Override
    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public int compareTo(SourceTreeNode other) {
        return getPath(true).compareTo(other.getPath(true));
    }

    public String getHtmlToolTip() {
        return htmlToolTip;
    }

    public String getHtmlDetails() {
        return htmlDetails;
    }

    public String toString() {
        if (tag == null) {
            return "?";
        }
        else if (valueStats != null) {
            return String.format("%s (%d)", tag.toString(), valueStats.total);
        }
        else {
            return tag.toString();
        }
    }

    public static void setStatsTreeNodes(final SortedSet<SourceTreeNode> nodes, final NodeMapping nodeMapping) {
        List<Path> inputPaths = new ArrayList<Path>();
        for (SourceTreeNode node : nodes) {
            inputPaths.add(node.getPath(false));
            node.addMappedIn(nodeMapping);
        }
        nodeMapping.setStatsTreeNodes(nodes, inputPaths);
    }

    public static void removeStatsTreeNodes(final NodeMapping nodeMapping) {
        if (nodeMapping.hasSourceTreeNodes()) {
            for (Object nodeObject : nodeMapping.getSourceTreeNodes()) {
                ((SourceTreeNode) nodeObject).removeMappedIn(nodeMapping);
            }
        }
    }

    private void addMappedIn(NodeMapping nodeMapping) {
        this.nodeMappings.add(nodeMapping);
        fireChanged();
    }

    private void removeMappedIn(NodeMapping nodeMapping) {
        if (this.nodeMappings.remove(nodeMapping)) fireChanged();
    }

    private static SourceTreeNode createSubtree(Map<Path, Stats.ValueStats> pathMap, Path path, SourceTreeNode parent) {
        Map<Tag, Map<Path, Stats.ValueStats>> statisticsMap = new TreeMap<Tag, Map<Path, Stats.ValueStats>>();
        for (Map.Entry<Path, Stats.ValueStats> entry : pathMap.entrySet()) {
            Path key = entry.getKey();
            Path subPath = key.takeFirst(path.size());
            if (subPath.equals(path) && key.size() == path.size() + 1) {
                Tag tag = key.getTag(path.size());
                if (tag == null) continue;
                Map<Path, Stats.ValueStats> map = statisticsMap.get(tag);
                if (map == null) statisticsMap.put(tag, map = new TreeMap<Path, Stats.ValueStats>());
                map.put(key, entry.getValue());
            }
        }
        if (statisticsMap.isEmpty()) return null;
        Tag tag = path.peek();
        SourceTreeNode node = tag == null ? null : new SourceTreeNode(parent, tag);
        for (Map.Entry<Tag, Map<Path, Stats.ValueStats>> entry : statisticsMap.entrySet()) {
            Path childPath = path.child(entry.getKey());
            Stats.ValueStats valueStatsForChild = null;
            for (Map.Entry<Path, Stats.ValueStats> pathStatsEntry : entry.getValue().entrySet()) {
                if (pathStatsEntry.getKey().equals(childPath)) {
                    valueStatsForChild = pathStatsEntry.getValue();
                }
            }
            SourceTreeNode child = createSubtree(pathMap, childPath, node);
            if (child != null) {
                if (node == null) node = child;
                child.setStats(valueStatsForChild);
            }
            else if (valueStatsForChild != null) {
                SourceTreeNode fresh = new SourceTreeNode(node, entry.getKey(), valueStatsForChild);
                if (node == null) node = fresh;
            }
        }
        return node;
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof SourceTreeNode) {
                SourceTreeNode node = (SourceTreeNode) value;
                if (node.getTag().isAttribute()) {
                    setIcon(SwingHelper.ATTRIBUTE_ICON);
                }
                else if (node.getChildren().iterator().hasNext()) {
                    setIcon(SwingHelper.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(SwingHelper.VALUE_ELEMENT_ICON);
                }
                if (node.isRecordRoot() || node.isUniqueElement()) {
                    markDelimiters(sel, node);
                }
                else if (!node.nodeMappings.isEmpty()) {
                    markNodeMappings(sel, node);
                }
                else {
                    setOpaque(false);
                    setBorder(null);
                }
            }
            return component;
        }

        private void markDelimiters(boolean selected, SourceTreeNode node) {
            setDelimitedColor(this, selected);
            setOpaque(!selected);
//            setBorder(BorderFactory.createEtchedBorder());
            setText(String.format("<html><b>%s</b> &larr; %s", node.toString(), node.isRecordRoot() ? "Record Root" : "Unique Element"));
        }

        private void markNodeMappings(boolean selected, SourceTreeNode node) {
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
            StringBuilder commaList = new StringBuilder();
            Iterator<NodeMapping> walk = node.nodeMappings.iterator();
            while (walk.hasNext()) {
                commaList.append(walk.next().recDefNode.toString());
                if (walk.hasNext()) commaList.append(", ");
            }
            setText(String.format("<html><b>%s</b> &rarr; %s", node.toString(), commaList.toString()));
        }
    }

}
