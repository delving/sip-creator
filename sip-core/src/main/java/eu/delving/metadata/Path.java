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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

@XStreamAlias("path")
public class Path implements Comparable<Path>, Serializable {
    private static final Pattern PAT = Pattern.compile("/([^\\[/]*)(\\[([^\\]]*)\\])?");
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
            Matcher matcher = PAT.matcher(pathString);
            while (matcher.find()) {
                String part = matcher.group(0).substring(1);
                int at = part.indexOf("@");
                if (at > 0) throw new IllegalArgumentException("@ must be at the beginning of an attribute name");
                Tag newTag;
                if (at == 0) {
                    newTag = Tag.attribute(part.substring(1));
                }
                else {
                    newTag = Tag.element(part);
                }
                for (Tag tag : stack) newTag.inContextOf(tag);
                stack.push(newTag);
            }
        }
    }

    private Path(Path path) {
        if (path.stack != null) for (Tag tag : path.stack) stack.push(tag);
    }

    private Path(Path path, int n) {
        if (n >= 0) { // take the first n tags, ignore the rest
            for (Tag tag : path.stack) if (n-- > 0) stack.push(tag);
        }
        else {
            throw new IllegalArgumentException("negative!");
        }
    }

    // modifiers

    public Path extend(Tag newTag) {
        Path extended = new Path(this);
        for (Tag tag : stack) newTag.inContextOf(tag);
        extended.stack.push(newTag);
        return extended;
    }

    public Path shorten() {
        Path popped = new Path(this);
        popped.stack.pop();
        return popped;
    }

    public Path withDefaultPrefix(String prefix) {
        Path with = new Path(this);
        for (int walk=0; walk<size(); walk++) with.stack.set(walk, with.stack.get(walk).defaultPrefix(prefix));
        return with;
    }

    public Path takeFirst(int count) {
        return new Path(this, count);
    }

    public Path takeFirst() {
        return takeFirst(1);
    }

    public Path withRootRemoved() {
        Path removed = new Path(this);
        removed.stack.remove(0);
        return removed;
    }

    public Path extendAncestor(Path ancestor) {
        if (!(ancestor.isAncestorOf(this) || ancestor.equals(this))) {
            throw new IllegalArgumentException(String.format("%s is not an ancestor of %s", ancestor, this));
        }
        Path contained = new Path(this);
        for (int walk=0; walk<ancestor.size(); walk++) contained.stack.remove(0);
        contained.stack.insertElementAt(ancestor.peek(), 0);
        return contained;
    }

    public Path getParent() {
        Path parent = new Path(this);
        parent.stack.pop();
        return parent;
    }

    // getters

    public boolean isFamilyOf(Path other) {
        return equals(other) || this.isAncestorOf(other) || other.isAncestorOf(this);
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
        Iterator<Tag> us = stack.iterator();
        Iterator<Tag> them = path.stack.iterator();
        while (us.hasNext() && them.hasNext()) {
            int cmp = us.next().compareTo(them.next());
            if (cmp != 0) return cmp;
        }
        if (us.hasNext()) return -1;
        if (them.hasNext()) return 1;
        return 0;
    }

    public Tag getTag(int level) {
        if (level < 0) level = size() + level;
        return level < size() ? stack.get(level) : null;
    }

    public Tag peek() {
        return stack.isEmpty() ? null : stack.peek();
    }

    public String getTail() {
        return stack.isEmpty() ? "?" : stack.peek().toString();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int size() {
        return stack.size();
    }
    
    public Iterator<Tag> iterator() {
        return stack.iterator();
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
