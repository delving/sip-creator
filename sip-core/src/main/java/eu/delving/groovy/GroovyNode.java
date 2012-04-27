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
 * @author Gerald de Jong <gerald@delving.eu>
 */

@SuppressWarnings("unchecked")
public class GroovyNode {

    private GroovyNode parent;

    private QName qName;

    private String stringName;

    private Map<String, String> attributes;

    private Object value;

    public GroovyNode(GroovyNode parent, String namespaceUri, String localName, String prefix) {
        this(parent, new QName(namespaceUri, localName, prefix));
    }

    public GroovyNode(GroovyNode parent, String name) {
        this(parent, new QName(name), new NodeList());
    }

    public GroovyNode(GroovyNode parent, QName qName) {
        this(parent, qName, new NodeList());
    }

    public GroovyNode(GroovyNode parent, String name, String value) {
        this(parent, new QName(name), new TreeMap<String, String>(), value);
    }

    public GroovyNode(GroovyNode parent, QName qName, Object value) {
        this(parent, qName, new TreeMap<String, String>(), value);
    }

    public GroovyNode(GroovyNode parent, QName qName, Map<String, String> attributes, Object value) {
        this.parent = parent;
        this.qName = qName;
        this.attributes = attributes;
        this.value = value;
        if (parent != null) getParentList(parent).add(this);
    }

    public String text() {
        if (value instanceof String) {
            return (String) value;
        }
        else if (value instanceof Collection) {
            Collection coll = (Collection) value;
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
        if (value instanceof List) {
            return (List) value;
        }
        else {
            List l = new NodeList(4);
            l.add(value);
            return l;
        }
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public QName qName() {
        return qName;
    }

    public String name() {
        if (stringName == null) stringName = StringUtil.tagToVariable(qName.getPrefix() + qName.getLocalPart());
        return stringName;
    }

    public Object value() {
        return value;
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

    public void setValue(Object value) {
        this.value = value;
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
    public List get(String key) {
        if (key != null && key.charAt(0) == '@') {
            List answer = new ArrayList();
            String value = attributes().get(key.substring(1));
            if (value != null) answer.add(value);
            return answer;
        }
        if ("*".equals(key)) return children();
        if ("_".equals(key)) return getValueNodes();
        return getByName(key);
    }

    public boolean equals(Object other) {
        return other != null && toString().equals(other.toString());
    }

    public String toString() {
        return text();
    }

    // privates ===================================================================================

    private List<Object> getParentList(GroovyNode parent) {
        Object parentValue = parent.value();
        List<Object> parentList;
        if (parentValue instanceof List) {
            parentList = (List<Object>) parentValue;
        }
        else {
            parentList = new NodeList();
            parentList.add(parentValue);
            parent.setValue(parentList);
        }
        return parentList;
    }

    private List getByName(String name) {
        List answer = new NodeList();
        for (Object child : children()) {
            if (child instanceof GroovyNode) {
                GroovyNode childNode = (GroovyNode) child;
                if (name.equals(childNode.name())) answer.add(childNode);
            }
        }
        return answer;
    }

    private List<Object> getValueNodes() {
        List answer = new NodeList();
        getValueNodes(answer);
        return answer;
    }

    private void getValueNodes(List answer) {
        if (value instanceof List) {
            for (Object object : ((List) value)) {
                if (object instanceof GroovyNode) {
                    ((GroovyNode) object).getValueNodes(answer);
                }
                else {
                    getValueNodes((List) object);
                }
            }
        }
        else if (value instanceof String && !((String) value).trim().isEmpty()) {
            answer.add(this);
        }
        else {
            throw new RuntimeException(value.getClass().getName());
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
