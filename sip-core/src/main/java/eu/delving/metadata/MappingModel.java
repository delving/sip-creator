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

package eu.delving.metadata;

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
    private NodeMapping selectedNodeMapping;

    public void setRecMapping(RecMapping recMapping) {
        this.recMapping = recMapping;
        if (recMapping != null) recMapping.getRecDefTree().setListener(this);
        fireRecMappingSet();
    }

    public RecMapping getRecMapping() {
        return recMapping;
    }

    public NodeMapping getSelectedNodeMapping() {
        return selectedNodeMapping;
    }

    public void selectNodeMapping(NodeMapping nodeMapping) {
        this.selectedNodeMapping = nodeMapping;
        fireNodeMappingSelected();
    }

    public void setFact(String path, String value) {
        if (recMapping != null) {
            boolean changed;
            if (value == null) {
                changed = recMapping.facts.containsKey(path);
                recMapping.facts.remove(path);
            }
            else {
                changed = recMapping.facts.containsKey(path) && !recMapping.facts.get(path).equals(value);
                recMapping.facts.put(path, value);
            }
            if (changed)  fireFactChanged();
        }
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

        void factChanged(MappingModel mappingModel);

        void nodeMappingSelected(MappingModel mappingModel);

        void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

        void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping);

    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireRecMappingSet() {
        for (Listener listener : listeners) listener.recMappingSet(this);
    }

    private void fireFactChanged() {
        for (Listener listener : listeners) listener.factChanged(this);
    }

    private void fireNodeMappingSelected() {
        for (Listener listener : listeners) listener.nodeMappingSelected(this);
    }

}
