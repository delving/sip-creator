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
import static eu.delving.sip.model.SourceTreeNode.NodeType.*;

/**
 * A node of the analysis tree, where the statistics are stored, and also the node mappings associated
 * with this source tree node so this info can be shown in the interface.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceTreeNode extends FilterNode implements Comparable<SourceTreeNode> {
    private SourceTreeNode parent;
    private List<SourceTreeNode> children = new ArrayList<SourceTreeNode>();
    private Tag tag;
    private NodeType nodeType = NORMAL;
    private Stats.ValueStats valueStats;
    private String htmlToolTip, htmlDetails;
    private Set<NodeMapping> nodeMappings = new HashSet<NodeMapping>();

    public enum NodeType {
        NORMAL, RECORD_ROOT, UNIQUE_ELEMENT, CONSTANT, FACT
    }

    public static SourceTreeNode create(String rootTag) {
        return new SourceTreeNode(rootTag, "<h3>Root</h3>");
    }

    public static SourceTreeNode create(Map<Path, Stats.ValueStats> pathMap, Map<String, String> facts) {
        SourceTreeNode root = createSubtree(pathMap, Path.create(), null);
        if (root == null) {
            root = new SourceTreeNode("No statistics", "<h3>No statistics</h3>");
        }
        else if (root.getTag().toString().equals(Storage.SOURCE_ROOT_TAG)) {
            SourceTreeNode factNode = new SourceTreeNode(root, Storage.FACTS_TAG, FACT, "<h3>Select a fact from here</h3>");
            root.getChildren().add(0, factNode);
            for (Map.Entry<String, String> entry : facts.entrySet()) new SourceTreeNode(factNode, FACT, entry);
            SourceTreeNode constantNode = new SourceTreeNode(root, Storage.CONSTANT_TAG, CONSTANT, "<html>Constant value which you can adjust in the code snippet");
            root.getChildren().add(0, constantNode);
            // todo: the constant children
        }
        return root;
    }

    private SourceTreeNode(SourceTreeNode parent, String name, NodeType nodeType, String htmlChunk) {
        this.parent = parent;
        this.tag = Tag.create(name);
        this.nodeType = nodeType;
        this.htmlToolTip = this.htmlDetails = htmlChunk;
    }

    private SourceTreeNode(String name, String htmlChunk) {
        this(null, name, NORMAL, htmlChunk);
    }

    private SourceTreeNode(SourceTreeNode parent, NodeType nodeType, Tag tag) {
        if ((this.parent = parent) != null) parent.children.add(this);
        this.nodeType = nodeType;
        this.tag = tag;
    }

    private SourceTreeNode(SourceTreeNode parent, NodeType nodeType, Map.Entry<String, String> entry) {
        this(parent, nodeType, Tag.create(entry.getKey()));
        this.htmlToolTip = this.htmlDetails = SwingHelper.factEntryToHTML(entry);
    }

    private SourceTreeNode(SourceTreeNode parent, Tag tag, Stats.ValueStats valueStats) {
        this(parent, NORMAL, tag);
        setStats(valueStats);
    }

    public void setStats(Stats.ValueStats valueStats) {
        this.valueStats = valueStats;
        this.htmlToolTip = SwingHelper.statsHTML(getPath(true), valueStats, false);
        this.htmlDetails = SwingHelper.statsHTML(getPath(true), valueStats, true);
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
        if (recordRoot != null && getPath(true).equals(recordRoot)) {
            nodeType = RECORD_ROOT;
            fireChanged();
            return getStats().total;
        }
        if (nodeType == RECORD_ROOT) {
            nodeType = NORMAL;
            fireChanged();
        }
        int childTotal = 0;
        for (SourceTreeNode child : children) {
            int subtotal = child.setRecordRoot(recordRoot);
            if (subtotal > 0) childTotal = subtotal;
        }
        return childTotal;
    }

    public void setUniqueElement(Path uniqueElement) {
        if (uniqueElement != null && getPath(true).equals(uniqueElement)) {
            nodeType = UNIQUE_ELEMENT;
            fireChanged();
            return;
        }
        if (nodeType == UNIQUE_ELEMENT) {
            nodeType = NORMAL;
            fireChanged();
        }
        for (SourceTreeNode child : children) child.setUniqueElement(uniqueElement);
    }

    public boolean isRecordRoot() {
        return nodeType == RECORD_ROOT;
    }

    public boolean isUniqueElement() {
        return nodeType == UNIQUE_ELEMENT;
    }

    public boolean couldBeRecordRoot() {
        return valueStats != null && !valueStats.hasValues();
    }

    public boolean couldBeUniqueElement() {
        if (valueStats == null || !valueStats.hasValues()) return false;
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
        for (SourceTreeNode node : nodes) inputPaths.add(node.getPath(false));
        nodeMapping.setStatsTreeNodes(nodes, inputPaths);
        for (SourceTreeNode node : nodes) node.addMappedIn(nodeMapping);
    }

    public static void removeStatsTreeNodes(final NodeMapping nodeMapping) {
        if (nodeMapping.hasSourceTreeNodes()) {
            for (Object nodeObject : nodeMapping.getSourceTreeNodes()) {
                ((SourceTreeNode) nodeObject).removeMappedIn(nodeMapping);
            }
        }
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
        SourceTreeNode node = tag == null ? null : new SourceTreeNode(parent, NORMAL, tag);
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
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof SourceTreeNode) {
                SourceTreeNode node = (SourceTreeNode) value;
                setHorizontalTextPosition(RIGHT);
                setVerticalTextPosition(TOP);
                if (node.getTag().isAttribute()) {
                    setIcon(SwingHelper.ICON_ATTRIBUTE);
                }
                else if (node.getChildren().iterator().hasNext()) {
                    setIcon(SwingHelper.ICON_COMPOSITE);
                }
                else {
                    setIcon(SwingHelper.ICON_VALUE);
                }
                switch (node.nodeType) {
                    case NORMAL:
                        setSourceNodeColor(this, node, selected, MAPPED_COLOR);
                        setText(node);
                        break;
                    case RECORD_ROOT:
                        setDelimitedColor(this, selected);
                        setOpaque(!this.selected);
                        setText(String.format("<html><b>%s</b> &larr; %s", node.toString(), "Record Root"));
                        break;
                    case UNIQUE_ELEMENT:
                        setDelimitedColor(this, selected);
                        setOpaque(!this.selected);
                        setText(String.format("<html><b>%s</b> &larr; %s", node.toString(), "Unique Element"));
                        break;
                    case CONSTANT:
                        setSourceNodeColor(this, node, selected, CONSTANT_COLOR);
                        setConstantText(node);
                        break;
                    case FACT:
                        setSourceNodeColor(this, node, selected, FACT_COLOR);
                        setText(node);
                        break;
                    default:
                        throw new RuntimeException();
                }
            }
            return component;
        }

        private void setText(SourceTreeNode node) {
            if (node.getNodeMappings().isEmpty()) {
                setText(node.toString());
            }
            else {
                StringBuilder commaList = new StringBuilder();
                Iterator<NodeMapping> walk = node.nodeMappings.iterator();
                while (walk.hasNext()) {
                    commaList.append(walk.next().recDefNode.toString());
                    if (walk.hasNext()) commaList.append(", ");
                }
                setText(String.format("<html><b>%s</b> &rarr; %s", node.toString(), commaList.toString()));
            }
        }

        private void setConstantText(SourceTreeNode node) {
            if (node.getNodeMappings().isEmpty()) {
                setText(node.toString());
            }
            else {
                StringBuilder html = new StringBuilder(String.format("<html><table><tr><th align=left>%s</th><th/></tr><tr><td>", node.toString()));
                Iterator<NodeMapping> walk = node.nodeMappings.iterator();
                int maxWidth = 80, width = 0;
                while (walk.hasNext()) {
                    NodeMapping nodeMapping = walk.next();
                    String addition = String.format("\"%s\" &rarr %s", nodeMapping.getConstantValue(), nodeMapping.recDefNode.toString());
                    width += addition.length();
                    if (width > maxWidth) {
                        html.append("<br/>");
                        width = addition.length();
                    }
                    html.append(addition);
                    if (walk.hasNext()) html.append(", ");
                }
                html.append("</td></tr></table>");
                setText(html.toString());
            }
        }

    }

}
