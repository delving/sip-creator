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
import static eu.delving.sip.model.CreateState.*;
import static eu.delving.sip.model.CreateTransition.*;

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
    private CreateState state = NOTHING;
    private Setter setter = NONE;

    public enum Setter {NONE, SOURCE, TARGET, NODE_MAPPING}

    public CreateModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void setSource(SortedSet<SourceTreeNode> sourceTreeNodes) {
        Exec.checkWork();
        setter = SOURCE;
        setSourceInternal(sourceTreeNodes);
        setNodeMappingInternal(findExistingMapping());
        adjustHighlights();
        fireStateChanged();
        setter = NONE;
    }

    public void setTarget(RecDefTreeNode recDefTreeNode) {
        Exec.checkWork();
        setter = TARGET;
        setTargetInternal(recDefTreeNode);
        setNodeMappingInternal(findExistingMapping());
        adjustHighlights();
        fireStateChanged();
        setter = NONE;
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        Exec.checkWork();
        setter = NODE_MAPPING;
        setNodeMappingInternal(nodeMapping);
        if (nodeMapping != null) {
            setSourceInternal(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping));
            TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
            setTargetInternal((RecDefTreeNode) treePath.getLastPathComponent());
        }
        adjustHighlights();
        fireStateChanged();
        setter = NONE;
    }

    public void createMapping() {
        Exec.checkWork();
        if (!canCreate()) throw new RuntimeException("Should have checked");
        NodeMapping created = new NodeMapping().setOutputPath(recDefTreeNode.getRecDefPath().getTagPath());
        created.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sourceTreeNodes, created);
        recDefTreeNode.addNodeMapping(created);
        setNodeMappingInternal(created);
        adjustHighlights();
        fireStateChanged();
    }

    public void addMapping(NodeMapping nodeMapping) {
        Exec.checkWork();
        TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
        RecDefTreeNode recDefTreeNode = (RecDefTreeNode) treePath.getLastPathComponent();
        nodeMapping.recDefNode = recDefTreeNode.getRecDefNode();
        SourceTreeNode.setStatsTreeNodes(sipModel.getStatsModel().findNodesForInputPaths(nodeMapping), nodeMapping);
        recDefTreeNode.addNodeMapping(nodeMapping);
        setNodeMappingInternal(nodeMapping);
        adjustHighlights();
        fireStateChanged();
    }

    public boolean canCreate() {
        return state == SOURCE_AND_TARGET && !sipModel.getDataSetState().atLeast(MAPPING);
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

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public void revertToOriginal() {
        Exec.checkWork();
        if (nodeMapping != null) nodeMapping.setGroovyCode(null, null);
    }

    public boolean isDictionaryPossible() {
        return isDictionaryPossible(nodeMapping);
    }

    public boolean isDictionaryPresent() {
        return nodeMapping != null && nodeMapping.dictionary != null;
    }

    public void refreshDictionary() {
        refreshDictionary(nodeMapping);
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

    private void setSourceInternal(SortedSet<SourceTreeNode> sourceTreeNodes) {
        this.sourceTreeNodes = sourceTreeNodes != null ? !sourceTreeNodes.isEmpty() ? sourceTreeNodes : null : null;
    }

    private void setTargetInternal(RecDefTreeNode recDefTreeNode) {
        this.recDefTreeNode = recDefTreeNode != null ? recDefTreeNode.getParent() != null ? recDefTreeNode : null : null;
    }

    private void setNodeMappingInternal(NodeMapping nodeMapping) {
        Exec.checkWork();
        this.nodeMapping = nodeMapping;
    }

    private NodeMapping findExistingMapping() {
        Exec.checkWork();
        if (sourceTreeNodes == null || recDefTreeNode == null) return null;
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
                case NODE_MAPPING:
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

        void transition(CreateModel createModel, CreateTransition transition);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireStateChanged() {
        CreateTransition transition = getTransition();
        for (Listener listener : listeners) listener.transition(this, transition);
        this.state = getState();
    }

    private CreateTransition getTransition() {
        CreateState newState = getState();
        switch (state) {
            case NOTHING:
                switch (newState) {
                    case NOTHING:
                        break; // never
                    case SOURCE_ONLY:
                        return NOTHING_TO_SOURCE;
                    case TARGET_ONLY:
                        return NOTHING_TO_TARGET;
                    case SOURCE_AND_TARGET:
                        break; // never!
                    case COMPLETE:
                        return NOTHING_TO_COMPLETE;
                }
                break;
            case SOURCE_ONLY:
                switch (newState) {
                    case NOTHING:
                        return SOURCE_TO_NOTHING;
                    case SOURCE_ONLY:
                        return SOURCE_TO_SOURCE;
                    case TARGET_ONLY:
                        break; // never!
                    case SOURCE_AND_TARGET:
                        return SOURCE_TO_ARMED;
                    case COMPLETE:
                        return SOURCE_TO_COMPLETE;
                }
                break;
            case TARGET_ONLY:
                switch (newState) {
                    case NOTHING:
                        return TARGET_TO_NOTHING;
                    case SOURCE_ONLY:
                        break; // never
                    case TARGET_ONLY:
                        return TARGET_TO_TARGET;
                    case SOURCE_AND_TARGET:
                        return TARGET_TO_ARMED;
                    case COMPLETE:
                        return TARGET_TO_COMPLETE;
                }
                break;
            case SOURCE_AND_TARGET:
                switch (newState) {
                    case NOTHING:
                        break; // never
                    case SOURCE_ONLY:
                        return ARMED_TO_SOURCE;
                    case TARGET_ONLY:
                        return ARMED_TO_TARGET;
                    case SOURCE_AND_TARGET:
                        switch (setter) {
                            case NONE:
                                break; // never
                            case SOURCE:
                                return ARMED_TO_ARMED_SOURCE;
                            case TARGET:
                                return ARMED_TO_ARMED_TARGET;
                            case NODE_MAPPING:
                                break; // never
                        }
                        break;
                    case COMPLETE:
                        switch (setter) {
                            case NONE:
                                return CREATE_COMPLETE;
                            case SOURCE:
                                return ARMED_TO_COMPLETE_SOURCE;
                            case TARGET:
                                return ARMED_TO_COMPLETE_TARGET;
                            case NODE_MAPPING:
                                return COMPLETE_TO_COMPLETE;
                        }
                }
                break;
            case COMPLETE:
                switch (newState) {
                    case NOTHING:
                        break; // never
                    case SOURCE_ONLY:
                        break; // never
                    case TARGET_ONLY:
                        break; // never
                    case SOURCE_AND_TARGET:
                        switch (setter) {
                            case NONE:
                                break; // never
                            case SOURCE:
                                return COMPLETE_TO_ARMED_SOURCE;
                            case TARGET:
                                return COMPLETE_TO_ARMED_TARGET;
                            case NODE_MAPPING:
                                break; // never
                        }
                    case COMPLETE:
                        return COMPLETE_TO_COMPLETE;
                }
                break;
        }
        throw new IllegalStateException("No transition available from " + state + " to " + newState);
    }

    private CreateState getState() {
        if (sourceTreeNodes != null) {
            return recDefTreeNode != null ? nodeMapping != null ? COMPLETE : SOURCE_AND_TARGET : SOURCE_ONLY;
        }
        else {
            return recDefTreeNode != null ? TARGET_ONLY : NOTHING;
        }
    }
}
