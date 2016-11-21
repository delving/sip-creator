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

package eu.delving.groovy;

import eu.delving.metadata.StringUtil;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.util.NodeList;
import groovy.xml.QName;

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

    private Object nodeValue;

    public GroovyNode(GroovyNode parent, String namespaceUri, String localName, String prefix) {
        this(parent, new QName(namespaceUri, localName, prefix == null ? "" : prefix));
    }

    public GroovyNode(GroovyNode parent, String name) {
        this(parent, new QName(name), new NodeList());
    }

    public GroovyNode(GroovyNode parent, QName qName) {
        this(parent, qName, new NodeList());
    }

    public GroovyNode(GroovyNode parent, String name, String nodeValue) {
        this(parent, new QName(name), new TreeMap<>(), nodeValue);
    }

    public GroovyNode(GroovyNode parent, QName qName, Object nodeValue) {
        this(parent, qName, new TreeMap<>(), nodeValue);
    }

    public GroovyNode(GroovyNode parent, QName qName, Map<String, String> attributes, Object nodeValue) {
        this.parent = parent;
        this.qName = qName;
        this.attributes = attributes;
        this.nodeValue = nodeValue;
        if (parent != null) getParentList(parent).add(this);
    }

    public String text() {
        if (nodeValue instanceof String) {
            return (String) nodeValue;
        }
        else if (nodeValue instanceof Collection) {
            Collection coll = (Collection) nodeValue;
            String previousText = null;
            StringBuffer buffer = null;
            for (Object child : coll) {
                if (child instanceof String) {
                    String childText = (String) child;
                    if (previousText == null) {
                        previousText = childText;
                    }
                    else {
                        if (buffer == null) {
                            buffer = new StringBuffer();
                            buffer.append(previousText);
                        }
                        buffer.append(childText);
                    }
                }
            }
            if (buffer != null) {
                return buffer.toString();
            }
            else if (previousText != null) {
                return previousText;
            }
        }
        return "";
    }

    public List children() {
        if (nodeValue instanceof List) {
            return (List) nodeValue;
        }
        else {
            List l = new NodeList(4);
            l.add(nodeValue);
            return l;
        }
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
        return text().length();
    }

    public boolean contains(String s) {
        return text().contains(s);
    }

    public String[] split(String s) {
        return text().split(s);
    }

    public boolean endsWith(String s) {
        return text().endsWith(s);
    }

    public String replaceAll(String from, String to) {
        return text().replaceAll(from, to);
    }

    public Object getNodeValue() {
        return nodeValue;
    }

    public void setNodeValue(Object nodeValue) {
        this.nodeValue = nodeValue;
    }

    public GroovyNode parent() {
        return parent;
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
        if ("*".equals(key)) return children();
        if (key != null && key.endsWith("_")) {
            List<Object> valueNodes = getValueNodes(key.substring(0, key.length()-1));
            return valueNodes.isEmpty() ? "" : valueNodes.get(0);
        }
        return getByName(key);
    }

    @Override
    public boolean equals(Object other) {
        return other != null && toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return text().hashCode();
    }

    public String toString() {
        return text();
    }

    // privates ===================================================================================

    private List<Object> getParentList(GroovyNode parent) {
        Object parentValue = parent.getNodeValue();
        List<Object> parentList;
        if (parentValue instanceof List) {
            parentList = (List<Object>) parentValue;
        }
        else {
            parentList = new NodeList();
            parentList.add(parentValue);
            parent.setNodeValue(parentList);
        }
        return parentList;
    }

    private List getByName(String name) {
        List answer = new NodeList();
        for (Object child : children()) {
            if (child instanceof GroovyNode) {
                GroovyNode childNode = (GroovyNode) child;
                if (name.equals(childNode.getNodeName())) answer.add(childNode);
            }
        }
        return answer;
    }

    private List<Object> getValueNodes(String name) {
        List answer = new NodeList();
        getValueNodes(name, answer);
        return answer;
    }

    private void getValueNodes(String name, List answer) {
        if (nodeValue instanceof List) {
            for (Object object : ((List) nodeValue)) {
                if (object instanceof GroovyNode) {
                    ((GroovyNode) object).getValueNodes(name, answer);
                }
                else {
                    getValueNodes(name, (List) object);
                }
            }
        }
        else if (name.equals(this.getNodeName())) {
            if (nodeValue instanceof String && !((String) nodeValue).trim().isEmpty()) answer.add(this);
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
