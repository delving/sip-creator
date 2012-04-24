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
import java.util.*;

/**
 * This model is a list of mappings that could be applied
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingHintsModel implements MappingModel.ChangeListener {
    private RecMapping mappingHints;
    private SipModel sipModel;
    private NodeMappingListModel nodeMappingListModel = new NodeMappingListModel();
    private SourceTreeNode sourceTree;

    public MappingHintsModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }

    public NodeMappingListModel getNodeMappingListModel() {
        return nodeMappingListModel;
    }

    public void initialize(String metadataPrefix, RecDefModel recDefModel) {
        fetchMappingHints(metadataPrefix, recDefModel);
    }

    public void setSourceTree(SourceTreeNode sourceTree) {
        this.sourceTree = sourceTree;
        if (sourceTree != null) fillNodeMappings();
    }

    public SortedSet<MappingFunction> getFunctions() {
        return mappingHints != null ? mappingHints.getFunctions() : null;
    }

    private void fetchMappingHints(String metadataPrefix, RecDefModel recDefModel) {
        mappingHints = null;
        URL resource = getClass().getResource("/templates/" + Storage.FileType.MAPPING.getName(metadataPrefix));
        if (resource == null) return;
        try {
            mappingHints = RecMapping.read(resource.openStream(), recDefModel);
        }
        catch (Exception e) {
            // tolerated
        }
    }

    private void fillNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        Set<Path> sourcePaths = new HashSet<Path>();
        sourceTree.getPaths(sourcePaths);
        Set<Path> mappingPaths = new HashSet<Path>();
        if (sipModel.getMappingModel().hasRecMapping()) {
            for (NodeMapping mapping : sipModel.getMappingModel().getRecMapping().getRecDefTree().getNodeMappings()) {
                mappingPaths.add(mapping.inputPath);
            }
        }
        if (mappingHints != null) for (NodeMapping mapping : mappingHints.getNodeMappings()) {
            if (sourcePaths.contains(mapping.inputPath) && !mappingPaths.contains(mapping.inputPath)) {
                nodeMappings.add(mapping);
            }
        }
        nodeMappingListModel.setList(nodeMappings);
    }

    @Override
    public void functionChanged(MappingModel mappingModel, MappingFunction function) {
    }

    @Override
    public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
    }

    @Override
    public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        if (sourceTree != null) fillNodeMappings();
    }

    @Override
    public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        if (sourceTree != null) fillNodeMappings();
    }
}
