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

import static eu.delving.metadata.OptRole.CHILD;
import static eu.delving.metadata.OptRole.ROOT;

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
    private DynOpt dynOpt;
    private String defaultPrefix;
    private List<RecDef.FieldMarker> fieldMarkers = new ArrayList<RecDef.FieldMarker>();
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private SortedMap<Path, NodeMapping> nodeMappings = new TreeMap<Path, NodeMapping>();
    private boolean populated;
    private RecDefNodeListener listener;

    @Override
    public int compareTo(RecDefNode recDefNode) {
        return getTag().compareTo(recDefNode.getTag());
    }

    public static RecDefNode create(RecDefNodeListener listener, RecDef recDef) {
        return new RecDefNode(listener, null, recDef.root, null, recDef.prefix, null, null); // only root element
    }

    private RecDefNode(RecDefNodeListener listener, RecDefNode parent, RecDef.Elem elem, RecDef.Attr attr, String defaultPrefix, OptBox optBox, DynOpt dynOpt) {
        this.listener = listener;
        this.parent = parent;
        this.elem = elem;
        this.attr = attr;
        this.defaultPrefix = defaultPrefix;
        this.dynOpt = dynOpt;
        boolean childOpt = optBox != null && optBox.role == CHILD;
        this.optBox = childOpt ? optBox.inRoleFor(getPath()) : optBox;
        if (elem != null) {
            boolean rootOpt = optBox != null && optBox.role == ROOT;
            for (RecDef.Attr attribute : elem.attrList) {
                addSubNode(attribute, rootOpt ? optBox.createDescendant() : optBox);
            }
            for (RecDef.Elem element : elem.elemList) {
                if (element.optList != null) {
                    addSubNode(element, OptBox.forList(element.optList)); // introducing ROOT opt with list
                }
                else {
                    addSubNode(element, rootOpt ? optBox.createDescendant() : optBox);
                }
            }
            if (elem.nodeMapping != null) { // a default node mapping inside of an element
                nodeMappings.put(elem.nodeMapping.inputPath, elem.nodeMapping);
                elem.nodeMapping.recDefNode = this;
                elem.nodeMapping.outputPath = getPath();
            }
        }
    }

    private void addSubNode(RecDef.Attr attr, OptBox box) {
        RecDefNode node = new RecDefNode(listener, this, null, attr, defaultPrefix, box, null);
        children.add(node);
    }

    private void addSubNode(RecDef.Elem elem, OptBox box) {
        RecDefNode node = new RecDefNode(listener, this, elem, null, defaultPrefix, box, null);
        children.add(node);
    }

    private RecDefNode addSubNodeAfter(RecDefNode child, RecDef.Elem elem, DynOpt dynOpt) {
        RecDefNode node = new RecDefNode(listener, this, elem, null, defaultPrefix, null, dynOpt);
        if (child != null) {
            int before = children.indexOf(child);
            if (before < 0) throw new RuntimeException("Cannot find child");
            children.add(before + 1, node);
        }
        else {
            children.add(node);
        }
        return node;
    }

    public RecDefNode addSibling(DynOpt dynOpt) {
        RecDefNode before = optBox != null && optBox.role == ROOT ? null : this;
        return parent.addSubNodeAfter(before, elem, dynOpt);
    }

    public boolean isPopulated() {
        return populated;
    }

    public boolean checkPopulated() {
        boolean populatedNow = (!nodeMappings.isEmpty() || dynOpt != null);
        if (!populatedNow && isChildOpt()) {
            RecDefNode ancestor = this;
            while (ancestor.parent != null) {
                if (optBox.optList != null) { // todo: maybe make sure it's a child!
                    Path optListRoot = optBox.optList.path;
                    Path cleanPath = ancestor.getPath().withoutOpts();
                    if (cleanPath.equals(optListRoot)) {
                        if (!ancestor.nodeMappings.isEmpty()) populatedNow = true;
                        break;
                    }
                }
                ancestor = ancestor.parent;
            }
        }
        for (RecDefNode sub : children) {
            sub.checkPopulated();
            if (sub.isPopulated()) populatedNow = true;
        }
        if (populated == populatedNow) return false;
        populated = populatedNow;
        listener.populationChanged(this);
        return true;
    }

    public boolean isDuplicatePossible() { // todo: check for singular
        return parent != null && !isAttr() && (optBox == null || optBox.role == ROOT);
    }

    public DynOpt getDynOpt() {
        return dynOpt;
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

    public RecDefNode getParent() {
        return parent;
    }

    public List<RecDefNode> getChildren() {
        return children;
    }

    public Tag getTag() {
        Tag tag = isAttr() ? attr.tag : elem.tag;
        if (dynOpt != null) {
            return tag.withOpt(dynOpt.value);
        }
        else {
            return tag;
        }
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
        // todo: should be comparing tags
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

    public void collectDynOpts(List<DynOpt> dynOpts) {
        if (dynOpt != null) {
            boolean childrenPopulated = false;
            for (RecDefNode sub : children) {
                if (sub.populated) childrenPopulated = true;
            }
            if (nodeMappings.size() > 1 || childrenPopulated) dynOpts.add(dynOpt);
        }
        for (RecDefNode sub : children) sub.collectDynOpts(dynOpts);
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

    public OptBox getOptBox() {
        return optBox;
    }

    public boolean isRootOptNoOptList() {
        return elem != null && optBox != null && optBox.role == OptRole.ROOT && optBox.optList == null;
    }

    public boolean isChildOpt() {
        return optBox != null && optBox.role != ROOT;
    }

    public boolean hasActiveAttributes() {
        if (dynOpt != null) return true;
        for (RecDefNode sub : children) {
            if (sub.isAttr() && (!sub.nodeMappings.isEmpty() || sub.isChildOpt())) return true;
        }
        return false;
    }

    public String toString() {
        String name = isAttr() ? attr.tag.toString(defaultPrefix) : elem.tag.toString(defaultPrefix);
        if (dynOpt != null) {
            name += String.format("[%s]", dynOpt);
        }
        if (isRootOptNoOptList()) {
            name += String.format("[%s]", optBox);
        }
        else if (isChildOpt()) {
            name += "{Constant}";
        }
        return name;
    }

}
