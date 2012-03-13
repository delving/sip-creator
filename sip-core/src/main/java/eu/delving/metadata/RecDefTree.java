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
import java.util.Set;

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
        return root.getNode(path);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        root.collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    public String toCode(Set<MappingFunction> mappingFunctions, EditPath editPath) {
        Out out = new Out();
        out.line("// SIP-Creator Generated Mapping Code");
        out.line("// ----------------------------------");
        out.line("// Functions:");
        for (MappingFunction function : mappingFunctions) function.toCode(out);
        out.line("// Dictionaries:");
        for (NodeMapping nodeMapping : getNodeMappings()) nodeMapping.generateDictionaryCode(out);
        out.line("// DSL Category wraps Builder call:");
        out.line("org.w3c.dom.Node outputNode");
        out.line_("use (MappingCategory) {");
        out.line_("input * { _input -> outputNode = output.");
        if (root.hasNodeMappings()) {
            root.toElementCode(out, editPath);
        }
        else {
            out.line("'no' { 'mapping' }");
        }
        out._line("}");
        out.line("outputNode");
        out._line("}");
        out.line("// ----------------------------------");
        return out.toString();
    }

    public List<RecDef.Namespace> getNamespaces() {
        return recDef.namespaces;
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingChanged(recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingAdded(recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingRemoved(recDefNode, nodeMapping);
    }

}
