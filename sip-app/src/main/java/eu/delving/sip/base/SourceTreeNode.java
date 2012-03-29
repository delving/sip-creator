/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.FilterTreeNode;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.regex.Pattern;

import static eu.delving.sip.base.Utility.DELIMITER_HILITE;
import static eu.delving.sip.base.Utility.MAPPED_HILITE;

/**
 * A node of the analysis tree
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceTreeNode implements Comparable<SourceTreeNode>, FilterTreeNode {
    private SourceTreeNode parent;
    private List<SourceTreeNode> children = new ArrayList<SourceTreeNode>();
    private Tag tag;
    private boolean recordRoot, uniqueElement;
    private FieldStatistics fieldStatistics;
    private String htmlChunk;
    private List<NodeMapping> mappedIn = new ArrayList<NodeMapping>();
    private DefaultTreeModel treeModel;
    private boolean passesFilter = true;

    public static SourceTreeNode create(String rootTag) {
        return new SourceTreeNode(rootTag, "<h3>Root</h3>");
    }

    public static SourceTreeNode create(List<FieldStatistics> fieldStatisticsList, Map<String,String> facts) {
        SourceTreeNode root = createSubtree(fieldStatisticsList, Path.empty(), null);
        if (root == null) {
            root = new SourceTreeNode("No statistics", "<h3>No statistics</h3>");
        }
        if (root.getTag().toString().equals(Storage.ENVELOPE_TAG)) {
            SourceTreeNode factNode = new SourceTreeNode(root, Storage.FACTS_TAG, "<h3>Select a fact from here</h3>");
            root.getChildren().add(0, factNode);
            for (Map.Entry<String,String> entry : facts.entrySet()) new SourceTreeNode(factNode, entry);
            // todo: derived fields?
//            SourceTreeNode derivedNode = new SourceTreeNode(root, Storage.DERIVED_TAG, "<h3>Select a derived fields from here</h3>");
        }
        return root;
    }

    private SourceTreeNode(SourceTreeNode parent, String name, String htmlChunk) {
        this.parent = parent;
        this.tag = Tag.create(name);
        this.htmlChunk = htmlChunk;
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
        StringTemplate template = Utility.getTemplate("fact-brief");
        template.setAttribute("fact", entry);
        this.htmlChunk = template.toString();
    }

    private SourceTreeNode(SourceTreeNode parent, FieldStatistics fieldStatistics) {
        this(parent, fieldStatistics.getPath().peek());
        setStatistics(fieldStatistics);
    }

    public void setStatistics(FieldStatistics fieldStatistics) {
        this.fieldStatistics = fieldStatistics;
        StringTemplate template = Utility.getTemplate("stats-brief");
        template.setAttribute("stats", fieldStatistics);
        this.htmlChunk = template.toString();
    }

    public List<SourceTreeNode> getChildren() {
        return children;
    }

    public boolean hasStatistics() {
        return fieldStatistics != null;
    }

    public FieldStatistics getStatistics() {
        return fieldStatistics;
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
        Path path = Path.empty();
        for (SourceTreeNode node : list) path.push(node.getTag());
        return path;
    }

    public int setRecordRoot(Path recordRoot) {
        boolean oldValue = this.recordRoot;
        this.recordRoot = recordRoot != null && getPath(true).equals(recordRoot);
        if (this.recordRoot || this.recordRoot != oldValue) treeModel.nodeChanged(this);
        int childTotal = 0;
        for (SourceTreeNode child : children) {
            int subtotal = child.setRecordRoot(recordRoot);
            if (subtotal > 0) childTotal = subtotal;
        }
        return this.recordRoot ? getStatistics().getTotal() : childTotal;
    }

    public void setUniqueElement(Path uniqueElement) {
        boolean oldValue = this.uniqueElement;
        this.uniqueElement = uniqueElement != null && getPath(true).equals(uniqueElement);
        if (this.uniqueElement != oldValue) treeModel.nodeChanged(this);
        for (SourceTreeNode child : children) child.setUniqueElement(uniqueElement);
    }

    public boolean isRecordRoot() {
        return recordRoot;
    }

    public boolean isUniqueElement() {
        return uniqueElement;
    }

    public Iterable<? extends SourceTreeNode> getChildNodes() {
        return children;
    }

    public boolean couldBeRecordRoot() {
        return fieldStatistics != null && !fieldStatistics.hasValues();
    }

    public boolean couldBeUniqueElement() {
        if (couldBeRecordRoot()) return false;
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
        SourceTreeNode qNameNode = (SourceTreeNode) treeNode;
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
        return new Vector<SourceTreeNode>(children).elements();
    }

    @Override
    public int compareTo(SourceTreeNode other) {
        return getPath(true).compareTo(other.getPath(true));
    }

    public String toHtml() {
        return String.format("<html>%s</html>", toHtmlChunk());
    }

    public String toHtmlChunk() {
        return htmlChunk;
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

    public static void setStatsTreeNodes(SortedSet<SourceTreeNode> nodes, NodeMapping nodeMapping) {
        List<Path> inputPaths = new ArrayList<Path>();
        for (SourceTreeNode node : nodes) {
            inputPaths.add(node.getPath(false));
            node.addMappedIn(nodeMapping);
        }
        nodeMapping.setStatsTreeNodes(nodes, inputPaths);
    }

    private void addMappedIn(NodeMapping nodeMapping) {
        this.mappedIn.add(nodeMapping);
        this.treeModel.nodeChanged(this);
    }

    public static void removeStatsTreeNodes(NodeMapping nodeMapping) {
        if (nodeMapping.hasStatsTreeNodes()) {
            for (Object nodeObject : nodeMapping.getStatsTreeNodes()) {
                ((SourceTreeNode) nodeObject).removeMappedIn(nodeMapping);
            }
        }
    }

    private void removeMappedIn(NodeMapping nodeMapping) {
        if (this.mappedIn.remove(nodeMapping)) this.treeModel.nodeChanged(this);
    }

    public void setTreeModel(DefaultTreeModel treeModel) {
        this.treeModel = treeModel;
        for (SourceTreeNode child : children) child.setTreeModel(treeModel);
    }

    private static SourceTreeNode createSubtree(List<FieldStatistics> fieldStatisticsList, Path path, SourceTreeNode parent) {
        Map<Tag, List<FieldStatistics>> statisticsMap = new TreeMap<Tag, List<FieldStatistics>>();
        for (FieldStatistics fieldStatistics : fieldStatisticsList) {
            Path subPath = fieldStatistics.getPath().chop(path.size());
            if (subPath.equals(path) && fieldStatistics.getPath().size() == path.size() + 1) {
                Tag tag = fieldStatistics.getPath().getTag(path.size());
                if (tag != null) {
                    List<FieldStatistics> list = statisticsMap.get(tag);
                    if (list == null) statisticsMap.put(tag, list = new ArrayList<FieldStatistics>());
                    list.add(fieldStatistics);
                }
            }
        }
        if (statisticsMap.isEmpty()) return null;
        Tag tag = path.peek();
        SourceTreeNode node = tag == null ? null : new SourceTreeNode(parent, tag);
        for (Map.Entry<Tag, List<FieldStatistics>> entry : statisticsMap.entrySet()) {
            Path childPath = path.copy();
            childPath.push(entry.getKey());
            FieldStatistics fieldStatisticsForChild = null;
            for (FieldStatistics fieldStatistics : entry.getValue()) {
                if (fieldStatistics.getPath().equals(childPath)) fieldStatisticsForChild = fieldStatistics;
            }
            SourceTreeNode child = createSubtree(fieldStatisticsList, childPath, node);
            if (child != null) {
                if (node == null) node = child;
                child.setStatistics(fieldStatisticsForChild);
            }
            else if (fieldStatisticsForChild != null) {
                SourceTreeNode fresh = new SourceTreeNode(node, fieldStatisticsForChild);
                if (node == null) node = fresh;
            }
        }
        return node;
    }

    @Override
    public void filter(Pattern pattern) {
        if (!passesFilter) {
            boolean found = pattern.matcher(tag.toString()).find();
            if (found) {
                setPassesFilter(true);
                for (SourceTreeNode mark = this.parent; mark != null; mark = mark.parent) mark.passesFilter = true;
            }
        }
        for (SourceTreeNode sub : children) sub.filter(pattern);
    }

    @Override
    public void setPassesFilter(boolean passesFilter) {
        this.passesFilter = passesFilter;
        for (SourceTreeNode sub : children) sub.setPassesFilter(passesFilter);
    }

    @Override
    public boolean passesFilter() {
        return passesFilter;
    }

    public static class Renderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof SourceTreeNode) {
                SourceTreeNode node = (SourceTreeNode) value;
                if (node.getTag().isAttribute()) {
                    setIcon(Utility.ATTRIBUTE_ICON);
                }
                else if (node.getChildNodes().iterator().hasNext()) {
                    setIcon(Utility.COMPOSITE_ELEMENT_ICON);
                }
                else {
                    setIcon(Utility.VALUE_ELEMENT_ICON);
                }
                if (node.isRecordRoot() || node.isUniqueElement()) {
                    markDelimiters(sel, node);
                }
                else if (!node.mappedIn.isEmpty()) {
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
            setOpaque(!selected);
            setBackground(selected ? Color.WHITE : DELIMITER_HILITE);
            setForeground(selected ? DELIMITER_HILITE : Color.BLACK);
            setBorder(BorderFactory.createEtchedBorder());
            setText(String.format("<html><b>%s</b> &larr; %s", node.toString(), node.isRecordRoot() ? "Record Root" : "Unique Element"));
        }

        private void markNodeMappings(boolean selected, SourceTreeNode node) {
            setOpaque(!selected);
            setBackground(selected ? Color.WHITE : MAPPED_HILITE);
            setForeground(selected ? MAPPED_HILITE : Color.BLACK);
            setBorder(BorderFactory.createEtchedBorder());
            StringBuilder commaList = new StringBuilder();
            Iterator<NodeMapping> walk = node.mappedIn.iterator();
            while (walk.hasNext()) {
                commaList.append(walk.next().recDefNode.toString());
                if (walk.hasNext()) commaList.append(", ");
            }
            setText(String.format("<html><b>%s</b> &rarr; %s", node.toString(), commaList.toString()));
        }
    }

}
