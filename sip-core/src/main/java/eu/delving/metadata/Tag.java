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

/**
 * This is an extremely frequently used class which holds the prefix and localPart
 * of an XML tag, either for an attribute or an element.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Tag implements Comparable<Tag>, Serializable {
    private boolean attribute;
    private String prefix;
    private String localName;

    public static Tag element(QName qname) {
        return element(qname.getPrefix(), qname.getLocalPart());
    }

    public static Tag attribute(QName qname) {
        return attribute(qname.getPrefix(), qname.getLocalPart());
    }

    public static Tag element(String prefix, String localName) {
        return new Tag(false, prefix, localName);
    }

    public static Tag attribute(String prefix, String localName) {
        return new Tag(true, prefix, localName);
    }

    public static Tag element(String tagString) {
        int colon = tagString.indexOf(':');
        if (colon < 0) {
            return element(null, tagString);
        }
        else {
            return element(tagString.substring(0, colon), tagString.substring(colon + 1));
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
        int colon = tagString.indexOf(':');
        String prefix, localPart;
        if (colon < 0) {
            prefix = null;
            localPart = tagString;
        }
        else {
            prefix = tagString.substring(0, colon);
            localPart = tagString.substring(colon + 1);
        }
        return attribute ? attribute(prefix, localPart) : element(prefix, localPart);
    }

    private Tag(boolean attribute, String prefix, String localName) {
        if (prefix != null && prefix.isEmpty()) {
            prefix = null;
        }
        this.attribute = attribute;
        this.prefix = prefix;
        this.localName = localName;
    }

    public boolean isAttribute() {
        return attribute;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLocalName() {
        return localName;
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
            if (comp != 0) {
                return comp;
            }
        }
        return localName.compareTo(tag.localName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return !(localName != null ? !localName.equals(tag.localName) : tag.localName != null) && !(prefix != null ? !prefix.equals(tag.prefix) : tag.prefix != null);
    }

    @Override
    public int hashCode() {
        int result = prefix != null ? prefix.hashCode() : 0;
        result = 31 * result + (localName != null ? localName.hashCode() : 0);
        return result;
    }

    public String toPathElement() {
        if (attribute) {
            return "/@" + (prefix != null ? prefix + ":" + localName : localName);
        }
        else {
            return "/" + (prefix != null ? prefix + ":" + localName : localName);
        }
    }

    public String toString() {
        if (attribute) {
            return "@" + (prefix != null ? prefix + ":" + localName : localName);
        }
        else {
            return prefix != null ? prefix + ":" + localName : localName;
        }
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


