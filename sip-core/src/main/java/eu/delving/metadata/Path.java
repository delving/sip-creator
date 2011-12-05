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

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;

/**
 * A very frequently used class which stores a stack of Tag instances, which
 * correspond to a QName's prefix and localPart, and which can be either
 * for an element or an attribute.
 *
 * The string representation is cached for efficiency, since it is used a lot.
 *
 * There is a static converter class at the bottom so that these things can be
 * used easily in XStream.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Path implements Comparable<Path>, Serializable {
    private Stack<Tag> stack = new Stack<Tag>();
    private String string;

    public static Path empty() {
        return new Path();
    }

    public static Path create(String pathString) {
        return new Path(pathString);
    }

    private Path() {
    }

    private Path(String pathString) {
        if (pathString != null) {
            if (!pathString.startsWith("/")) {
                pathString = "/" + pathString;
            }
            for (String part : pathString.substring(1).split("/")) {
                int at = part.indexOf("@");
                if (at > 0) throw new IllegalArgumentException("@ must be at the beginning of an attribute name");
                if (at == 0) {
                    stack.push(Tag.attribute(part.substring(1)));
                }
                else {
                    stack.push(Tag.element(part));
                }
            }
        }
    }

    private Path(Path path) {
        if (path.stack != null) for (Tag name : path.stack) stack.push(name);
    }

    private Path(Path path, int count) {
        if (count >= 0) {
            for (Tag name : path.stack) if (count-- > 0) stack.push(name);
        }
        else {
            for (Tag name : path.stack) if (count++ >= 0) stack.push(name);
        }
    }

    public Path copy() {
        return new Path(this);
    }

    public Path extend(Tag tag) {
        Path extended = new Path(this);
        extended.push(tag);
        return extended;
    }

    public void push(Tag tag) {
        stack.push(tag);
        string = null;
    }

    public Tag pop() {
        Tag tag = stack.pop();
        string = null;
        return tag;
    }

    public boolean isAncestorOf(Path other) {
        if (other == null) return false;
        Iterator<Tag> walkThis = stack.iterator();
        Iterator<Tag> walkOther = other.stack.iterator();
        while (walkThis.hasNext()) {
            if (!walkOther.hasNext()) return false; // other shorter than this
            Tag thisTag = walkThis.next();
            Tag otherTag = walkOther.next();
            if (!thisTag.equals(otherTag)) return false; // always equal
        }
        return walkOther.hasNext();
    }

    public Path chop(int index) {
        return new Path(this, index);
    }

    public Path minusAncestor(Path ancestor) {
        if (!ancestor.isAncestorOf(this)) throw new IllegalArgumentException(String.format("%s is not an ancestor of %s", ancestor, this));
        return chop(-ancestor.size());
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
    public int compareTo(Path path) {
        return toString().compareTo(path.toString());
    }

    public Tag getTag(int level) {
        return level < stack.size() ? stack.get(level) : null;
    }

    public Tag peek() {
        return stack.isEmpty() ? null : stack.peek();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int size() {
        return stack.size();
    }

    public String toString() {
        if (string == null) {
            StringBuilder builder = new StringBuilder(150);
            for (Tag tag : stack) {
                builder.append(tag.toPathElement());
            }
            string = builder.toString();
        }
        return string;
    }

    public static class Converter extends AbstractSingleValueConverter {

        @Override
        public boolean canConvert(Class type) {
            return Path.class.equals(type);
        }

        @Override
        public Object fromString(String str) {
            return new Path(str);
        }
    }
}
