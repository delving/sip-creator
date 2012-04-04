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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

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
    private static final String INDENT = "    ";

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
    public boolean flat;

    public List<Namespace> namespaces;

    public List<Role> roles;

    public List<Attr> attrs;

    public List<Elem> elems;

    public Elem root;

    public List<OptList> opts;

    public List<FactRef> facts;

    public List<SearchField> searchFields;

    public List<Doc> docs;

    public List<Category> bookmarks;

    public Map<String, String> getNamespacesMap() {
        Map<String, String> ns = new HashMap<String, String>();
        if (namespaces != null) for (Namespace namespace : namespaces) ns.put(namespace.prefix, namespace.uri);
        return ns;
    }

    public Attr attr(Tag tag) {
        for (Attr def : attrs) {
            def.tag = def.tag.defaultPrefix(prefix);
            if (def.tag.equals(tag)) return def;
        }
        throw new RuntimeException(String.format("No attr [%s]", tag));
    }

    public Elem elem(Tag tag) {
        for (Elem def : elems) {
            def.tag = def.tag.defaultPrefix(prefix);
            if (def.tag.equals(tag)) return def;
        }
        throw new RuntimeException(String.format("No elem [%s]", tag));
    }

    public void print(StringBuilder out) {
        root.print(out, 0);
    }

    public String toString() {
        List<Path> paths = new ArrayList<Path>();
        Path path = Path.empty();
        collectPaths(root, path, paths);
        StringBuilder out = new StringBuilder();
        for (Path p : paths) out.append(p.toString()).append('\n');
        return out.toString();
    }

    private void resolve() {
        root.resolve(Path.empty(), this);
        if (searchFields != null) for (SearchField searchField : searchFields) searchField.resolve(this);
        if (docs != null) for (Doc doc : docs) doc.resolve(this);
        if (bookmarks != null) for (Category category : bookmarks) category.resolve(this);
        if (opts != null) for (OptList optList : opts) optList.resolve(this);
    }

    private void collectPaths(Elem elem, Path path, List<Path> paths) {
        path = path.extend(elem.tag);
        paths.add(path);
        for (Attr sub : elem.attrList) collectPaths(sub, path, paths);
        for (Elem sub : elem.elemList) collectPaths(sub, path, paths);
    }

    private void collectPaths(Attr attr, Path path, List<Path> paths) {
        path = path.extend(attr.tag);
        paths.add(path);
    }

    private Attr findAttr(Path path) {
        Attr found = root.findAttr(path, 0);
        if (found == null) throw new RuntimeException("No attribute found for path " + path);
        return found;
    }

    private Elem findElem(Path path) {
        Elem found = root.findElem(path, 0);
        if (found == null) throw new RuntimeException("No element found for path " + path);
        return found;
    }

    @XStreamAlias("category")
    public static class Category {

        @XStreamAsAttribute
        public String name;

        public List<String> doc;

        @XStreamImplicit
        public List<Ref> refs;

        public void resolve(RecDef recDef) {
            for (Ref ref : refs) ref.resolve(recDef);
        }

        public String toString() {
            return name;
        }
    }

    @XStreamAlias("ref")
    public static class Ref {

        @XStreamAsAttribute
        public Path outputPath;

        @XStreamAsAttribute
        public String display;

        public List<Opt> opts;

        public List<String> doc;

        @XStreamOmitField
        public Elem elem;

        @XStreamOmitField
        public Attr attr;

        public void resolve(RecDef recDef) {
            outputPath.defaultPrefix(recDef.prefix);
            if (outputPath.peek().isAttribute()) {
                this.attr = recDef.findAttr(outputPath);
            }
            else {
                this.elem = recDef.findElem(outputPath);
            }
        }

        public String toString() {
            return display;
        }

        public boolean isAttr() {
            return attr != null;
        }
    }

    @XStreamAlias("fact-ref")
    public static class FactRef {

        @XStreamAsAttribute
        public Path path;

        @XStreamAsAttribute
        public String name;
    }

    @XStreamAlias("opt-list")
    public static class OptList {

        @XStreamAsAttribute
        public Path path;

        @XStreamAsAttribute
        public Tag key;

        @XStreamAsAttribute
        public Tag value;

        @XStreamImplicit
        public List<Opt> opts;

        public void resolve(RecDef recDef) {
            for (Opt opt : opts) opt.parent = this;
            if (path == null) throw new RuntimeException("No path for OptList: " + opts);
            if (path.peek().isAttribute()) throw new RuntimeException("An option list may not be connected to an attribute: " + path);
            path.defaultPrefix(recDef.prefix);
            key = key.defaultPrefix(recDef.prefix);
            value = value.defaultPrefix(recDef.prefix);
            Elem elem = recDef.findElem(path);
            elem.optList = this;
        }
    }

    @XStreamAlias("opt")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class Opt {

        @XStreamAsAttribute
        public String key;

        public String content;
        
        @XStreamOmitField
        public OptList parent;

        public Opt setContent(String content) {
            this.content = content;
            return this;
        }

        public String toString() {
            return String.format("%s: %s", key, content);
        }
    }

    @XStreamAlias("searchField")
    public static class SearchField {

        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public Path path;

        public void resolve(RecDef recDef) {
            if (path.peek().isAttribute()) throw new RuntimeException("Attribute here?");
            Elem elem = recDef.findElem(path.defaultPrefix(recDef.prefix));
            elem.searchField = this;
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

        public void resolve(RecDef recDef) {
            if (recDef.prefix == null) throw new RuntimeException("No prefix found");
            Elem elem;
            Attr attr;
            if (path != null) {
                if (lines == null) throw new RuntimeException("Lines is null for " + path);
                path = path.defaultPrefix(recDef.prefix);
                elem = recDef.root.findElem(path, 0);
                attr = recDef.root.findAttr(path, 0);
                if (elem == null && attr == null) throw new RuntimeException("Cannot find path " + path);
            }
            else if (tag != null) {
                if (lines == null) throw new RuntimeException("Lines is null for " + tag);
                tag = tag.defaultPrefix(recDef.prefix);
                elem = recDef.root.findElem(tag);
                attr = recDef.root.findAttr(tag);
                if (elem == null && attr == null) throw new RuntimeException("Cannot find tag " + tag);
            }
            else {
                throw new RuntimeException("Neither path nor tag available: " + lines);
            }
            if (elem != null) elem.doc = this;
            if (attr != null) attr.doc = this;
        }
    }

    @XStreamAlias("namespace")
    public static class Namespace {

        @XStreamAsAttribute
        public String prefix;

        @XStreamAsAttribute
        public String uri;
    }

    @XStreamAlias("role")
    public static class Role {
        @XStreamAsAttribute
        public String name;

        @XStreamAsAttribute
        public String i18n;
    }

    @XStreamAlias("attr")
    public static class Attr {

        @XStreamAsAttribute
        @XStreamConverter(Tag.AttributeConverter.class)
        public Tag tag;

        @XStreamAsAttribute
        public boolean systemField;

        public List<String> options;

        @XStreamOmitField
        public Doc doc;
        
        public String toString() {
            return tag.toString();
        }
    }

    @XStreamAlias("elem")
    public static class Elem {

        @XStreamAsAttribute
        public Tag tag;

        @XStreamAsAttribute
        public String attrs;

        @XStreamAsAttribute
        public String elems;

        @XStreamAsAttribute
        public boolean required;

        @XStreamAsAttribute
        public boolean singular;

        @XStreamAsAttribute
        public boolean systemField;

        @XStreamAsAttribute
        public boolean unmappable;

        @XStreamAsAttribute
        public String fieldType;

        @XStreamAsAttribute
        public SummaryField summaryField;

        public List<String> options;

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
        public SearchField searchField;

        @Override
        public String toString() {
            return tag.toString();
        }

        public Elem findElem(Tag tag) {
            if (this.tag.equals(tag)) return this;
            for (Elem sub : elemList) {
                Elem found = sub.findElem(tag);
                if (found != null) return found;
            }
            return null;
        }

        public Attr findAttr(Tag tag) {
            for (Attr attr : attrList) if (attr.tag.equals(tag)) return attr;
            for (Elem sub : elemList) {
                Attr found = sub.findAttr(tag);
                if (found != null) return found;
            }
            return null;
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
            path.push(tag);
            tag = tag.defaultPrefix(recDef.prefix);
            if (attrs != null) {
                for (String localName : attrs.split(DELIM)) {
                    attrList.add(recDef.attr(Tag.attribute(recDef.prefix, localName)));
                }
                attrs = null;
            }
            if (elems != null) {
                for (String localName : elems.split(DELIM)) {
                    elemList.add(recDef.elem(Tag.element(recDef.prefix, localName, null)));
                }
                elems = null;
            }
            for (Elem elem : elemList) elem.resolve(path, recDef);
            path.pop();
        }

        public void print(StringBuilder out, int level) {
            if (doc != null) {
                indent(out, level).append("/*\n");
                indent(out, level + 1).append(String.format("\"%s\"\n", doc.path));
                for (String line : doc.lines) {
                    indent(out, level + 1).append(line).append('\n');
                }
                indent(out, level).append("*/\n");
            }
            if (optList != null) {
                indent(out, level).append("// ");
                for (Opt opt : optList.opts) out.append(String.format("%s=%s, ", opt.key, opt.content));
                out.append("\n");
            }
            indent(out, level).append("lido.");
            out.append(tag);
            if (!attrList.isEmpty()) {
                out.append("(");
                Iterator<Attr> walk = attrList.iterator();
                while (walk.hasNext()) {
                    Attr def = walk.next();
                    out.append(def.tag);
                    out.append(": \"attrval\"");
                    if (walk.hasNext()) out.append(", ");
                }
                if (elemList.isEmpty()) {
                    out.append(", \"elemval\")");
                }
                else {
                    out.append(")");
                }
            }
            if (elemList.isEmpty()) {
                if (required || singular) {
                    out.append(" // ");
                    if (required) out.append("required ");
                    if (singular) out.append("singular ");
                }
                out.append("\n");
            }
            else {
                if (required || singular) {
                    out.append(" {");
                    out.append(" // ");
                    if (required) out.append("required ");
                    if (singular) out.append("singular ");
                    out.append("\n");
                }
                else {
                    out.append(" {\n");
                }
                for (Elem def : elemList) def.print(out, level + 1);
                indent(out, level).append("}\n");
            }
        }

        private StringBuilder indent(StringBuilder out, int level) {
            for (int count = 0; count < level; count++) out.append(INDENT);
            return out;
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
