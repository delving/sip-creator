/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.sip.base.Swing;
import eu.delving.sip.files.Storage;

import java.net.URL;
import java.util.*;

/**
 * This model is a list of mappings that could be added to the current mapping, as well as the list of functions
 * that can also be a part of a mapping.  To feed this model, all we have to do is put a mapping file in the
 * associated resource directory /templates/.  Additions can be copy-pasted from successful mappings.  Selection of
 * which node mappings may be relevant is based on the source paths, so only node mappings with recognized
 * source paths will appear.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingHintsModel {
    private RecMapping mappingHints;
    private SipModel sipModel;
    private MappingEar mappingEar = new MappingEar();
    private NodeMappingListModel nodeMappingListModel = new NodeMappingListModel();
    private SourceTreeNode sourceTree;

    public MappingHintsModel(SipModel sipModel) {
        this.sipModel = sipModel;
    }


    public MappingEar getMappingChangeListener() {
        return mappingEar;
    }

    public NodeMappingListModel getNodeMappingListModel() {
        return nodeMappingListModel;
    }

    public void initialize(String metadataPrefix, RecDefModel recDefModel) {
        fetchMappingHints(metadataPrefix, recDefModel);
    }

    public void setSourceTree(SourceTreeNode sourceTree) {
        this.sourceTree = sourceTree;
        if (sourceTree != null) refresh();
    }

    public SortedSet<MappingFunction> getFunctions() {
        return mappingHints != null ? mappingHints.getFunctions() : null;
    }

    public void refresh() {
        final List<NodeMapping> mappingHintList = new ArrayList<NodeMapping>();
        Set<Path> sourcePaths = new HashSet<Path>();
        sourceTree.getPaths(sourcePaths);
        if (sipModel.getMappingModel().hasRecMapping()) {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            List<NodeMapping> nodeMappingList = recMapping.getRecDefTree().getNodeMappings();
            Set<NodeMapping> existingMappings = new HashSet<NodeMapping>(nodeMappingList);
            if (mappingHints != null) {
                for (NodeMapping mapping : mappingHints.getNodeMappings()) {
                    if (sourcePaths.contains(mapping.inputPath) && !existingMappings.contains(mapping)) {
                        mappingHintList.add(mapping);
                    }
                }
            }
            for (Path sourcePath : sourcePaths) {
                RecDefNode node = recMapping.getRecDefTree().getFirstRecDefNode(sourcePath.peek());
                if (node != null) {
                    NodeMapping mapping = new NodeMapping().setInputPath(sourcePath).setOutputPath(node.getPath());
                    mapping.recDefNode = node;
                    if (!existingMappings.contains(mapping)) mappingHintList.add(mapping);
                }
            }
        }
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                nodeMappingListModel.setList(mappingHintList);
            }
        });
    }

    private void fetchMappingHints(String metadataPrefix, RecDefModel recDefModel) {
        mappingHints = null;
        String resourceName = "/templates/" + Storage.FileType.MAPPING.getName(metadataPrefix);
        URL resource = getClass().getResource(resourceName);
        if (resource == null) return;
        try {
            mappingHints = RecMapping.read(resource.openStream(), recDefModel);
        }
        catch (Exception e) {
            sipModel.getFeedback().alert("Unable to read mapping hints file: " + resourceName, e);
        }
    }

    private class MappingEar implements MappingModel.ChangeListener {

        @Override
        public void lockChanged(MappingModel mappingModel, boolean locked) {
        }

        @Override
        public void functionChanged(MappingModel mappingModel, MappingFunction function) {
        }

        @Override
        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (sourceTree != null) refresh();
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (sourceTree != null) refresh();
        }

        @Override
        public void populationChanged(MappingModel mappingModel, RecDefNode node) {
        }
    }
}
