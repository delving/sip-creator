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
import eu.delving.sip.files.Storage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This model is a list of mappings that could be applied
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingHintsModel implements MappingModel.ChangeListener {
    private RecMapping mappingHints;
    private List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
    private SourceTreeNode sourceTree;
    private SipModel sipModel;

    public MappingHintsModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public void setPrefix(String metadataPrefix, RecDefModel recDefModel) {
        mappingHints = null;
        URL resource = getClass().getResource("/templates/" + String.format(Storage.FileType.MAPPING.getPattern(), metadataPrefix));
        if (resource == null) return;
        try {
            mappingHints = RecMapping.read(resource.openStream(), recDefModel);
        }
        catch (Exception e) {
            // tolerated
        }
    }
    
    public void setSourceTree(SourceTreeNode sourceTree) {
        this.sourceTree = sourceTree;
        refresh();
    }

    public void refresh() {
        boolean changed = false;
        if (!nodeMappings.isEmpty()) {
            nodeMappings.clear();
            changed = true;
        }
        if (mappingHints != null && sourceTree != null) {
            Set<Path> sourcePaths = new HashSet<Path>();
            sourceTree.getPaths(sourcePaths);
            Set<Path> mappingPaths = new HashSet<Path>();
            if (sipModel.getMappingModel().hasRecMapping()) {
                for (NodeMapping mapping : sipModel.getMappingModel().getRecMapping().getRecDefTree().getNodeMappings()) {
                    mappingPaths.add(mapping.inputPath);
                }
            }
            for (NodeMapping mapping : mappingHints.getNodeMappings()) {
                if (sourcePaths.contains(mapping.inputPath) && !mappingPaths.contains(mapping.inputPath)) {
                    nodeMappings.add(mapping);
                    changed = true;
                }
            }
        }
        if (changed) fireChanged();
    }

    @Override
    public void functionChanged(MappingModel mappingModel, MappingFunction function) {
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        refresh();
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        refresh();
    }

// observable

    public interface Listener {

        void mappingHintsChanged(List<NodeMapping> list);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Listener listener : listeners) listener.mappingHintsChanged(nodeMappings);
    }
}
