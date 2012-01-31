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
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class makes a RecMapping instance observable, and also assumes that
 * there is potentially one node in its tree of RecDefNode instances which
 * is currently selected.
 *
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
    
    public TreePath getTreePath(Path path) {
        return getTreePath(path, getRecDefTreeRoot());
    }

    public void setFact(String path, String value) {
        if (recMapping != null) {
            boolean changed;
            if (value == null) {
                changed = recMapping.getFacts().containsKey(path);
                recMapping.getFacts().remove(path);
            }
            else {
                changed = recMapping.getFacts().containsKey(path) && !recMapping.getFacts().get(path).equals(value);
                recMapping.getFacts().put(path, value);
            }
            if (changed) {
                for (Listener listener : listeners) listener.factChanged(this, path);
            }
        }
    }
    
    public void setFunction(String name, String value) {
        if (recMapping != null) {
            recMapping.getFunctions().put(name, value);
            for (Listener listener : listeners) listener.functionChanged(this, name);
        }
    }
    
    public String getFunctionCode(String name) {
        String code = "it";
        if (recMapping != null) {
            String existing = recMapping.getFunctions().get(name);
            if (existing != null) code = existing;
        }
        return code;
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (Listener listener : listeners) listener.nodeMappingAdded(this, recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        for (Listener listener : listeners) listener.nodeMappingRemoved(this, recDefNode, nodeMapping);
    }

    // observable

    public interface Listener {

        void recMappingSet(MappingModel mappingModel);

        void factChanged(MappingModel mappingModel, String name);
        
        void functionChanged(MappingModel mappingModel, String name);

        void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

    }

    private TreePath getTreePath(Path path, RecDefTreeNode node) {
        if (node.getRecDefPath().getTagPath().equals(path)) {
            return node.getRecDefPath();
        }
        for (RecDefTreeNode sub : node.getChildren()) {
            TreePath subPath = getTreePath(path, sub);
            if (subPath != null) return subPath;
        }
        return null;
    }


    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireRecMappingSet() {
        for (Listener listener : listeners) listener.recMappingSet(this);
    }
}
