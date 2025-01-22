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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import eu.delving.XStreamFactory;
import eu.delving.schema.SchemaVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class defines the structure of a record definition, and it involves a lot of recursion.
 * <p/>
 * There is a system of references built in which parses comma-delimited lists of identifiers which
 * are sought in the lists of common elements and attributes attrs and elems.  Any of these can
 * be referred by a subsequent one and this makes it easy to have patterns repeated throughout
 * the record definition, as is often the case.
 * <p/>
 * The main part is stored in the Elem called "root", which is a composite tree of Elem and
 * Attr instances.
 * <p/>
 * Information about which values are allowed can be stored in a list of options for that element
 * by adding entries to the option lists and identifying the path.  These option lists are placed
 * into the appropriate places when the RecDef is resolved.
 * <p/>
 * The same is true for the Doc instances, which hold human-facing documentation about the meaning
 * of the element.
 * <p/>
 * Bookmarks are a mechanism which provides for more easy navigation of elaborate record definitions
 * which are hard to work with due to their size.  Each bookmark can also include its own documentation,
 * which can instruct the mapper how to use the element to which it refers.
 */

@XStreamAlias("record-definition")
public class RecDef {

    private static final String DELIM = "[ ,]+";
    public static final RecDef.Namespace XML_NAMESPACE = new RecDef.Namespace("xml", "http://www.w3.org/XML/1998/namespace", null);
    public static final RecDef.Namespace XSI_NAMESPACE = new RecDef.Namespace("xsi", "http://www.w3.org/2001/XMLSchema-instance", null);

    public static final String DELVING_NAMESPACE_URI = "http://schemas.delving.eu/";
    public static final String DELVING_PREFIX = "delving";
    public RecDefTree.SourceTree sourceTree;

