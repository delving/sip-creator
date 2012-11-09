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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.schema.SchemaVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.*;

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
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("record-definition")
public class RecDef {

    private static final String DELIM = "[ ,]+";

    public static final String DEFAULT_FIELD_TYPE = "text";
    public static final String DELVING_NAMESPACE_URI = "http://schemas.delving.eu/";
    public static final String DELVING_PREFIX = "delving";
    public static final String[] REQUIRED_FIELDS = {
            "delving:title",
            "delving:owner",
            "delving:provider",
            "delving:landingPage",
            "delving:thumbnail"
    };
    public static final String LANDING_PAGE = REQUIRED_FIELDS[3];
    public static final String THUMBNAIL = REQUIRED_FIELDS[4];

    public static final String[][] BACKWARDS_COMPATIBILITY_REFRERENCE = {
            {"TITLE", "delving:title"},
            {"OWNER", "delving:owner"},
            {"PROVIDER", "delving:provider"},
            {"LANDING_PAGE", "delving:landingPage"},
            {"THUMBNAIL", "delving:thumbnail"},
            {"DESCRIPTION", "delving:description"},
            {"CREATOR", "delving:creator"},
            {"GEOHASH", "delving:geohash"},
            {"ADDRESS", "delving:address"},
            {"IMAGE_URL", "delving:imageUrl"},
            {"DEEP_ZOOM_URL", "delving:deepZoomUrl"},
    };

