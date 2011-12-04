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
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private NodeMapping nodeMapping;
    private Listener listener;

    public interface Listener {
        void nodeMappingSet(RecDefNode recDefNode);
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

    public RecDef.Elem getElem() {
        return elem;
    }

    public RecDef.Attr getAttr() {
        return attr;
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

    public RecDef.Doc getDoc() {
        return isAttr() ? attr.doc : elem.doc;
    }

    public List<RecDef.Opt> getOptions() {
        return isAttr() ? null : elem.options;
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

    public RecDefNode getNode(Path path, Path soughtPath) {
        path = path.extend(getTag());
        if (path.equals(soughtPath)) return this;
        for (RecDefNode sub : children) {
            RecDefNode found = sub.getNode(path, soughtPath);
            if (found != null) return found;
        }
        return null;
    }

    public boolean hasNodeMappings(Path path) {
        path = path.extend(getTag());
        boolean gotThem = nodeMapping != null;
        if (gotThem) nodeMapping.setOutputPath(path);
        for (RecDefNode sub : children) if (sub.hasNodeMappings(path)) gotThem = true;
        return gotThem;
    }

    public void collectNodeMappings(List<NodeMapping> nodeMappings) {
        if (nodeMapping != null) {
            nodeMappings.add(nodeMapping);
        }
        for (RecDefNode sub : children) {
            sub.collectNodeMappings(nodeMappings);
        }
    }

    public void collectNodesWithMappings(List<RecDefNode> nodesWithMappings) {
        if (nodeMapping != null) {
            nodesWithMappings.add(this);
        }
        for (RecDefNode sub : children) {
            sub.collectNodesWithMappings(nodesWithMappings);
        }
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        this.nodeMapping = nodeMapping;
        listener.nodeMappingSet(this);
    }

    public String getDictionaryName() {
        return "Dictionary"; // todo: based on path somehow
    }

    public void toCode(Path path, RecDefTree.Out out, Path selectedPath, String editedCode) {
        if (!hasNodeMappings(path)) return;
        path = path.extend(getTag());
        if (path.equals(selectedPath)) {
            if (nodeMapping == null) throw new IllegalStateException("Node mapping expected at " + selectedPath);
            out.before();
            nodeMapping.toCode(out, editedCode);
            out.after();
        }
        else if (selectedPath == null || path.isAncestorOf(selectedPath)) {
            if (isAttr()) {

            }
            else {
                out.before();
                String attributes = getAttributes();
                if (attributes.isEmpty()) {
                    out.line(String.format("%s {", getTag()));
                }
                else {
                    out.line(String.format("%s(%s) {", getTag(), attributes));
                }
                for (RecDefNode sub : children) if (!sub.isAttr()) sub.toCode(path, out, selectedPath, editedCode);
                out.line("}");
                out.after();
            }
        }
    }

    private String getAttributes() {
        StringBuilder attrs = new StringBuilder();
        for (RecDefNode sub : children)
            if (sub.isAttr() && sub.getNodeMapping() != null) {
                if (attrs.length() > 0) attrs.append(", ");
                NodeMapping mapping = sub.getNodeMapping();
                String value = mapping.getGroovyCode(0);
                attrs.append(sub.getTag().getLocalName()).append(":\"").append("something").append('"'); // todo: use value
            }
        return attrs.toString();
    }

    public String toString() {
        return isAttr() ? attr.tag.toString().substring(1) : elem.tag.toString();
    }

}