    public static RecDef read(InputStream in) {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            RecDef recDef = (RecDef) XStreamFactory.getStreamFor(RecDef.class).fromXML(inReader);
            recDef.resolve();
            return recDef;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private RecDef() {
    }

    @XStreamAsAttribute
    public String prefix;

    @XStreamAsAttribute
    public String version;

    @XStreamAsAttribute
    public boolean flat;

    @XStreamAsAttribute
    public boolean elementFormDefaultQualified = true;

    @XStreamAsAttribute
    public boolean attributeFormDefaultQualified = true;

    public List<Namespace> namespaces;

    public List<MappingFunction> functions;

    public List<Attr> attrs;

    @XStreamAlias("attr-groups")
    public List<AttrGroup> attrGroups;

    public List<Elem> elems;

    @XStreamAlias("elem-groups")
    public List<ElemGroup> elemGroups;

    public List<Elem> templates;

    public Elem root;

    public List<OptList> opts;

    @XStreamAlias("assertion-list")
    public Assertion.AssertionList assertionList;

    @XStreamAlias("field-markers")
    public List<FieldMarker> fieldMarkers;

    public List<Doc> docs;

    @XStreamOmitField
    public Map<String, Map<String, OptList.Opt>> valueOptLookup = new TreeMap<String, Map<String, OptList.Opt>>();

    public SchemaVersion getSchemaVersion() {
        if (prefix == null || version == null) throw new IllegalArgumentException("Mapping lacks prefix or version");
        return new SchemaVersion(prefix, version);
    }

    public Map<String, Namespace> getNamespaceMap() {
        Map<String, Namespace> namespaces = new HashMap<String, Namespace>();
        if (this.namespaces != null) {
            for (Namespace namespace : this.namespaces) namespaces.put(namespace.prefix, namespace);
        }
        namespaces.put(XML_NAMESPACE.prefix, XML_NAMESPACE);
        namespaces.put(XSI_NAMESPACE.prefix, XSI_NAMESPACE);
        return namespaces;
    }

    public Attr attr(Tag tag, String where) {
        for (Attr attr : attrs) {
            attr.tag = attr.tag.defaultPrefix(prefix);
            if (attr.tag.equals(tag)) return attr.copy();
        }
        throw new RuntimeException(String.format("No attr [%s] at %s", tag, where));
    }

    public AttrGroup attrGroup(String name, String where) {
        for (AttrGroup attrGroup : attrGroups) {
            if (name.equals(attrGroup.name)) return attrGroup;
        }
        throw new RuntimeException(String.format("No attr group [%s] at %s", name, where));
    }

    public Elem elem(Tag tag, String where) {
        for (Elem elem : elems) {
            if (elem.tag.equals(tag)) {
                return elem.deepCopy();
            }
        }
        throw new RuntimeException(String.format("No elem [%s] at %s", tag, where));
    }

    public ElemGroup elemGroup(String name, String where) {
        for (ElemGroup elemGroup : elemGroups) {
            if (name.equals(elemGroup.name)) return elemGroup;
        }
        throw new RuntimeException(String.format("No elem group [%s] at %s", name, where));
    }

    public String toString() {
        return String.format("RecDef(%s/%s)", prefix, version);
    }

    private Map<String, Elem> resolveTemplates() {
        if(templates == null) {
            return new HashMap<>();
        }

        Map<String, Elem> templatesByTag = new HashMap<>();
        for (Elem template : templates) {
            templatesByTag.put(template.tag.toString(), template);
        }

        for (Elem template : templates) {

            if (template.target != null) {

                String[] targets = template.target.split(",");
                for (String target : targets) {
                    Elem targetTemplate = templatesByTag.get(target);
                    if (targetTemplate == null) {
                        throw new IllegalStateException("Unable to locate template with tag=" + target);
                    }
                }
            }
        }
        return templatesByTag;
    }

    private void resolve() {
        if (prefix == null || version == null) throw new RuntimeException("Prefix and/or version missing");
        if (attrs != null) for (Attr attr : attrs) attr.tag = attr.tag.defaultPrefix(prefix);
        if (attrGroups != null) for (AttrGroup attrGroup : attrGroups) attrGroup.resolve(this);
        if (elems != null) for (Elem elem : elems) elem.tag = elem.tag.defaultPrefix(prefix);

        Map<String, Elem> templatesByTag = resolveTemplates();
        root.resolve(Path.create(), this, templatesByTag);

        if (docs != null) for (Doc doc : docs) doc.resolve(this);
        if (opts != null) for (OptList optList : opts) optList.resolve(this);
    }

    Attr findAttr(Path path) {
        Attr found = root.findAttr(path, 0);
        if (found == null) {
            throw new RuntimeException("No attribute found for path " + path);
        }
        return found;
    }

    Elem findElem(Path path) {
        Elem found = root.findElem(path, 0);
        if (found == null) {
            throw new RuntimeException("No element found for path " + path);
        }
        return found;
    }

    public enum Check {
        LANDING_PAGE(true, false),
        DIGITAL_OBJECT(true, true),
        THUMBNAIL(true, true),
        DEEP_ZOOM(true, false),
        THESAURUS_REFERENCE(true, false),
        LOD_REFERENCE(true, false),
        GEO_COORDINATE(false, false),
        DATE(false, false);

        public final boolean fetch;
        public final boolean captureSize;

        private Check(boolean fetch, boolean captureSize) {
            this.fetch = fetch;
            this.captureSize = captureSize;
        }
    }

    @XStreamAlias("field-marker")
    public static class FieldMarker {

        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String type;

        @XStreamAsAttribute
        public Path path;

        @XStreamAsAttribute
        public String xpath;

        @XStreamAsAttribute
        public Check check;

        public void resolve(RecDefTree recDefTree) {
            if (path != null) {
                path = path.withDefaultPrefix(recDefTree.getRecDef().prefix);
            }
        }

        public String getXPath() {
            return xpath != null ? xpath : path.toString();
        }

        public boolean hasPath() {
            return xpath != null || path != null;
        }
    }

    @XStreamAlias("doc")
    public static class Doc {

        @XStreamAsAttribute
        public Tag tag;

        @XStreamAsAttribute
        public Path path;

        @XStreamImplicit(itemFieldName="string")
        public List<String> lines;

        @XStreamImplicit(itemFieldName="para")
        public List<DocParagraph> paraList;

        public List<DocParagraph> paras;

        public void resolve(RecDef recDef) {
            if (recDef.prefix == null) throw new RuntimeException("No prefix found");
            if (path != null) {
                path = path.withDefaultPrefix(recDef.root.tag.getPrefix());
                boolean isAttr = path.peek().isAttribute();
                Elem elem = isAttr ? null : recDef.root.findElem(path, 0);
                Attr attr = isAttr ? recDef.root.findAttr(path, 0) : null;
                if (elem == null && attr == null) throw new RuntimeException("Cannot find path " + path);
                if (elem != null) elem.doc = this;
                if (attr != null) attr.doc = this;
            }
            else if (tag != null) {
                tag = tag.defaultPrefix(recDef.prefix);
                resolveTag(recDef.root);
            }
            else {
                throw new RuntimeException("Neither path nor tag available!");
            }
        }

        private void resolveTag(Elem elem) {
            if (this.tag.isAttribute()) {
                if (elem.attrList != null) for (Attr attr : elem.attrList) {
                    if (attr.tag.equals(this.tag)) attr.doc = this;
                }
            }
            else {
                if (elem.tag.equals(this.tag)) elem.doc = this;
            }
            if (elem.elemList != null) for (Elem subElem : elem.elemList) {
                resolveTag(subElem);
            }
        }
    }

    @XStreamAlias("para")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class DocParagraph {
        @XStreamAsAttribute
        public String name;

        public String content;
    }

    @XStreamAlias("namespace")
    public static class Namespace {

        public Namespace(String prefix, String uri, String schema) {
            this.prefix = prefix;
            this.uri = uri;
            this.schema = schema;
        }

        public Namespace() {
        }

        @XStreamAsAttribute
        public String prefix;

        @XStreamAsAttribute
        public String uri;

        @XStreamAsAttribute
        public String schema;
    }

    @XStreamAlias("attr")
    public static class Attr implements Cloneable {

        @XStreamAsAttribute
        @XStreamConverter(Tag.AttributeConverter.class)
        public Tag tag;

        @XStreamAsAttribute
        public boolean required;

        @XStreamAsAttribute
        public boolean simple;

        @XStreamAsAttribute
        public boolean uriCheck;

        @XStreamAsAttribute
        public boolean hidden;

        @XStreamAsAttribute
        public String initialValue;

        @XStreamAlias("node-mapping")
        public NodeMapping nodeMapping;

        @XStreamOmitField
        public Doc doc;

        public String toString() {
            return tag.toString();
        }

        public Attr copy() {
            try {
                return (Attr) this.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @XStreamAlias("attr-group")
    public static class AttrGroup implements Cloneable {
        @XStreamAsAttribute
        public String name;

        @XStreamImplicit
        public List<String> tags;

        @XStreamOmitField
        public List<Attr> attrs = new ArrayList<Attr>();

        public void resolve(RecDef recDef) {
            for (String tagString : tags) {
                Tag tag = Tag.attribute(tagString).defaultPrefix(recDef.prefix);
                attrs.add(recDef.attr(tag, "attr-group " + name));
            }
        }

        public AttrGroup deepCopy() {
            try {
                AttrGroup clone = (AttrGroup) this.clone();
                clone.attrs = new ArrayList<Attr>();
                for (Attr attr : attrs) clone.attrs.add(attr.copy());
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @XStreamAlias("elem")
    public static class Elem implements Cloneable {

        @XStreamAsAttribute
        public String label;

        @XStreamAsAttribute
        public Tag tag;

        @XStreamAsAttribute
        @XStreamAlias("attr-groups")
        public String attrGroups;

        @XStreamAsAttribute
        public String attrs;

        @XStreamAsAttribute
        @XStreamAlias("elem-groups")
        public String elemGroups;

        @XStreamAsAttribute
        public String elems;

        @XStreamAsAttribute
        public boolean required;

        @XStreamAsAttribute
        public boolean simple;

        @XStreamAsAttribute
        public boolean singular;

        @XStreamAsAttribute
        public boolean uriCheck;

        @XStreamAsAttribute
        public boolean hidden;

        @XStreamAsAttribute
        public boolean unmappable;

        @XStreamAsAttribute
        public String function;

        @XStreamAsAttribute
        public Operator operator;

        @XStreamAsAttribute
        public String initialValue;

        public Assertion assertion;

        @XStreamImplicit
        public List<Attr> subattrs = new ArrayList<Attr>();

        @XStreamImplicit
        public List<Elem> subelements = new ArrayList<Elem>();

        @XStreamAlias("node-mapping")
        public NodeMapping nodeMapping;

        @XStreamOmitField
        public Doc doc;

        @XStreamOmitField
        public OptList optList;

        @XStreamOmitField
        public List<Attr> attrList = new ArrayList<Attr>();

        @XStreamOmitField
        public List<Elem> elemList = new ArrayList<Elem>();

        @XStreamOmitField
        public boolean attrsAdded;

        @XStreamOmitField
        public boolean subelementsAdded;

        @XStreamAsAttribute
        public String target;

        @Override
        public String toString() {
            return tag.toString();
        }

        public Elem findElem(Path path, int level) {
            Tag levelTag = path.getTag(level);
            if (levelTag == null || !levelTag.equals(tag)) return null;
            if (level == path.size() - 1) return this;
            for (Elem sub : elemList) {
                Elem found = sub.findElem(path, level + 1);
                if (found != null) return found;
            }
            return null;
        }

        public Attr findAttr(Path path, int level) {
            if (!path.getTag(level).equals(tag)) return null;
            Tag nextTag = path.getTag(level + 1);
            if (nextTag == null) return null;
            for (Attr attr : attrList) if (attr.tag.equals(nextTag)) return attr;
            for (Elem sub : elemList) {
                Attr found = sub.findAttr(path, level + 1);
                if (found != null) return found;
            }
            return null;
        }

        public void resolve(Path path, RecDef recDef, Map<String, Elem> templatesByTag) {
            if (tag == null) throw new RuntimeException("Null tag at: " + path);
            path = path.child(tag);
            tag = tag.defaultPrefix(recDef.prefix);
            if (attrs != null) {
                for (String localName : attrs.split(DELIM)) {
                    attrList.add(recDef.attr(Tag.attribute(recDef.prefix, localName), path.toString()));
                }
                attrs = null;
            }
            if (attrGroups != null) {
                for (String name : attrGroups.split(DELIM)) {
                    attrList.addAll(recDef.attrGroup(name, path.toString()).deepCopy().attrs);
                }
                attrGroups = null;
            }
            if (!attrsAdded) {
                attrList.addAll(subattrs);
                attrsAdded = true;
            }
            if (elems != null) {
                for (String localName : elems.split(DELIM)) {
                    elemList.add(recDef.elem(Tag.element(recDef.prefix, localName, null), path.toString()));
                }
                elems = null;
            }
            if (elemGroups != null) {
                for (String name : elemGroups.split(DELIM)) {
                    elemList.addAll(recDef.elemGroup(name, path.toString()).deepCopy().elems);
                }
                attrGroups = null;
            }
            if (!subelementsAdded) {
                elemList.addAll(subelements);
                subelementsAdded = true;
            }

            if (target != null) {
                String[] targets = target.split(",");
                for (String target : targets) {
                    if(target.isEmpty()) {
                        continue;
                    }
                    Elem template = templatesByTag.get(target);
                    if (template == null) {
                        throw new IllegalStateException("Unable to locate template with tag=" + target);
                    }
                    elemList.add(template.deepCopy());
                }
            }

            for (Elem elem : elemList) elem.resolve(path, recDef, templatesByTag);
        }

        public Elem deepCopy() {
            try {
                Elem clone = (Elem) this.clone();
                clone.attrList = new ArrayList<Attr>();
                for (Attr attr : attrList) clone.attrList.add(attr.copy());
                clone.elemList = new ArrayList<Elem>();
                for (Elem elem : elemList) clone.elemList.add(elem.deepCopy());
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @XStreamAlias("elem-group")
    public static class ElemGroup implements Cloneable {
        @XStreamAsAttribute
        public String name;

        @XStreamImplicit
        public List<Elem> elems = new ArrayList<Elem>();

        public ElemGroup deepCopy() {
            try {
                ElemGroup clone = (ElemGroup) this.clone();
                clone.elems = new ArrayList<Elem>();
                for (Elem elem : elems) clone.elems.add(elem.deepCopy());
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }


}
