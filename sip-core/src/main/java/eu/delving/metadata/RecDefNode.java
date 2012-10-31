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

import static eu.delving.metadata.OptRole.ABSENT;
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
    public static final String ABSENT_IS_FALSE = "_absent_ = false";
    private RecDefNode parent;
    private Path path;
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private OptBox optBox;
    private String defaultPrefix;
    private List<RecDef.FieldMarker> fieldMarkers = new ArrayList<RecDef.FieldMarker>();
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
        OptRole optRole = ABSENT;
        if (optBox != null) {
            optRole = optBox.role;
            switch (optBox.role) {
                case ROOT:
                    this.optBox = optBox;
                    break;
                case DESCENDANT:
                    this.optBox = optBox.inRoleFor(getPath());
                    if (this.optBox != null) optRole = this.optBox.role;
                    break;
            }
        }
        if (elem != null) {
            for (RecDef.Attr attribute : elem.attrList) {
                switch (optRole) {
                    case ROOT:
                        if (optBox == null) {
                            throw new RuntimeException("No OptBox");
                        }
                        children.add(subNode(attribute, optBox.createDescendant()));
                        break;
                    case DESCENDANT:
                        children.add(subNode(attribute, optBox)); // percolate deeper
                        break;
                    default:
                        children.add(subNode(attribute, null));
                        break;
                }
            }
            for (RecDef.Elem element : elem.elemList) {
                if (element.optList == null) {
                    switch (optRole) {
                        case ROOT:
                            if (optBox == null) {
                                throw new RuntimeException("No OptBox");
                            }
                            children.add(subNode(element, optBox.createDescendant()));
                            break;
                        case DESCENDANT:
                            children.add(subNode(element, optBox)); // percolate deeper
                            break;
                        default:
                            children.add(subNode(element, null));
                            break;
                    }
                }
                else if (element.optList.dictionary != null) {
                    children.add(subNode(element, OptBox.asRoot(element.optList)));
                }
                else {
                    for (OptList.Opt opt : element.optList.opts) { // a child for each option (introducing the OptBox instances)
                        children.add(subNode(element, OptBox.asRoot(opt)));
                    }
                }
            }
            if (elem.nodeMapping != null) {
                nodeMappings.put(elem.nodeMapping.inputPath, elem.nodeMapping);
                elem.nodeMapping.recDefNode = this;
                elem.nodeMapping.outputPath = getPath();
            }
        }
    }

    private RecDefNode subNode(RecDef.Attr attr, OptBox box) {
        return new RecDefNode(listener, this, null, attr, defaultPrefix, box);
    }

    private RecDefNode subNode(RecDef.Elem elem, OptBox box) {
        return new RecDefNode(listener, this, elem, null, defaultPrefix, box);
    }

    public OptBox getDictionaryOptBox() {
        return optBox != null && optBox.isDictionary() ? optBox : null;
    }

    public boolean isHiddenOpt(OptList.Opt shown) {
        return isRootOpt() && optBox.opt != shown && optBox.opt.hidden;
    }

    public String getFieldType() {
        return elem != null ? elem.getFieldType() : attr.getFieldType();
    }

    public boolean isAttr() {
        return attr != null;
    }

    public boolean isLeafElem() {
        return elem != null && elem.elemList.isEmpty();
    }

    public void addFieldMarker(RecDef.FieldMarker fieldMarker) {
        this.fieldMarkers.add(fieldMarker);
    }

    public List<RecDef.FieldMarker> getFieldMarkers() {
        return fieldMarkers;
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

    public RecDefNode getFirstNode(Tag soughtTag) {
        if (getTag().equals(soughtTag)) return this;
        for (RecDefNode sub : children) {
            RecDefNode found = sub.getFirstNode(soughtTag);
            if (found != null) return found;
        }
        return null;
    }

    public boolean hasDescendentNodeMappings() {
        if (!nodeMappings.isEmpty() || hasConstant()) return true;
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

    public void toElementCode(CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (isAttr() || !hasDescendentNodeMappings()) return;
        if (editPath != null && !path.isFamilyOf(editPath.getNodeMapping().outputPath)) return;
        if (nodeMappings.isEmpty()) {
            if (isRootOpt()) {
                Set<Path> siblingPaths = getSiblingInputPathsOfChildren();
                if (siblingPaths != null && !siblingPaths.isEmpty()) {
                    NodeMapping nodeMapping = new NodeMapping().setOutputPath(path).setInputPaths(siblingPaths);
                    nodeMapping.recDefNode = this;
                    codeOut.start(nodeMapping);
                    toNodeMappingLoop(codeOut, nodeMapping, nodeMapping.getLocalPath(), groovyParams, editPath);
                    codeOut.end(nodeMapping);
                    return;
                }
            }
            if (!isLeafElem()) {
                toBranchCode(codeOut, groovyParams, editPath);
            }
            else if (hasActiveAttributes()) {
                startBuilderCall(codeOut, false, "R0", groovyParams, editPath);
                codeOut.line("// no node mappings");
                codeOut._line("} // R0");
            }
        }
        else if (editPath != null && editPath.getNodeMapping().recDefNode == this) {
            NodeMapping nodeMapping = editPath.getNodeMapping();
            codeOut.line("_absent_ = true");
            codeOut.start(nodeMapping);
            toNodeMappingLoop(codeOut, nodeMapping, nodeMapping.getLocalPath(), groovyParams, editPath);
            codeOut.end(nodeMapping);
            addIfAbsentCode(codeOut, nodeMapping, groovyParams, editPath);
        }
        else {
            for (NodeMapping nodeMapping : nodeMappings.values()) {
                codeOut.line("_absent_ = true");
                codeOut.start(nodeMapping);
                toNodeMappingLoop(codeOut, nodeMapping, nodeMapping.getLocalPath(), groovyParams, editPath);
                codeOut.end(nodeMapping);
                addIfAbsentCode(codeOut, nodeMapping, groovyParams, editPath);
            }
        }
    }

    private void addIfAbsentCode(CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
        List<String> groovyCode = nodeMapping.groovyCode;
        if (editPath != null) {
            if (!editPath.isGeneratedCode()) {
                String editedCode = editPath.getEditedCode(getPath());
                if (editedCode != null) groovyCode = StringUtil.stringToLines(editedCode);
            }
        }
        List<String> ifAbsentCode = StringUtil.getIfAbsentCode(groovyCode);
        if (ifAbsentCode != null) {
            codeOut.line_("if (_absent_) {");
            startBuilderCall(codeOut, false, "R0a", groovyParams, editPath);
            indentCode(ifAbsentCode, codeOut);
            codeOut._line("} // R0a");
            codeOut._line("}");
        }
    }

    private void toNodeMappingLoop(CodeOut codeOut, NodeMapping nodeMapping, Path path, Stack<String> groovyParams, EditPath editPath) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            if (isLeafElem()) {
                toLeafCode(codeOut, nodeMapping, groovyParams, editPath);
            }
            else {
                toBranchCode(codeOut, groovyParams, editPath);
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            if (groovyParams.contains(nodeMapping.getMapName())) {
                toMapNodeMapping(codeOut, nodeMapping, groovyParams, editPath);
            }
            else {
                codeOut.line_(
                        "%s %s { %s -> // R1",
                        toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), nodeMapping.getMapName()
                );
                groovyParams.push(nodeMapping.getMapName());
                toMapNodeMapping(codeOut, nodeMapping, groovyParams, editPath);
                groovyParams.pop();
                codeOut._line("} // R1");
            }
        }
        else { // path should never be empty
            Operator operator = nodeMapping.getOperator();
            if (path.size() > 2 && operator != Operator.FIRST) operator = Operator.ALL;
            String param = toLoopGroovyParam(path);
            if (groovyParams.contains(param)) {
                toNodeMappingLoop(codeOut, nodeMapping, path.withRootRemoved(), groovyParams, editPath);
            }
            else {
                codeOut.line_(
                        "%s %s { %s -> // R6",
                        toLoopRef(path), operator.getCodeString(), param
                );
                groovyParams.push(param);
                toNodeMappingLoop(codeOut, nodeMapping, path.withRootRemoved(), groovyParams, editPath);
                groovyParams.pop();
                codeOut._line("} // R6");
            }
        }
    }

    private void toMapNodeMapping(CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
        if (isLeafElem()) {
            startBuilderCall(codeOut, true, "R3", groovyParams, editPath);
            codeOut.start(nodeMapping);
            nodeMapping.toLeafElementCode(codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R3");
        }
        else {
            startBuilderCall(codeOut, true, "R4", groovyParams, editPath);
            codeOut.start(nodeMapping);
            for (RecDefNode sub : children) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    codeOut.line(
                            "%s '%s' // R5",
                            sub.getTag().toBuilderCall(), sub.optBox
                    );
                }
                else {
                    sub.toElementCode(codeOut, groovyParams, editPath);
                }
            }
            codeOut.end(nodeMapping);
            codeOut._line("} // R4");
        }
    }

    private void toBranchCode(CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        startBuilderCall(codeOut, false, "R8", groovyParams, editPath);
        for (RecDefNode sub : children) {
            if (sub.isAttr()) continue;
            if (sub.isChildOpt()) {
                codeOut.line(
                        "%s '%s' // R9",
                        sub.getTag().toBuilderCall(), sub.optBox
                );
            }
            else {
                sub.toElementCode(codeOut, groovyParams, editPath);
            }
        }
        codeOut._line("} // R8");
    }

    private void toLeafCode(CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
        if (nodeMapping.hasMap()) {
            codeOut.line_(
                    "%s %s { %s -> // R10",
                    toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), nodeMapping.getMapName()
            );
            startBuilderCall(codeOut, true, "R11", groovyParams, editPath);
            codeOut.start(nodeMapping);
            nodeMapping.toLeafElementCode(codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R11");
            codeOut._line("} // R10");
        }
        else {
            startBuilderCall(codeOut, true, "R12", groovyParams, editPath);
            codeOut.start(nodeMapping);
            nodeMapping.toLeafElementCode(codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R12");
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
        return elem != null && optBox != null && optBox.role == OptRole.ROOT && !optBox.isDictionary();
    }

    private boolean isChildOpt() {
        return optBox != null && optBox.role != OptRole.ROOT;
    }

    private void startBuilderCall(CodeOut codeOut, boolean absentFalse, String comment, Stack<String> groovyParams, EditPath editPath) {
        if (hasActiveAttributes()) {
            Tag tag = getTag();
            codeOut.line_(
                    "%s ( // %s%s",
                    tag.toBuilderCall(), comment, isRootOpt() ? "(opt)" : ""
            );
            boolean comma = false;
            for (RecDefNode sub : children) {
                if (!sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    if (comma) codeOut.line(",");
                    OptBox dictionaryOptBox = sub.getDictionaryOptBox();
                    if (dictionaryOptBox != null) {
                        if (nodeMappings.size() == 1) {
                            NodeMapping nodeMapping = nodeMappings.values().iterator().next();
                            codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                            nodeMapping.toDictionaryCode(codeOut, groovyParams, sub.optBox.role);
                            codeOut._line("}");
                        }
                        else { // this is actually a kind of error:
                            codeOut.line("%s : '%s' // %sc", sub.getTag().toBuilderCall(), sub.optBox, comment);
                        }
                    }
                    else {
                        codeOut.line("%s : '%s' // %sc", sub.getTag().toBuilderCall(), sub.optBox, comment);
                    }
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.nodeMappings.values()) {
                        if (comma) codeOut.line(",");
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        codeOut.start(nodeMapping);
                        nodeMapping.toAttributeCode(codeOut, groovyParams, editPath);
                        codeOut.end(nodeMapping);
                        codeOut._line("}");
                        comma = true;
                    }
                }
            }
            codeOut._line(
                    ") { %s",
                    absentFalse ? ABSENT_IS_FALSE : ""
            ).in();
        }
        else {
            codeOut.line_(
                    "%s { %s // %s%s",
                    getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : "", comment, isRootOpt() ? "(opt)" : ""
            );
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

}
