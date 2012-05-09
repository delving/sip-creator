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

import static eu.delving.metadata.StringUtil.*;

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

public class RecDefNode implements Comparable<RecDefNode> {
    private RecDefNode parent;
    private Path path;
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private OptBox optBox;
    private String defaultPrefix;
    private RecDef.FieldMarker fieldMarker;
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private SortedMap<Path, NodeMapping> nodeMappings = new TreeMap<Path, NodeMapping>();
    private RecDefNodeListener listener;

    @Override
    public int compareTo(RecDefNode recDefNode) {
        return getTag().compareTo(recDefNode.getTag());
    }

    public static RecDefNode create(RecDefNodeListener listener, RecDef recDef) {
        return new RecDefNode(listener, null, recDef.root, null, recDef.prefix, null); // only root element
    }

    private RecDefNode(RecDefNodeListener listener, RecDefNode parent, RecDef.Elem elem, RecDef.Attr attr, String defaultPrefix, OptBox optBox) {
        this.listener = listener;
        this.parent = parent;
        this.elem = elem;
        this.attr = attr;
        this.defaultPrefix = defaultPrefix;
        OptBox childOptBox = null;
        if (optBox != null) {
            switch (optBox.role) {
                case ROOT:
                    this.optBox = optBox;
                    childOptBox = optBox.createChild();
                    break;
                case CHILD:
                    this.optBox = optBox.inRoleFor(getTag());
                    break;
            }
        }
        if (elem != null) {
            for (RecDef.Attr sub : elem.attrList) {
                children.add(new RecDefNode(listener, this, null, sub, defaultPrefix, childOptBox)); // child  optBox
            }
            for (RecDef.Elem sub : elem.elemList) {
                if (sub.optList != null) {
                    if (sub.optList.dictionary) {
                        children.add(new RecDefNode(listener, this, sub, null, defaultPrefix, null)); // ignore dictionaries for this
                    }
                    else {
                        for (OptList.Opt subOpt : sub.optList.opts) { // a child for each option (introducing the OptBox instances)
                            children.add(new RecDefNode(listener, this, sub, null, defaultPrefix, new OptBox(OptRole.ROOT, subOpt)));
                        }
                    }
                }
                else {
                    children.add(new RecDefNode(listener, this, sub, null, defaultPrefix, childOptBox)); // child OptBox
                }
            }
            if (elem.nodeMapping != null) {
                nodeMappings.put(elem.nodeMapping.inputPath, elem.nodeMapping);
                elem.nodeMapping.recDefNode = this;
                elem.nodeMapping.outputPath = getPath();
            }
        }
    }

    public boolean isHiddenOpt(OptList.Opt shown) {
        return isRootOpt() && optBox.opt != shown && optBox.opt.hidden;
    }

    public String getFieldType() {
        if (elem != null && elem.fieldType != null) return elem.fieldType;
        return "text";
    }

    public boolean isAttr() {
        return attr != null;
    }

    public boolean isLeafElem() {
        return elem != null && elem.elemList.isEmpty();
    }

    public void setFieldMarker(RecDef.FieldMarker fieldMarker) {
        this.fieldMarker = fieldMarker;
    }

    public RecDef.FieldMarker getFieldMarker() {
        return fieldMarker;
    }

    public boolean isHidden() {
        return isAttr() ? attr.hidden : elem.hidden;
    }

    public boolean isUnmappable() {
        return !isAttr() && elem.unmappable;
    }

//    public boolean isSingular() { todo: use it
//        return !isAttr() && elem.singular;
//    }

    public RecDefNode getParent() {
        return parent;
    }

    public List<RecDefNode> getChildren() {
        return children;
    }

    public Tag getTag() {
        Tag tag = isAttr() ? attr.tag : elem.tag;
        return isRootOpt() ? tag.withOpt(optBox.opt.key) : tag;
    }

    public Path getPath() {
        if (path == null) {
            path = (parent == null) ? Path.create().child(getTag()) : parent.getPath().child(getTag());
        }
        return path;
    }

    public RecDef.Doc getDoc() {
        return isAttr() ? attr.doc : elem.doc;
    }