    public static RecDef read(InputStream in) {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            RecDef recDef = (RecDef) stream().fromXML(inReader);
            recDef.resolve();
            return recDef;
        }
        catch (UnsupportedEncodingException e) {
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

    public Elem root;

    public List<Dict> dicts;

    public List<OptList> opts;

    @XStreamAlias("field-markers")
    public List<FieldMarker> fieldMarkers;

    public List<Doc> docs;

    @XStreamOmitField
    public Map<String, Map<String, OptList.Opt>> optLookup = new TreeMap<String, Map<String, OptList.Opt>>();

    public SchemaVersion getSchemaVersion() {
        if (prefix == null || version == null) throw new IllegalArgumentException("Mapping lacks prefix or version");
        return new SchemaVersion(prefix, version);
    }

    public Map<String, String> getNamespacesMap() {
        Map<String, String> ns = new HashMap<String, String>();
        if (namespaces != null) for (Namespace namespace : namespaces) ns.put(namespace.prefix, namespace.uri);
        return ns;
    }

    public Attr attr(Tag tag, String where) {
        for (Attr attr : attrs) {
            attr.tag = attr.tag.defaultPrefix(prefix);
            if (attr.tag.equals(tag)) return attr;
        }
        throw new RuntimeException(String.format("No attr [%s] at %s", tag, where));
    }

    public AttrGroup attrGroup(String name, String where) {
        for (AttrGroup attrGroup : attrGroups) {
            if (name.equals(attrGroup.name)) return attrGroup;
        }
        throw new RuntimeException(String.format("No attr group [%s] at %s", name, where));
    }

    public Elem elem(Tag tag) {
        for (Elem elem : elems) if (elem.tag.equals(tag)) return elem.copy();
        throw new RuntimeException(String.format("No elem [%s]", tag));
    }

    public String getFieldType(Path path) {
        path = path.withDefaultPrefix(prefix);
        return root.getFieldType(path);
    }

    public String toString() {
        List<Path> paths = new ArrayList<Path>();
        Path path = Path.create();
        collectPaths(root, path, paths);
        StringBuilder out = new StringBuilder();
        for (Path p : paths) out.append(p.toString()).append('\n');
        return out.toString();
    }

    private void resolve() {
        if (prefix == null || version == null) throw new RuntimeException("Prefix and/or version missing");
        if (attrs != null) for (Attr attr : attrs) attr.tag = attr.tag.defaultPrefix(prefix);
        if (attrGroups != null) for (AttrGroup attrGroup : attrGroups) attrGroup.resolve(this);
        if (elems != null) for (Elem elem : elems) elem.tag = elem.tag.defaultPrefix(prefix);
        root.resolve(Path.create(), this);
        if (docs != null) for (Doc doc : docs) doc.resolve(this);
        if (opts != null) for (OptList optList : opts) optList.resolve(this);
    }

    private void collectPaths(Elem elem, Path path, List<Path> paths) {
        path = path.child(elem.tag);
        paths.add(path);
        for (Attr sub : elem.attrList) collectPaths(sub, path, paths);
        for (Elem sub : elem.elemList) collectPaths(sub, path, paths);
    }

    private void collectPaths(Attr attr, Path path, List<Path> paths) {
        path = path.child(attr.tag);
        paths.add(path);
    }

    Attr findAttr(Path path) {
        Attr found = root.findAttr(path, 0);
        if (found == null) throw new RuntimeException("No attribute found for path " + path);
        return found;
    }

    Elem findElem(Path path) {
        Elem found = root.findElem(path, 0);
        if (found == null) throw new RuntimeException("No element found for path " + path);
        return found;
    }

    @XStreamAlias("field-marker")
    public static class FieldMarker {

        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String type;

        @XStreamAsAttribute
        public Path path;

        public void resolve(RecDefTree recDefTree) {
            path = path.withDefaultPrefix(recDefTree.getRecDef().prefix);
            RecDefNode node = recDefTree.getRecDefNode(path);
            if (node == null) throw new RuntimeException("Cannot find path " + path);
            if (name == null) throw new RuntimeException("Field marker must have a name: " + path);
            for (String[] translation : BACKWARDS_COMPATIBILITY_REFRERENCE) {
                if (translation[0].equals(name)) {
                    name = translation[1];
                }
            }
            node.addFieldMarker(this);
        }
    }

    @XStreamAlias("doc")
    public static class Doc {

        @XStreamAsAttribute
        public Tag tag;

        @XStreamAsAttribute
        public Path path;

        @XStreamImplicit
        public List<String> lines;

        public List<DocParagraph> paras;

        public void resolve(RecDef recDef) {
            if (recDef.prefix == null) throw new RuntimeException("No prefix found");
            if (path != null) {
                path = path.withDefaultPrefix(recDef.prefix);
                Elem elem = recDef.root.findElem(path, 0);
                Attr attr = recDef.root.findAttr(path, 0);
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
    public static class Attr {

        @XStreamAsAttribute
        @XStreamConverter(Tag.AttributeConverter.class)
        public Tag tag;

        @XStreamAsAttribute
        public boolean hidden;

        @XStreamAsAttribute
        public String fieldType;

        @XStreamOmitField
        public Doc doc;

        public String toString() {
            return tag.toString();
        }

        public String getFieldType() {
            return fieldType != null ? fieldType : DEFAULT_FIELD_TYPE;
        }
    }

    @XStreamAlias("attr-group")
    public static class AttrGroup {
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
    }

    @XStreamAlias("elem")
    public static class Elem implements Cloneable {

        @XStreamAsAttribute
        public Tag tag;

        @XStreamAsAttribute
        @XStreamAlias("attr-groups")
        public String attrGroups;

        @XStreamAsAttribute
        public String attrs;

        @XStreamAsAttribute
        public String elems;

        @XStreamAsAttribute
        public boolean required;

        @XStreamAsAttribute
        public boolean singular;

        @XStreamAsAttribute
        public boolean hidden;

        @XStreamAsAttribute
        public boolean unmappable;

        @XStreamAsAttribute
        @XStreamConverter(Tag.AttributeConverter.class)
        public Tag discriminator;

        @XStreamAsAttribute
        public String fieldType;

        @XStreamAsAttribute
        public String function;

        @XStreamAsAttribute
        public Operator operator;

        @XStreamImplicit
        public List<Elem> elemList = new ArrayList<Elem>();

        @XStreamAlias("node-mapping")
        public NodeMapping nodeMapping;

        @XStreamOmitField
        public Doc doc;

        @XStreamOmitField
        public OptList optList;

        @XStreamOmitField
        public List<Attr> attrList = new ArrayList<Attr>();

        @XStreamOmitField
        public Attr discriminatorAttr;

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
            for (Attr attr : attrList) if (attr.tag.equals(nextTag)) return attr;
            for (Elem sub : elemList) {
                Attr found = sub.findAttr(path, level + 1);
                if (found != null) return found;
            }
            return null;
        }

        public void resolve(Path path, RecDef recDef) {
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
                    attrList.addAll(recDef.attrGroup(name, path.toString()).attrs);
                }
                attrGroups = null;
            }
            if (elems != null) {
                List<Elem> elemRefs = new ArrayList<Elem>();
                for (String localName : elems.split(DELIM)) {
                    elemRefs.add(recDef.elem(Tag.element(recDef.prefix, localName, null)));
                }
                elemList.addAll(0, elemRefs);
                elems = null;
            }
            for (Elem elem : elemList) elem.resolve(path, recDef);
            if (discriminator != null) {
                if (!discriminator.isAttribute()) throw new RuntimeException("Discriminator must be an attribute");
                discriminator = discriminator.defaultPrefix(recDef.prefix);
                for (Attr attr : attrList) {
                    if (attr.tag.equals(discriminator)) discriminatorAttr = attr;
                }
                if (discriminatorAttr == null) throw new RuntimeException("Discriminator attribute "+discriminator+" not found");
            }
        }

        String getFieldType() {
            return fieldType != null ? fieldType : DEFAULT_FIELD_TYPE;
        }

        private String getFieldType(Path path) {
            if (path.getTag(0).equals(tag)) {
                if (path.parent() == Path.ROOT) return getFieldType();
                Path subPath = path.withRootRemoved();
                for (Elem sub : elemList) {
                    String fieldType = sub.getFieldType(subPath);
                    if (fieldType != null) return fieldType;
                }
            }
            return null;
        }

        public Elem copy() {
            try {
                Elem clone = (Elem) this.clone();
                clone.attrList = new ArrayList<Attr>();
                clone.elemList = new ArrayList<Elem>();
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());
        stream.processAnnotations(RecDef.class);
        return stream;
    }
}
