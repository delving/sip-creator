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

import eu.delving.metadata.*;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class makes a RecMapping instance observable, and also assumes that
 * there is potentially one node in its tree of RecDefNode instances which
 * is currently selected.
 * <p/>
 * The RecMapping informs us here of any changes happening in its nodes,
 * and we watch all changes in the facts and notify the world when any
 * of this changes, or when a new node is selected.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingModel implements RecDefNode.Listener {

    private RecMapping recMapping;
    private RecDefTreeNode recDefTreeRoot;

    public void setRecMapping(RecMapping recMapping) {
        this.recMapping = recMapping;
        recDefTreeRoot = null;
        if (recMapping != null) recMapping.getRecDefTree().setListener(this);
        fireRecMappingSet();
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

    public TreePath getTreePath(Path path, String discriminatorKey) {
        return getTreePath(path, discriminatorKey, getRecDefTreeRoot());
    }

    public void setFacts(Map<String, String> map) {
        recMapping.getFacts().putAll(map);
    }

    public void notifyFunctionChanged(MappingFunction mappingFunction) {
        for (ChangeListener changeListener : changeListeners) changeListener.functionChanged(this, mappingFunction);
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (ChangeListener changeListener : changeListeners)
            changeListener.nodeMappingChanged(this, recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (ChangeListener changeListener : changeListeners)
            changeListener.nodeMappingAdded(this, recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (ChangeListener changeListener : changeListeners)
            changeListener.nodeMappingRemoved(this, recDefNode, nodeMapping);
    }

    // observable

    public interface SetListener {
        void recMappingSet(MappingModel mappingModel);
    }

    public interface ChangeListener {

        void functionChanged(MappingModel mappingModel, MappingFunction function);

        void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

    }

    private TreePath getTreePath(Path path, String discriminatorKey, RecDefTreeNode node) {
        Path nodePath = node.getRecDefPath().getTagPath();
        String nodeDescriminator = node.getRecDefNode().getDiscriminatorRootKey();
        if (nodePath.equals(path)  && (discriminatorKey == null || nodeDescriminator == null || discriminatorKey.equals(nodeDescriminator))) {
            return node.getRecDefPath();
        }
        for (RecDefTreeNode sub : node.getChildren()) {
            TreePath subPath = getTreePath(path, discriminatorKey, sub);
            if (subPath != null) return subPath;
        }
        return null;
    }

    private List<SetListener> setListeners = new CopyOnWriteArrayList<SetListener>();
    private List<ChangeListener> changeListeners = new CopyOnWriteArrayList<ChangeListener>();

    public void addSetListener(SetListener listener) {
        setListeners.add(listener);
    }

    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    private void fireRecMappingSet() {
        for (SetListener listener : setListeners) listener.recMappingSet(this);
    }
}
