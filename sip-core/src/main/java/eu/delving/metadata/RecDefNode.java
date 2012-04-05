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
    private RecDef.Opt optRoot, optKey, optValue;
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private SortedMap<Path, NodeMapping> nodeMappings = new TreeMap<Path, NodeMapping>();
    private Listener listener;

    public interface Listener {
        void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping);

        void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping);

        void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping);
    }

    public static RecDefNode create(Listener listener, RecDef recDef) {
        return new RecDefNode(listener, null, recDef.root, null, null, null, null); // only root element
    }

    private RecDefNode(Listener listener, RecDefNode parent, RecDef.Elem elem, RecDef.Attr attr, RecDef.Opt optRoot, RecDef.Opt optKey, RecDef.Opt optValue) {
        this.listener = listener;
        this.parent = parent;
        this.elem = elem;
        this.attr = attr;
        this.optRoot = optRoot;
        if (optKey != null && optKey.parent.key.equals(getTag()))
            this.optKey = optKey;
        if (optValue != null && optValue.parent.value.equals(getTag()))
            this.optValue = optValue;
        if (elem != null) {
            for (RecDef.Attr sub : elem.attrList) {
                children.add(new RecDefNode(listener, this, null, sub, null, optRoot, optRoot));
            }
            for (RecDef.Elem sub : elem.elemList) {
                if (sub.optList == null) {
                    children.add(new RecDefNode(listener, this, sub, null, null, optRoot, optRoot));
                }
                else
                    for (RecDef.Opt subOpt : sub.optList.opts) { // a child for each option
                        children.add(new RecDefNode(listener, this, sub, null, subOpt, null, null));
                    }
            }
            if (elem.nodeMapping != null) {
                nodeMappings.put(elem.nodeMapping.inputPath, elem.nodeMapping);
                elem.nodeMapping.recDefNode = this;
                elem.nodeMapping.outputPath = getPath();
            }
        }
    }

    public List<String> getOptions() {
        return elem != null ? elem.options : attr.options;
    }

    public boolean hasSearchField() {
        return elem != null && elem.searchField != null;
    }

    public String getSearchField() {
        return elem.searchField.name;
    }

    public String getFieldType() {
        if (elem != null && elem.fieldType != null) return elem.fieldType;
        return "text";
    }

    public SummaryField getSummaryField() {
        if (elem != null && elem.summaryField != null) return elem.summaryField;
        return null;
    }

    public boolean isAttr() {
        return attr != null;
    }

    public boolean isLeafElem() {
        return elem != null && elem.elemList.isEmpty();
    }

    public boolean isSystemField() {
        return isAttr() ? attr.systemField : elem.systemField;
    }

    public boolean isUnmappable() {
        return !isAttr() && elem.unmappable;
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
        Tag tag = isAttr() ? attr.tag : elem.tag;
        return optRoot == null ? tag : tag.withOpt(optRoot.key);
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

    public RecDef.OptList getOptList() {
        return isAttr() ? null : elem.optList;
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

    public boolean hasConstant() {
        return optKey != null || optValue != null;
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
            throw new RuntimeException("Node mapping already exists for " + nodeMapping.inputPath);
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

    public void notifyNodeMappingChange(NodeMapping nodeMapping) {
        listener.nodeMappingChanged(this, nodeMapping);
    }

    public void toElementCode(Out out, Stack<Tag> groovyParams, EditPath editPath) {
        if (isAttr() || !hasNodeMappings()) return;
        if (editPath != null && !path.equals(editPath.getPath()) && !path.isAncestorOf(editPath.getPath())) return;
        if (nodeMappings.isEmpty()) {
            childrenToCode(out, groovyParams, editPath);
        }
        else for (NodeMapping nodeMapping : nodeMappings.values()) {
            childrenInLoop(nodeMapping, nodeMapping.getLocalPath(), groovyParams, out, editPath);
        }
    }

    private void childrenInLoop(NodeMapping nodeMapping, Path path, Stack<Tag> groovyParams, Out out, EditPath editPath) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            childrenToCode(out, groovyParams, editPath);
        }
        else if (nodeMapping.hasTuple() && path.size() == 2) {
            out.line_("%s * { %s -> // R1", nodeMapping.getTupleExpression(), nodeMapping.getTupleName());
            startBuilderCall(out, editPath);
            nodeMapping.toElementCode(out, editPath);
            out._line("}");
            out._line("}");
        }
        else { // path should never be empty
            Tag outer = path.getTag(0);
            Tag inner = path.getTag(1);
            Operator operator = (path.size() == 2) ? nodeMapping.getOperator() : Operator.ALL;
            boolean needLoop = !groovyParams.contains(inner);
            if (needLoop) {
                out.line_("%s%s %s { %s -> // R2", outer.toGroovyParam(), inner.toGroovyRef(), operator.getChar(), inner.toGroovyParam());
                groovyParams.push(inner);
            }
            childrenInLoop(nodeMapping, path.chop(-1), groovyParams, out, editPath);
            if (needLoop) {
                groovyParams.pop();
                out._line("}");
            }
        }
    }

    private void childrenToCode(Out out, Stack<Tag> groovyParams, EditPath editPath) {
        if (hasChildren()) {
            startBuilderCall(out, editPath);
            for (RecDefNode sub : children) {
                if (sub.isAttr()) continue;
                if (sub.optKey != null) {
                    out.line("%s '%s'", sub.getTag().toBuilderCall(), sub.optKey.key);
                }
                else if (sub.optValue != null) {
                    out.line("%s '%s'", sub.getTag().toBuilderCall(), sub.optValue.content);
                }
                else {
                    sub.toElementCode(out, groovyParams, editPath);
                }
            }
            out._line("}");
        }
        else if (nodeMappings.isEmpty()) {
            startBuilderCall(out, editPath);
            out.line("''");
            out._line("}");
        }
        else {
            for (NodeMapping nodeMapping : nodeMappings.values()) {
                if (nodeMapping.tuplePaths == null) {
                    startBuilderCall(out, editPath);
                    nodeMapping.toElementCode(out, editPath);
                    out._line("}");
                }
                else {
                    out.line_("%s %s { %s -> // R3", nodeMapping.getTupleExpression(), nodeMapping.getOperator().getChar(), nodeMapping.getTupleName());
                    startBuilderCall(out, editPath);
                    nodeMapping.toElementCode(out, editPath);
                    out._line("}");
                    out._line("}");
                }
            }
        }
    }

    private boolean hasChildren() {
        return !elem.elemList.isEmpty();
    }

    private void startBuilderCall(Out out, EditPath editPath) {
        if (hasActiveAttributes()) {
            out.line_("%s (", getTag().toBuilderCall());
            boolean comma = false;
            for (RecDefNode sub : children) {
                if (!sub.isAttr()) continue;
                if (sub.optKey != null) {
                    if (comma) out.line(",");
                    out.line("%s : '%s'", sub.getTag().toBuilderCall(), sub.optKey.key);
                    comma = true;
                }
                else if (sub.optValue != null) {
                    if (comma) out.line(",");
                    out.line("%s : '%s'", sub.getTag().toBuilderCall(), sub.optValue.content);
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.nodeMappings.values()) {
                        if (sub.isAttr()) {
                            if (comma) out.line(",");
                            nodeMapping.toAttributeCode(out, editPath);
                            comma = true;
                        }
                    }
                }
            }
            out._line(") {").in();
        }
        else {
            out.line_("%s {", getTag().toBuilderCall());
        }
    }

    private boolean hasActiveAttributes() {
        for (RecDefNode sub : children) if (sub.isAttr() && (sub.hasNodeMappings() || sub.hasConstant())) return true;
        return false;
    }

    public String toString() {
        String name = isAttr() ? attr.tag.toString() : elem.tag.toString();
        if (optRoot != null) name += String.format("[%s]", optRoot.content);
        if (optKey != null || optValue != null) name += "{Constant}";
        return name;
    }

}
