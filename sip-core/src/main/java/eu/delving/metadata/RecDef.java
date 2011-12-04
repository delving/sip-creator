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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class defines the structure of a record definition, and it involves a lot of recursion.
 *
 * There is a system of references built in which parses comma-delimited lists of identifiers which
 * are sought in the lists of common elements and attributes attrs and elems.  Any of these can
 * be referred by a subsequent one and this makes it easy to have patterns repeated throughout
 * the record definition, as is often the case.
 *
 * The main part is stored in the Elem called "root", which is a composite tree of Elem and
 * Attr instances.
 *
 * Information about which values are allowed can be stored in a list of options for that element
 * by adding entries to the option lists and identifying the path.  These option lists are placed
 * into the appropriate places when the RecDef is resolved.
 *
 * The same is true for the Doc instances, which hold human-facing documentation about the meaning
 * of the element.
 *
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

    public List<Namespace> namespaces;

    public List<Attr> attrs;

    public List<Elem> elems;

    public Elem root;

    public List<OptionList> options;

    public List<Doc> docs;

    public List<Category> bookmarks;

    private Attr findAttr(Path path) {
        return root.findAttr(path, 0);
    }

    private Elem findElem(Path path) {
        return root.findElem(path, 0);
    }

    public Attr attr(Tag tag) {
        for (Attr def : attrs) if (def.tag.equals(tag)) return def;
        throw new RuntimeException(String.format("No attr [%s]", tag));
    }

    public Elem elem(Tag tag) {
        for (Elem def : elems) if (def.tag.equals(tag)) return def;
        throw new RuntimeException(String.format("No elem [%s]", tag));
    }

    public void print(StringBuilder out) {
        root.print(out, 0);
    }

    public String toString() {
        List<Path> paths = new ArrayList<Path>();
        Path path = new Path();
        collectPaths(root, path, paths);
        StringBuilder out = new StringBuilder();
        for (Path p : paths) out.append(p.toString()).append('\n');
        return out.toString();
    }

    private void resolve() {
        root.resolve(this);
        for (Doc doc : docs) doc.resolve(this);
        for (Category category : bookmarks) category.resolve(this);
        for (OptionList optionList : options) optionList.resolve(this);
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
        public Path path;

        @XStreamAsAttribute
        public String display;

        public List<Opt> options;

        public List<String> doc;

        @XStreamOmitField
        public Elem elem;

        @XStreamOmitField
        public Attr attr;

        public void resolve(RecDef recDef) {
            this.elem = recDef.findElem(path);
            this.attr = recDef.findAttr(path);
            if (this.attr == null && this.elem == null) throw new RuntimeException("Cannot resolve " + path);
        }

        public String toString() {
            return display;
        }

        public boolean isAttr() {
            return attr != null;
        }
    }

    @XStreamAlias("option-list")
    public static class OptionList {

        @XStreamAsAttribute
        public Path path;

        @XStreamImplicit
        public List<Opt> opts;

        public void resolve(RecDef recDef) {
            Elem elem = recDef.findElem(path);
            if (elem == null) throw new RuntimeException("Cannot find path " + path);
            elem.options = opts;
        }
    }

    @XStreamAlias("opt")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"content"})
    public static class Opt {

        @XStreamAsAttribute
        public String key;

        public String content;

        public Opt setContent(String content) {
            this.content = content;
            return this;
        }
    }

    @XStreamAlias("doc")
    public static class Doc {

        @XStreamAsAttribute
        public Tag tag;

        @XStreamImplicit
        public List<String> lines;

        public List<String> getLines() {
            return lines;
        }

        public void resolve(RecDef recDef) {
            if (lines == null) throw new RuntimeException("Lines is null for " + tag);
            Elem elem = recDef.root.findElem(tag);
            Attr attr = recDef.root.findAttr(tag);
            if (elem == null && attr == null) throw new RuntimeException("Cannot find tag " + tag);
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

    @XStreamAlias("attr")
    public static class Attr {

        @XStreamAsAttribute
        @XStreamConverter(Tag.AttributeConverter.class)
        public Tag tag;

        @XStreamOmitField
        public Doc doc;
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
        public SummaryField summaryField;

        @XStreamImplicit
        public List<Elem> elemList = new ArrayList<Elem>();

        @XStreamOmitField
        public Doc doc;

        @XStreamOmitField
        public List<Opt> options;

        @XStreamOmitField
        public List<Attr> attrList = new ArrayList<Attr>();

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

        public void resolve(RecDef recDef) {
            if (tag == null) throw new RuntimeException("Null tag!");
            if (attrs != null) {
                for (String tagString : attrs.split(DELIM)) attrList.add(recDef.attr(Tag.create(tagString)));
                attrs = null;
            }
            if (elems != null) {
                for (String tagString : elems.split(DELIM)) elemList.add(recDef.elem(Tag.create(tagString)));
                elems = null;
            }
            for (Elem elem : elemList) elem.resolve(recDef);
        }

        public void print(StringBuilder out, int level) {
            if (doc != null) {
                indent(out, level).append("/*\n");
                indent(out, level + 1).append(String.format("\"%s\"\n", doc.tag));
                for (String line : doc.lines) {
                    indent(out, level + 1).append(line).append('\n');
                }
                indent(out, level).append("*/\n");
            }
            if (options != null) {
                indent(out, level).append("// ");
                for (Opt opt : options) out.append(String.format("%s=%s, ", opt.key, opt.content));
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