    public OptList getOptList() {
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

    public boolean hasDescendentNodeMappings() {
        if (!nodeMappings.isEmpty()) return true;
        for (RecDefNode sub : children) if (sub.hasDescendentNodeMappings()) return true;
        return false;
    }

    public boolean hasConstant() {
        return optBox != null && optBox.isChild();
    }

    public boolean hasFunction() {
        return !isAttr() && elem.function != null;
    }

    public boolean hasOperator() {
        return !isAttr() && elem.operator != null;
    }

    public Operator getOperator() {
        return elem.operator;
    }

    public String getFunction() {
        return elem.function;
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

    public void notifyNodeMappingChange(NodeMapping nodeMapping, NodeMappingChange change) {
        listener.nodeMappingChanged(this, nodeMapping, change);
    }

    public void toElementCode(boolean virtual, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (isAttr() || !hasDescendentNodeMappings()) return;
        if (editPath != null && !path.isFamilyOf(editPath.getNodeMapping().outputPath)) return;
        if (nodeMappings.isEmpty()) {
            if (isRootOpt() && !virtual) {
                Set<Path> siblingPaths = getSiblingInputPathsOfChildren();
                if (siblingPaths != null) {
                    NodeMapping nodeMapping = new NodeMapping().setOutputPath(path).setInputPaths(siblingPaths);
                    nodeMapping.recDefNode = this;
                    nodeMapping.codeOut = codeOut.createChild();
                    toNodeMappingLoop(true, nodeMapping, nodeMapping.getLocalPath(), groovyParams, codeOut, editPath);
                    return;
                }
            }
            if (!isLeafElem()) {
                toBranchCode(false, codeOut, groovyParams, editPath);
            }
            else if (hasActiveAttributes()) {
                startBuilderCall("R0", codeOut, groovyParams, editPath);
                codeOut.line("// no node mappings");
                codeOut._line("}");
            }
        }
        else if (editPath != null) {
            NodeMapping nodeMapping = editPath.getNodeMapping();
            nodeMapping.codeOut = codeOut.createChild();
            toNodeMappingLoop(false, nodeMapping, nodeMapping.getLocalPath(), groovyParams, codeOut, editPath);
        }
        else {
            for (NodeMapping nodeMapping : nodeMappings.values()) {
                nodeMapping.codeOut = codeOut.createChild();
                toNodeMappingLoop(false, nodeMapping, nodeMapping.getLocalPath(), groovyParams, codeOut, editPath);
            }
        }
    }

    private void toNodeMappingLoop(boolean virtual, NodeMapping nodeMapping, Path path, Stack<String> groovyParams, CodeOut codeOut, EditPath editPath) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            if (isLeafElem()) {
                toLeafCode(nodeMapping, codeOut, groovyParams, editPath);
            }
            else {
                toBranchCode(virtual, codeOut, groovyParams, editPath);
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            if (groovyParams.contains(nodeMapping.getMapName())) {
                toMapNodeMapping(virtual, nodeMapping, codeOut, groovyParams, editPath);
            }
            else {
                if (virtual) {
                    codeOut.line_(
                            "if (%s) { // R1",
                            toMapExpression(nodeMapping)
                    );
                }
                else {
                    codeOut.line_(
                            "%s * { %s -> // R2",
                            toMapExpression(nodeMapping),
                            nodeMapping.getMapName()
                    );
                    groovyParams.push(nodeMapping.getMapName());
                }
                toMapNodeMapping(false /* no longer virtual */, nodeMapping, codeOut, groovyParams, editPath);
                if (!virtual) groovyParams.pop();
                codeOut._line("}");
            }
        }
        else { // path should never be empty
            Operator operator = (path.size() == 2) ? nodeMapping.getOperator() : Operator.ALL;
            String param = toLoopGroovyParam(path);
            if (groovyParams.contains(param)) {
                toNodeMappingLoop(false, nodeMapping, path.withRootRemoved(), groovyParams, codeOut, editPath);
            }
            else {
                if (virtual) {
                    codeOut.line_(
                            "if (%s) { // R6",
                            toLoopRef(path)
                    );
                }
                else {
                    codeOut.line_(
                            "%s %s { %s -> // R7",
                            toLoopRef(path), operator.getChar(), param
                    );
                    groovyParams.push(param);
                }
                toNodeMappingLoop(false /* no longer virtual */, nodeMapping, path.withRootRemoved(), groovyParams, codeOut, editPath);
                if (!virtual) groovyParams.pop();
                codeOut._line("}");
            }
        }
    }

    private void toMapNodeMapping(boolean virtual, NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (isLeafElem()) {
            startBuilderCall("R3", codeOut, groovyParams, editPath);
            nodeMapping.toLeafElementCode(groovyParams, editPath);
            codeOut._line("}");
        }
        else {
            startBuilderCall("R4", nodeMapping.codeOut, groovyParams, editPath);
            for (RecDefNode sub : children) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    nodeMapping.codeOut.line(
                            "%s '%s' // R5",
                            sub.getTag().toBuilderCall(), sub.optBox
                    );
                }
                else {
                    sub.toElementCode(virtual, nodeMapping.codeOut, groovyParams, editPath);
                }
            }
            nodeMapping.codeOut._line("}");
        }
    }

    private void toBranchCode(boolean virtual, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        startBuilderCall("R8", codeOut, groovyParams, editPath);
        for (RecDefNode sub : children) {
            if (sub.isAttr()) continue;
            if (sub.isChildOpt()) {
                codeOut.line(
                        "%s '%s' // R9",
                        sub.getTag().toBuilderCall(), sub.optBox
                );
            }
            else {
                sub.toElementCode(virtual, codeOut, groovyParams, editPath);
            }
        }
        codeOut._line("}");
    }

    private void toLeafCode(NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (nodeMapping.hasMap()) {
            codeOut.line_(
                    "%s %s { %s -> // R10",
                    toMapExpression(nodeMapping), nodeMapping.getOperator().getChar(), nodeMapping.getMapName()
            );
            startBuilderCall("R11", codeOut, groovyParams, editPath);
            nodeMapping.codeOut = codeOut.createChild();
            nodeMapping.toLeafElementCode(groovyParams, editPath);
            codeOut._line("}");
            codeOut._line("}");
        }
        else {
            startBuilderCall("R12", codeOut, groovyParams, editPath);
            nodeMapping.codeOut = codeOut.createChild();
            nodeMapping.toLeafElementCode(groovyParams, editPath);
            codeOut._line("}");
        }
    }

    public Set<Path> getSiblingInputPathsOfChildren() {
        List<NodeMapping> subMappings = new ArrayList<NodeMapping>();
        for (RecDefNode sub : children) sub.collectNodeMappings(subMappings);
        Set<Path> inputPaths = new TreeSet<Path>();
        Path parent = null;
        for (NodeMapping subMapping : subMappings) {
            if (parent == null) {
                parent = subMapping.inputPath.parent();
            }
            else {
                if (!subMapping.inputPath.parent().equals(parent)) return null; // different parents
            }
            inputPaths.addAll(subMapping.getInputPaths());
        }
        return inputPaths;
    }

    private boolean isRootOpt() {
        return elem != null && optBox != null && optBox.role == OptRole.ROOT;
    }

    private boolean isChildOpt() {
        return optBox != null && optBox.role != OptRole.ROOT;
    }

    private void startBuilderCall(String comment, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (hasActiveAttributes()) {
            Tag tag = getTag();
            codeOut.line_("%s ( // %s%s", tag.toBuilderCall(), comment, isRootOpt() ? "(opt)" : "");
            boolean comma = false;
            for (RecDefNode sub : children) {
                if (!sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    if (comma) codeOut.line(",");
                    codeOut.line("%s : '%s' // %sc", sub.getTag().toBuilderCall(), sub.optBox, comment);
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.nodeMappings.values()) {
                        nodeMapping.codeOut = codeOut.createChild();
                        if (comma) codeOut.line(",");
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        nodeMapping.toAttributeCode(groovyParams, editPath);
                        codeOut._line("}");
                        comma = true;
                    }
                }
            }
            codeOut._line(") {").in();
        }
        else {
            codeOut.line_("%s { // %s%s", getTag().toBuilderCall(), comment, isRootOpt() ? "(opt)" : "");
        }
    }

    private boolean hasActiveAttributes() {
        for (RecDefNode sub : children) {
            if (sub.isAttr() && (sub.hasDescendentNodeMappings() || sub.hasConstant())) return true;
        }
        return false;
    }

    public String toString() {
        String name = isAttr() ? attr.tag.toString(defaultPrefix) : elem.tag.toString(defaultPrefix);
        if (isRootOpt()) name += String.format("[%s]", optBox);
        if (isChildOpt()) name += "{Constant}";
        return name;
    }

    private enum OptRole {ROOT, CHILD, KEY, VALUE, SCHEMA, SCHEMA_URI}

    private static class OptBox {
        final OptRole role;
        final OptList.Opt opt;

        OptBox(OptRole role, OptList.Opt opt) {
            this.role = role;
            this.opt = opt;
        }

        OptBox inRoleFor(Tag tag) {
            if (role == OptRole.CHILD) {
                if (opt.parent.key != null && opt.parent.key.equals(tag)) return new OptBox(OptRole.KEY, opt);
                if (opt.parent.value != null && opt.parent.value.equals(tag)) return new OptBox(OptRole.VALUE, opt);
                if (opt.parent.schema != null && opt.parent.schema.equals(tag)) return new OptBox(OptRole.SCHEMA, opt);
                if (opt.parent.schemaUri != null && opt.parent.schemaUri.equals(tag))
                    return new OptBox(OptRole.SCHEMA_URI, opt);
            }
            return null;
        }

        OptBox createChild() {
            if (role != OptRole.ROOT) throw new RuntimeException();
            return new OptBox(OptRole.CHILD, opt);
        }

        public boolean isChild() {
            return role != OptRole.ROOT;
        }

        public String toString() {
            switch (role) {
                case ROOT:
                    return opt.value;
                case KEY:
                    return opt.key;
                case VALUE:
                    return opt.value;
                case SCHEMA:
                    return opt.schema;
                case SCHEMA_URI:
                    return opt.schemaUri;
                default:
                    return "OPT";
            }
        }
    }
}
