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

package eu.delving.sip.base;

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import eu.delving.sip.files.Storage;

import javax.swing.tree.DefaultTreeModel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tree representing the statistics gathered
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class StatsTree implements Serializable {
    private StatsTreeNode root;

    public static int setRecordRoot(DefaultTreeModel model, Path recordRoot) {
        StatsTreeNode node = (StatsTreeNode) model.getRoot();
        List<StatsTreeNode> changedNodes = new ArrayList<StatsTreeNode>();
        int count = setRecordRoot(node, recordRoot, changedNodes);
        for (StatsTreeNode changedNode : changedNodes) {
            model.nodeChanged(changedNode);
        }
        return count;
    }

    public static void setUniqueElement(DefaultTreeModel model, Path uniqueElement) {
        StatsTreeNode node = (StatsTreeNode) model.getRoot();
        List<StatsTreeNode> changedNodes = new ArrayList<StatsTreeNode>();
        setUniqueElement(node, uniqueElement, changedNodes);
        for (StatsTreeNode changedNode : changedNodes) {
            model.nodeChanged(changedNode);
        }
    }

    public static StatsTree create(String rootTag) {
        return new StatsTree(new StatsTreeNode(rootTag, "<h3>Root</h3>"));
    }

    public static StatsTree create(List<FieldStatistics> fieldStatisticsList, Map<String,String> facts) {
        StatsTreeNode root = createSubtree(fieldStatisticsList, Path.empty(), null);
        if (root == null) {
            root = new StatsTreeNode("No statistics", "<h3>No statistics</h3>");
        }
        if (root.getTag().toString().equals(Storage.ENVELOPE_TAG)) {
            StatsTreeNode factNode = new StatsTreeNode(Storage.FACTS_TAG, "<h3>Select a fact from here</h3>");
            root.getChildren().add(0, factNode);
            for (Map.Entry<String,String> entry : facts.entrySet()) new StatsTreeNode(factNode, entry);
        }
        return new StatsTree(root);
    }

    public StatsTreeNode getRoot() {
        return root;
    }

    public void getVariables(List<StatsTreeNode> variables) {
        getVariables(root, false, variables);
    }

    // ==== privates

    private StatsTree(StatsTreeNode root) {
        this.root = root;
    }

    private static int setRecordRoot(StatsTreeNode node, Path recordRoot, List<StatsTreeNode> changedNodes) {
        if (node.setRecordRoot(recordRoot)) changedNodes.add(node);
        int childTotal = 0;
        for (StatsTreeNode child : node.getChildNodes()) {
            int subtotal = setRecordRoot(child, recordRoot, changedNodes);
            if (subtotal > 0) childTotal = subtotal;
        }
        return node.isRecordRoot() ? node.getStatistics().getTotal() : childTotal;
    }

    private static void setUniqueElement(StatsTreeNode node, Path uniqueElement, List<StatsTreeNode> changedNodes) {
        if (node.setUniqueElement(uniqueElement)) changedNodes.add(node);
        if (uniqueElement == null || !node.isUniqueElement()) {
            for (StatsTreeNode child : node.getChildNodes()) setUniqueElement(child, uniqueElement, changedNodes);
        }
    }

    private static void getVariables(StatsTreeNode node, boolean withinRecord, List<StatsTreeNode> variables) {
        if (withinRecord && node.hasStatistics() && !node.getTag().isAttribute()) variables.add(node);
        for (StatsTreeNode child : node.getChildren()) {
            getVariables(child, withinRecord || node.isRecordRoot(), variables);
        }
    }

    private static StatsTreeNode createSubtree(List<FieldStatistics> fieldStatisticsList, Path path, StatsTreeNode parent) {
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
        StatsTreeNode node = tag == null ? null : new StatsTreeNode(parent, tag);
        for (Map.Entry<Tag, List<FieldStatistics>> entry : statisticsMap.entrySet()) {
            Path childPath = path.copy();
            childPath.push(entry.getKey());
            FieldStatistics fieldStatisticsForChild = null;
            for (FieldStatistics fieldStatistics : entry.getValue()) {
                if (fieldStatistics.getPath().equals(childPath)) fieldStatisticsForChild = fieldStatistics;
            }
            StatsTreeNode child = createSubtree(fieldStatisticsList, childPath, node);
            if (child != null) {
                if (node == null) node = child;
                child.setStatistics(fieldStatisticsForChild);
            }
            else if (fieldStatisticsForChild != null) {
                StatsTreeNode fresh = new StatsTreeNode(node, fieldStatisticsForChild);
                if (node == null) node = fresh;
            }
        }
        return node;
    }

}
