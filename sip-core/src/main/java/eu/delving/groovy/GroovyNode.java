/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.groovy;

import eu.delving.metadata.StringUtil;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.util.NodeList;
import groovy.namespace.QName;

import java.util.*;

/**
 * A variation on the Groovy Node class which is used to store XML-like data
 * in memory.
 * <p/>
 * Trimmed down from the original and with some added or modified functions to make
 * it easier to use these as input variables in the mapping code.
 * <p/>
 * The MetadataParser produces records containing structures of these nodes.
 *
 *
 */
@SuppressWarnings("unchecked")
public class GroovyNode {

    private GroovyNode parent;
    private QName qName;
    private String stringName;
    private Map<String, String> attributes;
    // TODO make sure accessors can handle its new value type
    private Object nodeValue;
    private int hashCode;
    public String text;
    public final List<GroovyNode> children = new NodeList(1);

    public GroovyNode(GroovyNode parent, String namespaceUri, String localName, String prefix) {
        this(parent, new QName(namespaceUri, localName, prefix == null ? "" : prefix));
    }

    public GroovyNode(GroovyNode parent, String name) {
        this(parent, new QName(name), null);
    }

    public GroovyNode(GroovyNode parent, QName qName) {
        this(parent, qName, null);
    }

    public GroovyNode(GroovyNode parent, String name, String nodeValue) {
        this(parent, new QName(name), new TreeMap<>(), nodeValue);
    }

    public GroovyNode(GroovyNode parent, QName qName, String nodeValue) {
        this(parent, qName, new TreeMap<>(), nodeValue);
    }

    public GroovyNode(GroovyNode parent, QName qName, Map<String, String> attributes, String nodeValue) {
        this.parent = parent;
        this.qName = qName;
        this.attributes = attributes;

        if (parent != null)
            parent.children.add(this);
        setNodeValue(nodeValue);
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public QName qName() {
        return qName;
    }

    public String getNodeName() {
        if (stringName == null) stringName = StringUtil.tagToVariable(qName.getPrefix() + qName.getLocalPart());
        return stringName;
    }

    public int size() {
        return text.length();
    }

    public boolean contains(String s) {
        return text.contains(s);
    }

    public String[] split(String s) {
        return PatternCache.getPattern(s).split(text);
    }

    public boolean endsWith(String s) {
        return text.endsWith(s);
    }

    public String replaceAll(String from, String to) {
        return PatternCache.getPattern(from).matcher(text).replaceAll(to);
    }

    public Object getNodeValue() {
        return nodeValue;
    }

    public void setNodeValue(String nodeValue) {
        if(nodeValue != null) {
            this.text = nodeValue.trim();
            this.hashCode = text.hashCode();
        } else {
            this.text = "";
        }
        this.nodeValue = text;
    }

    public GroovyNode parent() {
        return parent;
    }

    public List getChildren() {
        return children;
    }

    public String text() {
        return text;
    }

    /**
     * Provides lookup of elements by non-namespaced name
     *
     * @param key the name (or shortcut key) of the node(s) of interest
     * @return the nodes which match key
     */
    public Object get(String key) {
        if (key != null && key.charAt(0) == '@') {
            List answer = new ArrayList();
            String attributeValue = attributes().get(key.substring(1));
            if (attributeValue != null) answer.add(attributeValue);
            return answer;
        }
        if ("*".equals(key)) return children;
        if (key != null && key.endsWith("_")) {
            GroovyNode node = findFirstMatch(key.substring(0, key.length()-1));
            return node == null ? "" : node;
        }
        return getByName(key);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (hashCode != other.hashCode())
            return false;
        return text.equals(other.toString());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public String toString() {
        return text;
    }

    private List getByName(String name) {
        List answer = new NodeList(1);
        for (GroovyNode child : children) {
            if (name.equals(child.getNodeName())) answer.add(child);
        }
        return answer;
    }

    private GroovyNode findFirstMatch(String name) {
        if (getNodeName().equals(name) && !text.isEmpty())
            return this;
        for (GroovyNode node : children) {
            GroovyNode match = node.findFirstMatch(name);
            if (match != null) return match;
        }
        return null;
    }

    // Used by scripts
    public List<Object> getValueNodes(String name) {
        List answer = new NodeList();
        getValueNodes(name, answer);
        return answer;
    }

    private void getValueNodes(String name, List answer) {
        if (name.equals(this.getNodeName())) {
            if (text != null && !text.isEmpty()) answer.add(this);
        }

        for (GroovyNode child : children) {
            child.getValueNodes(name, answer);
        }
    }

    protected static void setMetaClass(final MetaClass metaClass, Class nodeClass) {
        final MetaClass newMetaClass = new DelegatingMetaClass(metaClass) {
            @Override
            public Object getAttribute(final Object object, final String attribute) {
                GroovyNode n = (GroovyNode) object;
                return n.get("@" + attribute);
            }

            @Override
            public void setAttribute(final Object object, final String attribute, final Object newValue) {
                GroovyNode n = (GroovyNode) object;
                n.attributes().put(attribute, (String) newValue);
            }

            @Override
            public Object getProperty(Object object, String property) {
                if (object instanceof GroovyNode) {
                    GroovyNode n = (GroovyNode) object;
                    return n.get(property);
                }
                return super.getProperty(object, property);
            }

            @Override
            public void setProperty(Object object, String property, Object newValue) {
                if (property.startsWith("@")) {
                    String attribute = property.substring(1);
                    GroovyNode n = (GroovyNode) object;
                    n.attributes().put(attribute, (String) newValue);
                    return;
                }
                delegate.setProperty(object, property, newValue);
            }

        };
        GroovySystem.getMetaClassRegistry().setMetaClass(nodeClass, newMetaClass);
    }

    static {
        setMetaClass(GroovySystem.getMetaClassRegistry().getMetaClass(GroovyNode.class), GroovyNode.class);
    }
}
