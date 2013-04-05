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

package eu.delving.metadata;

import java.util.ArrayList;
import java.util.List;

import static eu.delving.metadata.RecDef.REQUIRED_FIELDS;

/**
 * This class takes a RecDef instance and wraps itself around, ensuring
 * event propagation by brokering the Listener that is passed to every
 * contained RecDefNode.
 * <p/>
 * The RecDefNode composite elements of the tree are held at "root" and
 * they contain NodeMappings from the RecMapping, so this plust the fact
 * that the RecDef's data is always available makes this the class
 * responsible for writing the code of the Groovy builder which will
 * ultimately do the transformation.
 * <p/>
 * Code generation is done in two ways, with or without a particular
 * path selected, so that the user interface can give people only a tiny
 * part of the whole mapping to deal with at one time.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefTree implements RecDefNodeListener {
    private RecDef recDef;
    private RecDefNode root;
    private RecDefNodeListener listener;

    public static RecDefTree create(RecDef recDef) {
        RecDefTree tree = new RecDefTree(recDef);
        tree.resolve();
        return tree;
    }

    private RecDefTree(RecDef recDef) {
        this.recDef = recDef;
        this.root = RecDefNode.create(this, recDef);
    }

    public void setListener(RecDefNodeListener listener) {
        this.listener = listener;
    }

    public RecDef getRecDef() {
        return recDef;
    }

    public RecDefNode getRoot() {
        return root;
    }

    public RecDefNode getRecDefNode(Path path) {
        return root.getNode(path);
    }

    public String getPathsList() {
        StringBuilder out = new StringBuilder();
        for (Path path : collectPaths()) {
            out.append(path.toString()).append('\n');
        }
        return out.toString();
    }

    public List<Path> collectPaths() {
        List<Path> paths = new ArrayList<Path>();
        collectPaths(root, Path.create(), paths);
        return paths;
    }

    public RecDefNode getFirstRecDefNode(Tag tag) {
        return root.getFirstNode(tag);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        root.collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    public List<DynOpt> getDynOpts() {
        List<DynOpt> dynOpts = new ArrayList<DynOpt>();
        root.collectDynOpts(dynOpts);
        return dynOpts;
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
        if (listener != null) listener.nodeMappingChanged(recDefNode, nodeMapping, change);
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingAdded(recDefNode, nodeMapping);
        root.checkPopulated();
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingRemoved(recDefNode, nodeMapping);
        root.checkPopulated();
    }

    @Override
    public void populationChanged(RecDefNode recDefNode) {
        if (listener != null) listener.populationChanged(recDefNode);
    }

    private void collectPaths(RecDefNode node, Path path, List<Path> paths) {
        path = path.child(node.getTag());
        paths.add(path);
        for (RecDefNode child : node.getChildren()) collectPaths(child, path, paths);
    }

    private void resolve() {
        if (recDef.fieldMarkers == null) throw new IllegalStateException("Record definition must have field markers");
        boolean[] present = new boolean[REQUIRED_FIELDS.length];
        for (RecDef.FieldMarker marker : recDef.fieldMarkers) {
            if (marker.path == null) continue;
            marker.resolve(this);
            if (marker.name != null && marker.type == null) {
                for (int index = 0; index < REQUIRED_FIELDS.length; index++) {
                    if (marker.name.equals(REQUIRED_FIELDS[index])) present[index] = true;
                }
            }
        }
        for (int index = 0; index < present.length; index++) {
            if (!present[index]) System.out.println(String.format(
                    "%s field markers missing required field %s",
                    recDef.getSchemaVersion(), REQUIRED_FIELDS[index]
            ));
        }
        root.checkPopulated();
    }
}
