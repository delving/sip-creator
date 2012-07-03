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

import java.util.*;

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

    public RecDefNode getFirstRecDefNode(Tag tag) {
        return root.getFirstNode(tag);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        root.collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    public void toCode(CodeOut codeOut, Set<MappingFunction> mappingFunctions, Map<String,String> facts, EditPath editPath) {
        codeOut.line("// SIP-Creator Generated Mapping Code");
        codeOut.line("// ----------------------------------");
        codeOut.line("// Discarding:");
        codeOut.line("import eu.delving.groovy.DiscardRecordException");
        codeOut.line("def discard = { reason -> throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("// Facts:");
        for (Map.Entry<String,String> entry : facts.entrySet()) {
            codeOut.line(String.format("String %s = '''%s'''", entry.getKey(), entry.getValue()));
        }
        codeOut.line("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'");
        codeOut.line("// Functions from Mapping:");
        Set<String> names = new TreeSet<String>();
        for (MappingFunction function : mappingFunctions) {
            function.toCode(codeOut);
            names.add(function.name);
        }
        if (recDef.functions != null) {
            codeOut.line("// Functions from Record Definition:");
            for (MappingFunction function : recDef.functions) {
                if (names.contains(function.name)) continue;
                function.toCode(codeOut);
                names.add(function.name);
            }
        }
        codeOut.line("// Dictionaries:");
        for (NodeMapping nodeMapping : getNodeMappings()) StringUtil.toDictionaryCode(nodeMapping,codeOut);
        codeOut.line("// DSL Category wraps Builder call:");
        codeOut.line("boolean _absent_");
        codeOut.line("org.w3c.dom.Node outputNode");
        codeOut.line_("use (MappingCategory) {");
        codeOut.line_("input * { _input ->");
        codeOut.line("_uniqueIdentifier = _input._id[0].toString()");
        codeOut.line("outputNode = output.");
        if (root.hasDescendentNodeMappings()) {
            root.toElementCode(codeOut, new Stack<String>(), editPath);
        }
        else {
            codeOut.line("'no' { 'mapping' }");
        }
        codeOut._line("}");
        codeOut.line("outputNode");
        codeOut._line("}");
        codeOut.line("// ----------------------------------");
    }

    public List<RecDef.Namespace> getNamespaces() {
        return recDef.namespaces;
    }

    @Override
    public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
        if (listener != null) listener.nodeMappingChanged(recDefNode, nodeMapping, change);
    }

    @Override
    public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingAdded(recDefNode, nodeMapping);
    }

    @Override
    public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
        if (listener != null) listener.nodeMappingRemoved(recDefNode, nodeMapping);
    }

    private void resolve() {
        if (recDef.fieldMarkers == null) throw new IllegalStateException("Record definition must have field markers");
        EnumSet<SystemField> missing = EnumSet.allOf(SystemField.class);
        for (RecDef.FieldMarker marker : recDef.fieldMarkers) {
            if (marker.name != null && marker.type == null) missing.remove(SystemField.valueOf(marker.name));
            if (marker.path == null) continue;
            marker.resolve(this);
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Record definition missing system fields: "+missing);
    }
}
