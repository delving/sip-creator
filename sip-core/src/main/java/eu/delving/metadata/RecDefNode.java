/*
 * Copyright 2010 DELVING BV
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
 * Wrap all nodes of a RecDef and give them important powers
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecDefNode {
    private RecDefNode parent;
    private RecDef.Elem elem;
    private RecDef.Attr attr;
    private List<RecDefNode> children = new ArrayList<RecDefNode>();
    private NodeMapping nodeMapping;

    public static RecDefNode create(RecDef recDef) {
        return new RecDefNode(null, recDef.root);
    }

    private RecDefNode(RecDefNode parent, RecDef.Elem elem) {
        this.parent = parent;
        this.elem = elem;
        for (RecDef.Attr sub : elem.attrList) children.add(new RecDefNode(this, sub));
        for (RecDef.Elem sub : elem.elemList) children.add(new RecDefNode(this, sub));
    }

    private RecDefNode(RecDefNode parent, RecDef.Attr attr) {
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

    public RecDefNode getNode(Path path, Path soughtPath) {
        path = path.extend(getTag());
        if (path.equals(soughtPath)) return this;
        for (RecDefNode sub : children) {
            RecDefNode found = sub.getNode(path, soughtPath);
            if (found != null) return found;
        }
        return null;
    }

    public void collect(Path path, List<NodeMapping> nodeMappings) {
        path = path.extend(getTag());
        if (nodeMapping != null) {
            nodeMapping.inputPath = path;
            nodeMappings.add(nodeMapping);
        }
        for (RecDefNode sub : children) {
            sub.collect(path, nodeMappings);
        }
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        this.nodeMapping = nodeMapping;
    }

    public String toString() {
        return isAttr() ? attr.tag.toString().substring(1) : elem.tag.toString();
    }

}
