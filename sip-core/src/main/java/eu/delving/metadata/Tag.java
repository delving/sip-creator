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

import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is an extremely frequently used class which holds the prefix and localPart
 * of an XML tag, either for an attribute or an element.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Tag implements Comparable<Tag>, Serializable {
    private static final Pattern PAT = Pattern.compile("(([^:]*):)?([^\\[/]*)(\\[([^\\]]*)\\])?");
    private boolean attribute;
    private int descendency;
    private String prefix;
    private String localName;
    private String opt;

    public static Tag element(QName qname) {
        return element(qname.getPrefix(), qname.getLocalPart(), null);
    }

    public static Tag attribute(QName qname) {
        return attribute(qname.getPrefix(), qname.getLocalPart());
    }

    public static Tag element(String prefix, String localName, String opt) {
        int colon = localName.indexOf(":");
        if (colon > 0 && !localName.contains("[")) {
            prefix = localName.substring(0, colon);
            localName = localName.substring(colon + 1);
        }
        return new Tag(false, prefix, localName, opt);
    }

    public static Tag attribute(String prefix, String localName) {
        int colon = localName.indexOf(":");
        if (colon > 0) {
            prefix = localName.substring(0, colon);
            localName = localName.substring(colon + 1);
        }
        return new Tag(true, prefix, localName, null);
    }

    public static Tag element(String tagString) {
        Matcher matcher = PAT.matcher(tagString);
        if (matcher.matches()) {
            return element(matcher.group(2), matcher.group(3), matcher.group(5));
        }
        else {
            throw new RuntimeException("Unable to create element from " + tagString);
        }
    }

    public static Tag attribute(String tagString) {
        int colon = tagString.indexOf(':');
        if (colon < 0) {
            return attribute(null, tagString);
        }
        else {
            return attribute(tagString.substring(0, colon), tagString.substring(colon + 1));
        }
    }

    public static Tag create(String tagString) {
        boolean attribute = tagString.startsWith("@");
        if (attribute) tagString = tagString.substring(1);
        Matcher matcher = PAT.matcher(tagString);
        if (matcher.matches()) {
            if (attribute) {
                return attribute(matcher.group(2), matcher.group(3));
            }
            else {
                return element(matcher.group(2), matcher.group(3), matcher.group(5));
            }
        }
        else {
            throw new RuntimeException("Unable to create tag from " + tagString);
        }
    }

    private Tag(boolean attribute, String prefix, String localName, String opt) {
        if (prefix != null && prefix.isEmpty()) prefix = null;
        this.attribute = attribute;
        this.prefix = prefix;
        this.localName = localName;
        this.opt = opt;
    }

    public boolean isAttribute() {
        return attribute;
    }

    public boolean isDescendent() {
        return descendency > 0;
    }

    public Tag defaultPrefix(String prefix) {
        if (this.prefix == null) {
            return attribute ? Tag.attribute(prefix, localName) : Tag.element(prefix, localName, opt);
        }
        else {
            return this;
        }
    }

    public void inContextOf(Tag ancestorTag) {
        if (this.equals(ancestorTag) && descendency <= ancestorTag.descendency) {
            descendency = ancestorTag.descendency + 1;
        }
    }

    public Tag withOpt(String optKey) {
        return new Tag(attribute, prefix, localName, optKey);
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLocalName() {
        return localName;
    }

    public String getOpt() {
        return opt;
    }

    public int getDescendency() {
        return descendency;
    }

    @Override
    public int compareTo(Tag tag) {
        if (prefix == null && tag.prefix != null) {
            return 1;
        }
        if (prefix != null && tag.prefix == null) {
            return -1;
        }
        if (!attribute && tag.attribute) {
            return 1;
        }
        if (attribute && !tag.attribute) {
            return -1;
        }
        if (prefix != null && tag.prefix != null) {
            int comp = prefix.compareTo(tag.prefix);
            if (comp != 0) return comp;
        }
        int local = localName.compareTo(tag.localName);
        if (local != 0) return local;
        if (opt == null && tag.opt != null) {
            return 1;
        }
        if (opt != null && tag.opt == null) {
            return -1;
        }
        if (opt != null && tag.opt != null) {
            return opt.compareTo(tag.opt);
        }
        else {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag tag = (Tag) o;

        if (attribute != tag.attribute) return false;
        if (opt != null ? !opt.equals(tag.opt) : tag.opt != null) return false;
        if (!localName.equals(tag.localName)) return false;
        if (prefix != null ? !prefix.equals(tag.prefix) : tag.prefix != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (attribute ? 1 : 0);
        result = 31 * result + (prefix != null ? prefix.hashCode() : 0);
        result = 31 * result + localName.hashCode();
        result = 31 * result + (opt != null ? opt.hashCode() : 0);
        return result;
    }

    public String toBuilderCall() {
        if (prefix != null) {
            return String.format("'%s:%s'", prefix, localName);
        }
        else {
            return localName;
        }
    }

    public String toMapKey() {
        if (prefix == null) {
            return StringUtil.tagToVariable(localName);
        }
        else {
            return prefix + StringUtil.tagToVariable(localName);
        }
    }

    public String toPathElement() {
        if (attribute) {
            return "/@" + toString();
        }
        else {
            return "/" + toString();
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        if (prefix != null) out.append(prefix).append(':');
        out.append(localName);
        if (opt != null) out.append('[').append(opt).append(']');
        return out.toString();
    }

    public static class Converter extends AbstractSingleValueConverter {

        @Override
        public boolean canConvert(Class type) {
            return Tag.class.equals(type);
        }

        @Override
        public Object fromString(String tagString) {
            return create(tagString);
        }
    }

    public static class AttributeConverter extends AbstractSingleValueConverter {

        @Override
        public boolean canConvert(Class type) {
            return Tag.class.equals(type);
        }

        @Override
        public Object fromString(String tagString) {
            return attribute(tagString);
        }
    }
}


