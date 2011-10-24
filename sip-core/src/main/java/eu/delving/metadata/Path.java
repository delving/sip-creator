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

import java.io.Serializable;
import java.util.Stack;

/**
 * A path consisting of a stack of instances of QName, representing the paths
 * of elements that came from the analysis process.
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class Path implements Comparable<Path>, Serializable {
    private Stack<Tag> stack = new Stack<Tag>();
    private String string;

    public Path() {
    }

    public Path(String pathString) {
        if (pathString != null) {
            if (!pathString.startsWith("/")) {
                pathString = "/" + pathString;
            }
            for (String part : pathString.substring(1).split("/")) {
                int at = part.indexOf("@");
                if (at > 0) {
                    String element = part.substring(0, at);
                    String attribute = part.substring(at + 1);
                    stack.push(Tag.element(element));
                    stack.push(Tag.attribute(attribute));
                }
                else {
                    stack.push(Tag.element(part));
                }
            }
        }
    }

    public Path(Path path) {
        if (path.stack != null) {
            for (Tag name : path.stack) {
                stack.push(name);
            }
        }
    }

    public Path(Path path, int count) {
        for (Tag name : path.stack) {
            if (count-- > 0) {
                stack.push(name);
            }
        }
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

    public Path pop() {
        stack.pop();
        string = null;
        return this;
    }

    @Override
    public boolean equals(Object path) {
        if (path == null || !(path instanceof Path)) return false;
        return toString().equals(path.toString());
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
        if (level < stack.size()) {
            return stack.get(level);
        }
        else {
            return null;
        }
    }

    public Tag peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
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
}
