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
import eu.delving.metadata.OptList;
import eu.delving.metadata.Path;
import eu.delving.sip.base.Exec;

import javax.swing.tree.TreePath;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.sip.files.DataSetState.MAPPING;
import static eu.delving.sip.model.CreateModel.Setter.*;

/**
 * This model holds the source and destination of a node mapping, and is observable.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateModel {
    private SipModel sipModel;
    private SortedSet<SourceTreeNode> sourceTreeNodes;
    private RecDefTreeNode recDefTreeNode;
    private NodeMapping nodeMapping;

    enum Setter {NONE, SOURCE, TARGET, RESULT}

    private Setter setter = NONE;

    public CreateModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public boolean canCreate() {
        return recDefTreeNode != null && sourceTreeNodes != null && nodeMapping == null && !sipModel.getDataSetState().atLeast(MAPPING);
    }

    public void createMapping() {
        Exec.checkWork();
        if (!canCreate()) throw new RuntimeException("Should have checked");
        NodeMapping created = new NodeMapping().setOutputPath(recDefTreeNode.getRecDefPath().getTagPath());
        created.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sourceTreeNodes, created);
        recDefTreeNode.addNodeMapping(created);
        setNodeMapping(created);
    }

    public void addMapping(NodeMapping nodeMapping) {
        Exec.checkWork();
        TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
        RecDefTreeNode recDefTreeNode = (RecDefTreeNode) treePath.getLastPathComponent();
        nodeMapping.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping), nodeMapping);
        recDefTreeNode.addNodeMapping(nodeMapping);
        setNodeMapping(nodeMapping);
    }

    public void revertToOriginal() {
        Exec.checkWork();
        if (nodeMapping != null) nodeMapping.setGroovyCode(null, null);
    }

    public SortedSet<SourceTreeNode> getSourceTreeNodes() {
        return sourceTreeNodes;
    }

    public RecDefTreeNode getRecDefTreeNode() {
        return recDefTreeNode;
    }

    public boolean nodeMappingExists() {
        return nodeMapping != null;
    }

    public void setSourceTreeNodes(SortedSet<SourceTreeNode> sourceTreeNodes) {
        Exec.checkWork();
        if (sourceTreeNodes != null && sourceTreeNodes.isEmpty()) sourceTreeNodes = null;
        this.sourceTreeNodes = sourceTreeNodes;
        Setter internalSetter = setter;
        for (Listener listener : listeners) listener.sourceTreeNodesSet(this, internalSetter != NONE);
        setter = SOURCE;
        if (internalSetter == null) {
            setNodeMapping(findExistingMapping());
            adjustHighlights();
        }
        setter = internalSetter;
    }

    public void setRecDefTreeNode(RecDefTreeNode recDefTreeNode) {
        Exec.checkWork();
        if (recDefTreeNode != null && recDefTreeNode.getParent() == null) recDefTreeNode = null;
        this.recDefTreeNode = recDefTreeNode;
        Setter internalSetter = setter;
        for (Listener listener : listeners) listener.recDefTreeNodeSet(this, internalSetter != NONE);
        setter = TARGET;
        if (internalSetter == NONE) {
            setNodeMapping(findExistingMapping());
            adjustHighlights();
        }
        setter = internalSetter;
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public void clearNodeMapping() {
        Exec.checkWork();
        nodeMapping = null;
        Setter internalSetter = setter;
        for (Listener listener : listeners) listener.nodeMappingSet(this, internalSetter != NONE);
        setter = RESULT;
        adjustHighlights();
        setter = internalSetter;
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        Exec.checkWork();
        this.nodeMapping = nodeMapping;
        Setter internalSetter = setter;
        setter = RESULT;
        for (Listener listener : listeners) listener.nodeMappingSet(this, internalSetter != NONE);
        if (internalSetter == NONE) {
            setSourceTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping));
            TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
            if (treePath != null) setRecDefTreeNode((RecDefTreeNode) treePath.getLastPathComponent());
            adjustHighlights();
        }
        setter = internalSetter;
    }

    public void removeDictionary() {
        if (nodeMapping.removeDictionary()) fireNodeMappingChanged();
    }

    public boolean isDictionaryPossible() {
        return isDictionaryPossible(nodeMapping);
    }

    public boolean isDictionaryPresent() {
        return nodeMapping != null && nodeMapping.dictionary != null;
    }

    public void fireDictionaryEntriesChanged() {
        fireNodeMappingChanged();
    }

    public void refreshDictionary() {
        if (refreshDictionary(nodeMapping)) fireNodeMappingChanged();
    }

    public int countNonemptyDictionaryEntries() {
        int nonemptyEntries = 0;
        if (isDictionaryPresent()) {
            for (String value : nodeMapping.dictionary.values()) {
                if (!value.trim().isEmpty()) nonemptyEntries++;
            }
        }
        return nonemptyEntries;
    }

    private NodeMapping findExistingMapping() {
        Exec.checkWork();
        if (!canCreate()) return null;
        nextNodeMapping:
        for (NodeMapping nodeMapping : recDefTreeNode.getRecDefNode().getNodeMappings().values()) {
            Iterator<SourceTreeNode> sourceIterator = sourceTreeNodes.iterator();
            for (Path inputPath : nodeMapping.getInputPaths()) { // do the sourceTreeNodes match those of one of the nodeMappings here?
                if (!sourceIterator.hasNext()) continue nextNodeMapping; // mismatch
                Path sourcePath = sourceIterator.next().getPath(false);
                if (!inputPath.equals(sourcePath)) continue nextNodeMapping; // mismatch
            }
            return nodeMapping;
        }
        return null;
    }

    private static boolean isDictionaryPossible(NodeMapping nodeMapping) {
        if (nodeMapping == null || nodeMapping.recDefNode == null || !nodeMapping.hasOneStatsTreeNode()) return false;
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        Set<String> values = sourceTreeNode.getStatistics().getHistogramValues();
        OptList optList = nodeMapping.recDefNode.getOptList();
        return values != null && optList != null && nodeMapping.dictionary == null;
    }

    private static boolean refreshDictionary(NodeMapping nodeMapping) {
        if (!isDictionaryPossible(nodeMapping)) throw new RuntimeException("Should have checked");
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        return nodeMapping.setDictionaryDomain(sourceTreeNode.getStatistics().getHistogramValues());
    }

    private void adjustHighlights() {
        removeAllHighlights();
        try {
            switch (setter) {
                case SOURCE:
                    if (sourceTreeNodes == null) return;
                    for (SourceTreeNode node : sourceTreeNodes) {
                        for (NodeMapping nodeMapping : node.getNodeMappings()) {
                            sipModel.getMappingModel().getNodeMappingListModel().getEntry(nodeMapping).setHighlighted();
                            sipModel.getMappingModel().getRecDefTreeRoot().getRecDefTreeNode(nodeMapping.recDefNode).setHighlighted();
                        }
                    }
                    break;
                case TARGET:
                    if (recDefTreeNode == null) return;
                    for (NodeMapping nodeMapping : recDefTreeNode.getRecDefNode().getNodeMappings().values()) {
                        NodeMappingEntry entry = sipModel.getMappingModel().getNodeMappingListModel().getEntry(nodeMapping);
                        entry.setHighlighted();
                        for (Object sourceTreeNodeObject : nodeMapping.getSourceTreeNodes()) {
                            ((SourceTreeNode) sourceTreeNodeObject).setHighlighted();
                        }
                    }
                    break;
                case RESULT:
                    if (nodeMapping == null) return;
                    for (Object node : nodeMapping.getSourceTreeNodes()) ((SourceTreeNode) node).setHighlighted();
                    RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
                    RecDefTreeNode recDefTreeNode = root.getRecDefTreeNode(nodeMapping.recDefNode);
                    recDefTreeNode.setHighlighted();
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace(); // it's only highlighting, but i want to know
        }
    }

    private void removeAllHighlights() {
        sipModel.getMappingModel().getNodeMappingListModel().clearHighlighted();
        sipModel.getStatsModel().getSourceTree().clearHighlighted();
        sipModel.getMappingModel().getRecDefTreeRoot().clearHighlighted();
    }

    // observable

    public interface Listener {

        void sourceTreeNodesSet(CreateModel createModel, boolean internal);

        void recDefTreeNodeSet(CreateModel createModel, boolean internal);

        void nodeMappingSet(CreateModel createModel, boolean internal);

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
