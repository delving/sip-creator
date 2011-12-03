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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        return root.getNode(new Path(), path);
    }

    public List<NodeMapping> getNodeMappings() {
        List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();
        if (root.hasNodeMappings(new Path())) root.collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    public List<RecDefNode> getNodesWithMappings() {
        List<RecDefNode> nodesWithMappings = new ArrayList<RecDefNode>();
        if (root.hasNodeMappings(new Path())) root.collectNodesWithMappings(nodesWithMappings);
        return nodesWithMappings;
    }

    public String toCode(Map<String, String> facts, Path selectedPath, String editedCode) {
        Out out = new Out();
        out.line("// SIP-Creator Generated Mapping Code");
        out.line("// Facts:");
        for (Map.Entry<String, String> factEntry : facts.entrySet()) {
            out.line(String.format("def %s = '''%s'''",
                    factEntry.getKey(),
                    Sanitizer.sanitizeGroovy(factEntry.getValue())
            ));
        }
        out.line("// Dictionaries:");
        for (RecDefNode node : getNodesWithMappings()) generateDictionary(node, out);
        out.line("use (MappingCategory) { output.");
        out.before();
        root.toCode(new Path(), out, selectedPath, editedCode);
        out.after();
        out.line("}");
        return out.toString();
    }

    private void generateDictionary(RecDefNode node, Out out) {
        if (node.getNodeMapping().dictionary == null) return;
        String name = node.getDictionaryName();
        out.line(String.format("def %s_Dictionary = [", name));
        out.before();
        Iterator<Map.Entry<String, String>> walk = node.getNodeMapping().dictionary.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<String, String> entry = walk.next();
            out.line(String.format("'''%s''':'''%s'''%s",
                    Sanitizer.sanitizeGroovy(entry.getKey()),
                    Sanitizer.sanitizeGroovy(entry.getValue()),
                    walk.hasNext() ? "," : ""
            ));
        }
        out.after();
        out.line("]");
        out.line("// lookup closure:");
        out.line("def $s_lookup = { value =>");
        out.before();
        out.line("if (value) {");
        out.before();
        out.line("def v = %s_Dictionary[value.sanitize()];", name);
        out.line("if (v) {");
        out.before();
        out.line("if (v.endsWith(':')) {");
        out.before();
        out.line("\"${v} ${value}\"");
        out.after();
        out.line("}");
        out.line("else { v }");
        out.line("}");
        out.line("else { '' }");
        out.after();
        out.line("}");
        out.line("else { '' }");
        out.after();
        out.line("}");
        out.after();
        out.line("}");
    }

    public List<RecDef.Namespace> getNamespaces() {
        return recDef.namespaces;
    }

    @Override
    public void nodeMappingSet(RecDefNode recDefNode) {
        if (listener != null) listener.nodeMappingSet(recDefNode);
    }

    public class Out {
        private int indentLevel;
        private StringBuilder stringBuilder = new StringBuilder();

        public void line(String line) {
            for (int walk = 0; walk < indentLevel; walk++) {
                stringBuilder.append('\t');
            }
            stringBuilder.append(line).append('\n');
        }

        public void line(String pattern, Object... params) {
            line(String.format(pattern, params));
        }

        public void before() {
            indentLevel--;
        }

        public void after() {
            indentLevel++;
        }

        @Override
        public String toString() {
            return stringBuilder.toString();
        }
    }

}
