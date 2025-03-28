/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

import com.google.common.annotations.VisibleForTesting;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefNodeListener;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class makes a RecMapping instance observable, and provides the model for a tree component and a refreshable
 * list of all of the node mappings contained in it.
 *
 *
 */

public class MappingModel implements RecDefNodeListener {
    private RecMapping recMapping;
    private RecDefTreeNode recDefTreeRoot;
    private NodeMappingListModel nodeMappingListModel = new NodeMappingListModel();

    public MappingModel() {
    }

    public MappingModel(SipModel sipModel) {
        addSetListener(nodeMappingListModel.createSetEar(sipModel));
        addChangeListener(nodeMappingListModel.createMappingChangeEar(sipModel));
    }

    public void setRecMapping(RecMapping recMapping) {
        this.recMapping = recMapping;
        recDefTreeRoot = null;
        if (recMapping != null) recMapping.getRecDefTree().setListener(this);
        fireRecMappingSet();
    }

    public String getPrefix() {
        if (!hasRecMapping()) return "ERROR";
        return recMapping.getPrefix();
    }

    public boolean isLocked() {
        return recMapping == null || recMapping.isLocked();
    }

    public void setLocked(boolean locked) {
        if (!hasRecMapping() || locked == isLocked()) return;
        recMapping.setLocked(locked);
        notifyLockChanged(locked);
    }

    public boolean hasRecMapping() {
        return recMapping != null;
    }

    public RecMapping getRecMapping() {
        return recMapping;
    }

    public RecDefTreeNode getRecDefTreeRoot() {
        if (recDefTreeRoot == null && recMapping != null) {
            recDefTreeRoot = RecDefTreeNode.create(recMapping.getRecDefTree().getRoot());
        }
        return recDefTreeRoot;
    }

    public NodeMappingListModel getNodeMappingListModel() {
        return nodeMappingListModel;
    }

    public TreePath getTreePath(Path path) {
        return getTreePath(path, getRecDefTreeRoot());
    }

    public void setFacts(Map<String, String> map) {
        recMapping.getFacts().putAll(map);
    }

    public void setSchemaVersion(SchemaVersion schemaVersion) {
        if (schemaVersion.getPrefix().equals(recMapping.getPrefix())) recMapping.setSchemaVersion(schemaVersion);
    }

    public void notifyLockChanged(boolean locked) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.lockChanged(this, locked);
        }
    }

    public void notifyFunctionChanged(MappingFunction mappingFunction) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.functionChanged(this, mappingFunction);
        }
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.nodeMappingChanged(this, recDefNode, nodeMapping, change);
        }
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.nodeMappingAdded(this, recDefNode, nodeMapping);
        }
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.nodeMappingRemoved(this, recDefNode, nodeMapping);
        }
    }

    @Override
    public void populationChanged(RecDefNode recDefNode) {
        for (ChangeListener changeListener : changeListeners) {
            changeListener.populationChanged(this, recDefNode);
        }
    }

    // observable

    public interface SetListener {
        void recMappingSet(MappingModel mappingModel);
    }

    public interface ChangeListener {

        void lockChanged(MappingModel mappingModel, boolean locked);

        void functionChanged(MappingModel mappingModel, MappingFunction function);

        void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change);

        void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void populationChanged(MappingModel mappingModel, RecDefNode node);

    }

    public static class ChangeListenerAdapter implements ChangeListener {
        public void lockChanged(MappingModel mappingModel, boolean locked) {}

        public void functionChanged(MappingModel mappingModel, MappingFunction function) {}

        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {}

        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {}

        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {}

        public void populationChanged(MappingModel mappingModel, RecDefNode node) {}
    }

    private TreePath getTreePath(Path path, RecDefTreeNode node) {
        Path nodePath = node.getRecDefPath().getTagPath();
        if (nodePath.equals(path)) return node.getRecDefPath();
        for (RecDefTreeNode sub : node.getChildren()) {
            TreePath subPath = getTreePath(path, sub);
            if (subPath != null) return subPath;
        }
        return null;
    }

    private List<SetListener> setListeners = new CopyOnWriteArrayList<>();
    private List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    public void addSetListener(SetListener listener) {
        setListeners.add(listener);
    }

    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    private void fireRecMappingSet() {
        for (SetListener listener : setListeners) listener.recMappingSet(this);
        notifyLockChanged(isLocked());
    }
}
