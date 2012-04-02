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

    public Tag defaultPrefix(String prefix) {
        if (this.prefix == null) {
            return attribute ? Tag.attribute(prefix, localName) : Tag.element(prefix, localName, opt);
        }
        else {
            return this;
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

    public String toGroovyRef() {
        return attribute ? String.format("['@%s']", toString()) : "." + nameToVariable(toString());
    }

    public String toGroovyParam() {
        return "_" + nameToVariable(toString());
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

    private static String nameToVariable(String name) {
        if (name.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(name.length());
        int n = name.length();
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            int pos = UNICODE.indexOf(c);
            if (pos > -1) {
                sb.append(PLAIN_ASCII.charAt(pos));
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String UNICODE =
            "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"
                    + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"
                    + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"
                    + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"
                    + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"
                    + "\u00C5\u00E5"
                    + "\u00C7\u00E7"
                    + "\u0150\u0151\u0170\u0171"
                    + "-"
                    + "."
                    + ":";

    private static final String PLAIN_ASCII =
            "AaEeIiOoUu"    // grave
                    + "AaEeIiOoUuYy"  // acute
                    + "AaEeIiOoUuYy"  // circumflex
                    + "AaOoNn"        // tilde
                    + "AaEeIiOoUuYy"  // umlaut
                    + "Aa"            // ring
                    + "Cc"            // cedilla
                    + "OoUu"          // double acute
                    + "_"             // dash
                    + "_"             // dot
                    + "_"             // colon
            ;

}


