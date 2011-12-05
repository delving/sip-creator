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

import eu.delving.groovy.GroovyVariable;

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
    private Path path;
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

    public boolean isLeaf() {
        return isAttr() || elem.elemList.isEmpty();
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

    public RecDefNode getNode(Path soughtPath) {
        if (getPath().equals(soughtPath)) return this;
        for (RecDefNode sub : children) {
            RecDefNode found = sub.getNode(soughtPath);
            if (found != null) return found;
        }
        return null;
    }

    public boolean hasNodeMappings() {
        if (nodeMapping != null) return true;
        for (RecDefNode sub : children) if (sub.hasNodeMappings()) return true;
        return false;
    }

    public void collectNodeMappings(List<NodeMapping> nodeMappings) {
        if (nodeMapping != null) nodeMappings.add(nodeMapping);
        for (RecDefNode sub : children) sub.collectNodeMappings(nodeMappings);
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public NodeMapping setNodeMapping(NodeMapping nodeMapping) {
        if ((this.nodeMapping = nodeMapping) != null) this.nodeMapping.attachTo(this);
        listener.nodeMappingSet(this);
        return nodeMapping;
    }

    public void toCode(RecDefTree.Out out, Path selectedPath, String editedCode) {
        if (!hasNodeMappings()) return;
        if (selectedPath != null) throw new RuntimeException("unimplemented");
        if (nodeMapping != null) {
            if (isLeaf()) {
                beforeLeaf(out);
                nodeMapping.toCode(out, editedCode);
                afterBrace(out);
            }
            else {
                beforeIteration(out);
                beforeChildren(out);
                for (RecDefNode sub : children) if (!sub.isAttr()) sub.toCode(out, selectedPath, editedCode);
                afterBrace(out);
                afterBrace(out);
            }
        }
        else {
            beforeChildren(out);
            for (RecDefNode sub : children) if (!sub.isAttr()) sub.toCode(out, selectedPath, editedCode);
            afterBrace(out);
        }
    }

    private void beforeChildren(RecDefTree.Out out) {
        String attributes = getAttributes();
        if (attributes.isEmpty()) {
            out.line(String.format("%s {", getTag()));
        }
        else {
            out.line(String.format("%s(%s) {", getTag(), attributes));
        }
        out.before();
    }

    private void beforeLeaf(RecDefTree.Out out) {
        out.line("%s { ", getTag());
        out.before();
    }

    private void beforeIteration(RecDefTree.Out out) {
        out.line("%s * { %s ->", nodeMapping.getVariableName(), GroovyVariable.paramName(nodeMapping.inputPath));
        out.before();
    }

    private void afterBrace(RecDefTree.Out out) {
        out.after();
        out.line("}");
    }

    private String getAttributes() {
        StringBuilder attrs = new StringBuilder();
        for (RecDefNode sub : children) {
            if (sub.isAttr() && sub.getNodeMapping() != null) {
                if (attrs.length() > 0) attrs.append(", ");
                NodeMapping subNodeMapping = sub.getNodeMapping();
                String value = subNodeMapping.getVariableName();
                attrs.append(sub.getTag().getLocalName()).append(":{ ").append(value).append(" }");
            }
        }
        return attrs.toString();
    }

    public String toString() {
        return isAttr() ? attr.tag.toString().substring(1) : elem.tag.toString();
    }

}
