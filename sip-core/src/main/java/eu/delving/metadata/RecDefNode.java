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

import java.util.*;

/**
 * The RecDefNode is the node element of a RecDefTree which stores the whole
 * hierarchy of the record definition as an in-memory data structure that
 * can be decorated with NodeMapping instances.
 * <p/>
 * A node here can represent either an Elem or an Attr and it knows about
 * its parent and children.
 * <p/>
 * Whenever a NodeMapping is placed here or removed, a callback to a listener
 * is invoked, and each node in the tree will have the same listener: the
 * tree itself, which delegates to a single settable listener.  This way
 * all changes propagate out and the user interface code can deal
 * with node references.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefNode {
    private RecDefNode parent;
    private Path path;
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private Map<Path, NodeMapping> nodeMappings = new TreeMap<Path, NodeMapping>();
    private Listener listener;

    public interface Listener {
        void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping);

        void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping);
    }

    public static RecDefNode create(Listener listener, RecDef recDef) {
        return new RecDefNode(listener, null, recDef.root);
    }

    private RecDefNode(Listener listener, RecDefNode parent, RecDef.Elem elem) {
        this.listener = listener;
        this.parent = parent;
        this.elem = elem;
        for (RecDef.Attr sub : elem.attrList) children.add(new RecDefNode(listener, this, sub));
        for (RecDef.Elem sub : elem.elemList) children.add(new RecDefNode(listener, this, sub));
    }

    private RecDefNode(Listener listener, RecDefNode parent, RecDef.Attr attr) {
        this.listener = listener;
        this.parent = parent;
        this.attr = attr;
    }

    public boolean isAttr() {
        return attr != null;
    }

    public boolean isLeafElem() {
        return elem != null && elem.elemList.isEmpty();
    }

    public boolean isSingular() {
        return !isAttr() && elem.singular;
    }

    public RecDefNode getParent() {
        return parent;
    }

    public List<RecDefNode> getChildren() {
        return children;
    }

    public Tag getTag() {
        return isAttr() ? attr.tag : elem.tag;
    }

    public Path getPath() {
        if (path == null) {
            path = (parent == null) ? Path.empty().extend(getTag()) : parent.getPath().extend(getTag());
        }
        return path;
    }

    public RecDef.Doc getDoc() {
        return isAttr() ? attr.doc : elem.doc;
    }

    public boolean hasOptions() {
        return getOptions() != null;
    }

    public List<RecDef.Opt> getOptions() {
        return isAttr() ? attr.options : elem.options;
    }

    public boolean allowOption(String value) {
        List<RecDef.Opt> options = getOptions();
        if (options != null) {
            for (RecDef.Opt option : options) {
                String member = option.content;
                if (member.endsWith(":")) {
                    int colon = value.indexOf(':');
                    if (colon > 0) {
                        if (member.equals(value.substring(0, colon + 1))) {
                            return true;
                        }
                    }
                    else {
                        if (member.equals(value) || member.substring(0, member.length() - 1).equals(value)) {
                            return true;
                        }
                    }
                }
                else if (member.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public RecDefNode getNode(Path soughtPath) {
        if (getPath().equals(soughtPath)) return this;
        for (RecDefNode sub : children) {
            RecDefNode found = sub.getNode(soughtPath);
            if (found != null) return found;
        }
        return null;
    }

    public boolean hasNodeMappings() {
        if (!nodeMappings.isEmpty()) return true;
        for (RecDefNode sub : children) if (sub.hasNodeMappings()) return true;
        return false;
    }

    public void collectNodeMappings(List<NodeMapping> nodeMappings) {
        nodeMappings.addAll(this.nodeMappings.values());
        for (RecDefNode sub : children) sub.collectNodeMappings(nodeMappings);
    }

    public Map<Path, NodeMapping> getNodeMappings() {
        return nodeMappings;
    }

    public NodeMapping addNodeMapping(NodeMapping nodeMapping) {
        nodeMapping.attachTo(this);
        if (!nodeMappings.containsKey(nodeMapping.inputPath)) {
            nodeMappings.put(nodeMapping.inputPath, nodeMapping);
            listener.nodeMappingAdded(this, nodeMapping);
        }
        else {
            throw new RuntimeException();
        }
        return nodeMapping;
    }

    public NodeMapping removeNodeMapping(Path path) {
        NodeMapping nodeMapping = nodeMappings.get(path);
        if (nodeMapping != null) {
            nodeMappings.remove(path);
            listener.nodeMappingRemoved(this, nodeMapping);
            return nodeMapping;
        }
        else {
            return null;
        }
    }

    public void toCode(Out out, Path selectedPath, String editedCode) {
        if (!hasNodeMappings()) return;
        if (selectedPath != null && !path.equals(selectedPath) && !path.isAncestorOf(selectedPath)) return;
        if (isAttr()) {
            if (nodeMappings.size() != 1) throw new RuntimeException("Not sure yet, might use +");
            NodeMapping nodeMapping = nodeMappings.values().iterator().next();
            nodeMapping.toLeafCode(out, editedCode);
        }
        else if (nodeMappings.isEmpty()) {
            childrenToCode(out, selectedPath, editedCode);
        }
        else {
            if (nodeMappings.size() != 1) throw new RuntimeException("Not sure yet, might use +");
            NodeMapping nodeMapping = nodeMappings.values().iterator().next();
            toLoop(nodeMapping.getLocalPath(), out, selectedPath, editedCode);
        }
    }

    private void toLoop(Path path, Out out, Path selectedPath, String editedCode) {
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            childrenToCode(out, selectedPath, editedCode);
        }
        else {
            Tag outer = path.getTag(0);
            Tag inner = path.getTag(1);
            out.line_("%s%s * { %s ->", outer.toGroovyParam(), inner.toGroovyRef(), inner.toGroovyParam());
            toLoop(path.chop(-1), out, selectedPath, editedCode);
            out._line("}");
        }
    }

    private void childrenToCode(Out out, Path selectedPath, String editedCode) {
        boolean activeAttributes = false;
        for (RecDefNode sub : children) if (sub.isAttr() && sub.hasNodeMappings()) activeAttributes = true;
        if (activeAttributes) {
            out.line_("%s (", getTag().toBuilderCall());
            for (RecDefNode sub : children) {
                if (sub.isAttr() && sub.hasNodeMappings()) {
                    for (NodeMapping nodeMapping : sub.nodeMappings.values()) nodeMapping.toLeafCode(out, editedCode);
                }
            }
            out._line(") {").in();
        }
        else {
            out.line_("%s {", getTag().toBuilderCall());
        }
        for (RecDefNode sub : children) if (!sub.isAttr()) sub.toCode(out, selectedPath, editedCode);
        if (elem.elemList.isEmpty() && !nodeMappings.isEmpty()) {
            if (nodeMappings.size() != 1) throw new RuntimeException("Not sure yet, might use +");
            NodeMapping nodeMapping = nodeMappings.values().iterator().next();
            nodeMapping.toLeafCode(out, editedCode);
        }
        out._line("}");
    }

    public String toString() {
        String name = isAttr() ? attr.tag.toString().substring(1) : elem.tag.toString();
        if (nodeMappings.isEmpty()) {
            return name;
        }
        else {
            StringBuilder out = new StringBuilder(name);
            out.append(" <- ");
            Iterator<Path> walk = nodeMappings.keySet().iterator();
            while (walk.hasNext()) {
                out.append(walk.next().getTail());
                if (walk.hasNext()) out.append(", ");
            }
            return out.toString();
        }
    }

}
