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

import java.util.ArrayList;
import java.util.List;

/**
 * This class takes a RecDef instance and wraps itself around, ensuring
 * event propagation by brokering the Listener that is passed to every
 * contained RecDefNode.
 *
 * The RecDefNode composite elements of the tree are held at "root" and
 * they contain NodeMappings from the RecMapping, so this plust the fact
 * that the RecDef's data is always available makes this the class
 * responsible for writing the code of the Groovy builder which will
 * ultimately do the transformation.
 *
 * Code generation is done in two ways, with or without a particular
 * path selected, so that the user interface can give people only a tiny
 * part of the whole mapping to deal with at one time.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefTree implements RecDefNode.Listener {
    private RecDef recDef;
    private RecDefNode root;
    private RecDefNode.Listener listener;

    public static RecDefTree create(RecDef recDef) {
        return new RecDefTree(recDef);
    }

    private RecDefTree(RecDef recDef) {
        this.recDef = recDef;
        this.root = RecDefNode.create(this, recDef);
    }

    public void setListener(RecDefNode.Listener listener) {
        this.listener = listener;
    }

    public RecDef getRecDef() {
        return recDef;
    }

    public RecDefNode getRoot() {
        return root;
    }

    public RecDefNode getRecDefNode(Path path) {
        return root.getNode(new Path(), path);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        root.collect(new Path(), nodeMappings);
        return nodeMappings;
    }

    public String toCode(Path selectedPath, String editedCode) {
        StringBuilder out = new StringBuilder();
        // todo: write the code!
        return out.toString();
    }

    public List<RecDef.Namespace> getNamespaces() {
        return recDef.namespaces;
    }

    @Override
    public void nodeMappingSet(RecDefNode recDefNode) {
        if (listener != null) listener.nodeMappingSet(recDefNode);
    }
}
