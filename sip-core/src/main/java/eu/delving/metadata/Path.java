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
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A very frequently used class which stores a stack of Tag instances, which
 * correspond to a QName's prefix and localPart, and which can be either
 * for an element or an attribute.
 * <p/>
 * The string representation is cached for efficiency, since it is used a lot.
 * <p/>
 * There is a static converter class at the bottom so that these things can be
 * used easily in XStream.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("path")
public class Path implements Comparable<Path>, Serializable {
    private static final Pattern PAT = Pattern.compile("/([^\\[/]*)(\\[([^\\]]*)\\])?");
    public static final Path ROOT = new Path();
    private Path parent;
    private Tag tag;
    private String string;

    public static Path create() {
        return ROOT;
    }

    public static Path create(String pathString) {
        if (pathString == null) return create();
        if (!pathString.startsWith("/")) pathString = "/" + pathString;
        Path path = create();
        Matcher matcher = PAT.matcher(pathString);
        while (matcher.find()) {
            String part = matcher.group(0).substring(1);
            int at = part.indexOf("@");
            Tag newTag;
            if (at == 0) {
                newTag = Tag.attribute(part.substring(1));
            }
            else {
                newTag = Tag.element(part);
            }
            newTag.inContextOf(path);
            path = path.child(newTag);
        }
        return path;
    }

    private Path() {
    }

    private Path(Path parent, Tag tag) {
        this.parent = parent;
        this.tag = tag;
    }

    // modifiers

    public Path child(Tag newTag) {
        return new Path(this, newTag);
    }

    public Path descendant(Path path) {
        Path extended = this;
        for (int walk = 0; walk < path.size(); walk++) {
            Tag tag = path.getTag(walk);
            extended = extended.child(tag);
        }
        return extended;
    }

    public Path parent() {
        return parent;
    }

    public Path withDefaultPrefix(String prefix) {
        if (parent.isEmpty()) {
            return parent.child(tag.defaultPrefix(prefix));
        }
        else {
            return parent.withDefaultPrefix(prefix).child(tag.defaultPrefix(prefix));
        }
    }

    public Path withoutPrefixes() {
        if (parent.isEmpty()) {
            return parent.child(tag.withoutPrefix());
        }
        else {
            return parent.withoutPrefixes().child(tag.withoutPrefix());
        }
    }

    public Path takeFirst(int count) {
        return size() > count ? parent.takeFirst(count) : this;
    }

    public Path takeFirst() {
        Path ancestor = this.parent;
        while (!ancestor.isEmpty()) ancestor = ancestor.parent;
        return ancestor;
    }

    public Path withRootRemoved() {
        if (parent == null || parent.parent == null) throw new RuntimeException("Cannot remove root");
        if (parent.parent.isEmpty()) {
            return create().child(tag);
        }
        else {
            return parent.withRootRemoved().child(tag);
        }
    }

    public Path withoutOpts() {
        if (this == ROOT) return this;
        return this.parent().withoutOpts().child(tag.withOpt(null));
    }

    public Path extendAncestor(Path ancestor) {
        if (equals(ancestor)) {
            return create().child(ancestor.peek());
        }
        else {
            return parent.extendAncestor(ancestor).child(tag);
        }
    }

    // getters

    public boolean isFamilyOf(Path other) {
        return equals(other) || this.isAncestorOf(other) || other.isAncestorOf(this);
    }

    public boolean isAncestorOf(Path other) {
        return !other.isEmpty() && !isEmpty() && (equals(other.parent) || isAncestorOf(other.parent()));
    }

    @Override
    public boolean equals(Object path) {
        return !(path == null || !(path instanceof Path)) && toString().equals(path.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compareTo(Path that) {
        if (this.isEmpty() && that.isEmpty()) return 0;
        else if (this.isEmpty()) return -1;
        else if (that.isEmpty()) return 1;
        if (this.parent.isEmpty() && that.parent.isEmpty()) {
            int comparison = this.tag.compareTo(that.tag);
            if (comparison != 0) return comparison;
        }
        else if (this.parent.isEmpty()) {
            int comparison = this.compareTo(that.parent);
            if (comparison != 0) return comparison;
        }
        else if (that.parent.isEmpty()) {
            int comparison = this.parent.compareTo(that);
            if (comparison != 0) return comparison;
        }
        int comparison = this.parent.compareTo(that.parent);
        if (comparison != 0) return comparison;
        return this.tag.compareTo(that.tag);
    }

    public Tag getTag(int level) {
        int ourLevel = getLevel();
        if (ourLevel < level) return null;
        if (ourLevel == level) return tag;
        return parent.getTag(level);
    }

    public Tag peek() {
        return tag;
    }

    public int getLevel() {
        if (isEmpty()) return -1;
        return parent.getLevel() + 1;
    }

    public String getTail() {
        return peek().withoutPrefix().toString();
    }

    public boolean isEmpty() {
        return tag == null;
    }

    public int size() {
        if (parent != null) return parent.size() + 1;
        return 0;
    }

    public String toIndexString() {
        if (parent.tag == null) return "";
        return String.format("%s_%s", parent.toIndexString(), tag.toIndexString());
    }

    public String toString() {
        if (tag == null) return "";
        if (string == null) string = parent.toString() + tag.toPathElement();
        return string;
    }

    public static class Converter extends AbstractSingleValueConverter {

        @Override
        public boolean canConvert(Class type) {
            return Path.class.equals(type);
        }

        @Override
        public Object fromString(String str) {
            return create(str);
        }
    }
}
