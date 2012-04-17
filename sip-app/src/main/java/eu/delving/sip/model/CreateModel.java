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

package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;

import javax.swing.tree.TreePath;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This model holds the source and destination of a node mapping, and is observable.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateModel {
    private SipModel sipModel;
    private SortedSet<SourceTreeNode> sourceTreeNodes;
    private RecDefTreeNode recDefTreeNode;
    private boolean settingNodeMapping;
    private NodeMappingEntry nodeMappingEntry;

    public CreateModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void setSourceTreeNodes(SortedSet<SourceTreeNode> sourceTreeNodes) {
        if (sourceTreeNodes != null && sourceTreeNodes.isEmpty()) sourceTreeNodes = null;
        this.sourceTreeNodes = sourceTreeNodes;
        if (!settingNodeMapping) {
            clearNodeMapping();
            clearHighlights();
            sipModel.getMappingModel().getRecDefTreeRoot().clearHighlighted();
            if (sourceTreeNodes != null) {
                for (SourceTreeNode node : sourceTreeNodes) {
                    for (NodeMapping nodeMapping : node.getNodeMappings()) {
                        NodeMappingEntry entry = sipModel.getMappingModel().getNodeMappingListModel().getEntry(nodeMapping);
                        if (entry != null) entry.setHighlighted(); // todo: fix
                        RecDefTreeNode recDefTreeNode = sipModel.getMappingModel().getRecDefTreeRoot().getRecDefTreeNode(nodeMapping.recDefNode);
                        recDefTreeNode.setHighlighted();
                    }
                }
            }
        }
        for (Listener listener : listeners) listener.sourceTreeNodesSet(this);
    }

    public void setRecDefTreePath(Path path) {
        TreePath treePath = sipModel.getMappingModel().getTreePath(path);
        RecDefTreeNode node = ((RecDefTreeNode) (treePath.getLastPathComponent()));
        setRecDefTreeNode(node);
    }

    public void setRecDefTreeNode(RecDefTreeNode recDefTreeNode) {
        if (recDefTreeNode != null && recDefTreeNode.getParent() == null) recDefTreeNode = null;
        this.recDefTreeNode = recDefTreeNode;
        if (!settingNodeMapping) {
            clearNodeMapping();
            clearHighlights();
            sipModel.getStatsModel().getSourceTree().clearHighlighted();
            if (recDefTreeNode != null) {
                for (NodeMapping nodeMapping : recDefTreeNode.getRecDefNode().getNodeMappings().values()) {
                    NodeMappingEntry entry = sipModel.getMappingModel().getNodeMappingListModel().getEntry(nodeMapping); // todo: threading??
                    if (entry != null) entry.setHighlighted(); // todo: fix
                    for (Object sourceTreeNodeObject : nodeMapping.getSourceTreeNodes()) {
                        ((SourceTreeNode) sourceTreeNodeObject).setHighlighted();
                    }
                }
            }
        }
        for (Listener listener : listeners) listener.recDefTreeNodeSet(this);
    }

    public void clearNodeMapping() {
        nodeMappingEntry = null;
        for (Listener listener : listeners) listener.nodeMappingSet(this);
    }

    public void setNodeMapping(NodeMappingEntry entry) {
        this.nodeMappingEntry = entry;
        setNodeMapping(entry.getNodeMapping());
    }

    public void setNodeMapping(NodeMapping nodeMapping) { // todo: make this bullet-proof, avoid all NPE
        assert nodeMapping != null;
        settingNodeMapping = true;
        if (nodeMappingEntry == null || nodeMappingEntry.getNodeMapping() != nodeMapping) {
            nodeMappingEntry = sipModel.getMappingModel().getNodeMappingListModel().addEntry(nodeMapping);
        }
        setSourceTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping));
        TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
        if (treePath != null) setRecDefTreeNode((RecDefTreeNode) treePath.getLastPathComponent());
        clearHighlights();
        for (Object sourceTreeNodeObject : nodeMapping.getSourceTreeNodes()) {
            ((SourceTreeNode) sourceTreeNodeObject).setHighlighted();
        }
        RecDefTreeNode recDefTreeNode = sipModel.getMappingModel().getRecDefTreeRoot().getRecDefTreeNode(nodeMapping.recDefNode);
        recDefTreeNode.setHighlighted();
        settingNodeMapping = false;
        for (Listener listener : listeners) listener.nodeMappingSet(this);
    }

    public SortedSet<SourceTreeNode> getSourceTreeNodes() {
        return sourceTreeNodes;
    }

    public RecDefTreeNode getRecDefTreeNode() {
        return recDefTreeNode;
    }

    public NodeMappingEntry getNodeMappingEntry() {
        return nodeMappingEntry;
    }

    public void revertToOriginal() {
        if (nodeMappingEntry != null) nodeMappingEntry.getNodeMapping().setGroovyCode(null, null);
    }

    public boolean canCreate() {
        if (recDefTreeNode != null && sourceTreeNodes != null) {
            if (nodeMappingEntry == null) {
                nextNodeMapping:
                for (NodeMapping mapping : recDefTreeNode.getRecDefNode().getNodeMappings().values()) {
                    Iterator<SourceTreeNode> sourceIterator = sourceTreeNodes.iterator();
                    for (Path inputPath : mapping.getInputPaths()) {
                        if (!sourceIterator.hasNext() || !inputPath.equals(sourceIterator.next().getPath(false)))
                            continue nextNodeMapping;
                    }
                    setNodeMapping(mapping); // todo: this is a side effect
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public void createMapping() {
        if (!canCreate()) throw new RuntimeException("Should have checked");
        NodeMapping created = new NodeMapping().setOutputPath(recDefTreeNode.getRecDefPath().getTagPath());
        created.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sourceTreeNodes, created);
        recDefTreeNode.addNodeMapping(created);

        setNodeMapping(created);
    }

    public void createMapping(NodeMapping nodeMapping) {
        TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
        RecDefTreeNode recDefTreeNode = (RecDefTreeNode) treePath.getLastPathComponent();
        nodeMapping.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping), nodeMapping);
        recDefTreeNode.addNodeMapping(nodeMapping);
        setNodeMapping(nodeMapping);
    }

    private static boolean isDictionaryPossible(NodeMappingEntry nodeMappingEntry) {
        if (nodeMappingEntry == null) return false;
        NodeMapping nodeMapping = nodeMappingEntry.getNodeMapping();
        if (nodeMapping == null || nodeMapping.recDefNode == null || !nodeMapping.hasOneStatsTreeNode()) return false;
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        Set<String> values = sourceTreeNode.getStatistics().getHistogramValues();
        List<String> options = nodeMapping.recDefNode.getOptions();
        return values != null && options != null && nodeMapping.dictionary == null;
    }

    private static boolean refreshDictionary(NodeMappingEntry nodeMappingEntry) {
        if (!isDictionaryPossible(nodeMappingEntry)) throw new RuntimeException("Should have checked");
        NodeMapping nodeMapping = nodeMappingEntry.getNodeMapping();
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        return nodeMapping.setDictionaryDomain(sourceTreeNode.getStatistics().getHistogramValues());
    }

    public void removeDictionary() {
        if (nodeMappingEntry.getNodeMapping().removeDictionary()) fireNodeMappingChanged();
    }

    public boolean isDictionaryPossible() {
        return isDictionaryPossible(nodeMappingEntry);
    }

    public boolean isDictionaryPresent() {
        return nodeMappingEntry != null && nodeMappingEntry.getNodeMapping().dictionary != null;
    }

    public void fireDictionaryEntriesChanged() {
        fireNodeMappingChanged();
    }

    public void refreshDictionary() {
        if (refreshDictionary(nodeMappingEntry)) fireNodeMappingChanged();
    }

    public int countNonemptyDictionaryEntries() {
        int nonemptyEntries = 0;
        if (isDictionaryPresent()) {
            for (String value : nodeMappingEntry.getNodeMapping().dictionary.values())
                if (!value.trim().isEmpty()) nonemptyEntries++;
        }
        return nonemptyEntries;
    }

    private void clearHighlights() {
        sipModel.getMappingModel().getNodeMappingListModel().clearHighlighted();
        sipModel.getStatsModel().getSourceTree().clearHighlighted();
        sipModel.getMappingModel().getRecDefTreeRoot().clearHighlighted();
    }

    // observable

    public interface Listener {

        void sourceTreeNodesSet(CreateModel createModel);

        void recDefTreeNodeSet(CreateModel createModel);

        void nodeMappingSet(CreateModel createModel);

        void nodeMappingChanged(CreateModel createModel);

    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireNodeMappingChanged() {
        for (Listener listener : listeners) listener.nodeMappingChanged(this);
    }
}
