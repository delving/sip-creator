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

import eu.delving.metadata.RecDef;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.MissingMethodException;
import groovy.util.BuilderSupport;
import groovy.xml.FactorySupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.*;

/**
 * Custom node builder which executes closures if they are found as attribute values, or if
 * an element closure returns a String or GString.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DOMBuilder extends BuilderSupport {
    private Document document;
    private DocumentBuilder documentBuilder;
    private Map<String, String> namespaces = new TreeMap<String, String>();

    public static DOMBuilder newInstance(List<RecDef.Namespace> namespaces) {
        try {
            DOMBuilder instance = new DOMBuilder();
            instance.namespaces.put("xml", "http://www.w3.org/XML/1998/namespace");
            for (RecDef.Namespace ns : namespaces) {
                instance.namespaces.put(ns.prefix, ns.uri);
            }
            DocumentBuilderFactory factory = FactorySupport.createDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            instance.documentBuilder = factory.newDocumentBuilder();
            return instance;
        }
        catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void setParent(Object parent, Object child) {
        Node current = (Node) parent;
        Node node = (Node) child;
        current.appendChild(node);
    }

    @Override
    protected Object createNode(Object name) {
        if (document == null) document = documentBuilder.newDocument();
        String uri = getNamespace(name.toString());
        return uri != null ? document.createElementNS(uri, name.toString()) : document.createElement(name.toString());
    }

    @Override
    protected Object createNode(Object name, Object value) {
        Element element = (Element) createNode(name);
        element.appendChild(toNode(value.toString()));
        return element;
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        Element element = (Element) createNode(name);
        for (Object entryObject : attributes.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            String attrName = entry.getKey().toString();
            if ("xmlns".equals(attrName)) throw new RuntimeException("Can't handle xmlns attribute");
            if (attrName.startsWith("xmlns:")) throw new RuntimeException("Can't handle attribute xmlns:*");
            if (entry.getValue() == null) throw new RuntimeException("Can't handle null attribute value");
            String valueString;
            if (entry.getValue() instanceof Closure) {
                ClosureResult result = runClosure(element, (Closure) entry.getValue());
                if (result.string != null) {
                    valueString = result.string;
                }
                else if (result.list != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Object member : result.list) sb.append(member.toString());
                    valueString = sb.toString();
                }
                else {
                    throw new RuntimeException();
                }
            }
            else {
                valueString = entry.getValue().toString();
            }
            String uri = getNamespace(entry.getKey().toString());
            if (uri != null) {
                element.setAttributeNS(uri, attrName, valueString);
            }
            else {
                element.setAttribute(attrName, valueString);
            }
        }
        return element;
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        Element element = (Element) createNode(name, attributes);
        element.appendChild(toNode(value.toString()));
        return element;
    }

    @Override
    protected Object doInvokeMethod(String methodName, Object name, Object args) {
        Object node;
        List list = InvokerHelper.asList(args);
        Closure closure;
        switch (list.size()) {
            case 1: { ///
                Object object = list.get(0);
                if (object instanceof Map) {
                    node = createNode(name, runMapClosures((Map) object));
                }
                else if (object instanceof Closure) {
                    node = createNode(name);
                    closure = (Closure) object;
                    setValuesFromClosure((Node) node, closure);
                }
                else {
                    node = createNode(name, object);
                    setParent(getCurrent(), node);
                }
            }
            break;
            case 2: {
                Object object1 = list.get(0);
                Object object2 = list.get(1);
                if (object1 instanceof Map) {
                    if (object2 instanceof Closure) {
                        node = createNode(name, runMapClosures((Map) object1));
                        closure = (Closure) object2;
                        setValuesFromClosure((Node) node, closure);
                    }
                    else {
                        node = createNode(name, (Map) object1, object2);
                        setParent(getCurrent(), node);
                    }
                }
                else {
                    if (object2 instanceof Closure) {
                        node = createNode(name, object1);
                        closure = (Closure) object2;
                        setValuesFromClosure((Node) node, closure);
                    }
                    else if (object2 instanceof Map) {
                        node = createNode(name, (Map) object2, object1);
                        setParent(getCurrent(), node);
                    }
                    else {
                        throw new MissingMethodException(name.toString(), getClass(), list.toArray(), false);
                    }
                }
            }
            break;
            default: {
                throw new MissingMethodException(name.toString(), getClass(), list.toArray(), false);
            }
        }
        return node;
    }

    private void setValuesFromClosure(Node node, Closure closure) {
        ClosureResult result = runClosure(node, closure);
        if (result.string != null) {
            node.appendChild(toNode(result.string));
        }
        if (result.list != null && !result.list.isEmpty()) {
//            if (isPunctuation(result.list.get(0))) {
//                StringBuilder value = new StringBuilder();
//                for (int walk = 1; walk < result.list.size(); walk++) {
//                    value.append(result.list.get(walk));
//                    if (walk<result.list.size() - 1) value.append(result.list.get(0));
//                }
//                node.appendChild(toNode(value.toString()));
//            }
//            else {
                node.appendChild(toNode(result.list.get(0)));
                for (int walk = 1; walk < result.list.size(); walk++) {
                    Map<String, String> attributes = new TreeMap<String,String>();
                    NamedNodeMap nodeAttributes = node.getAttributes();
                    for (int a=0; a < nodeAttributes.getLength(); a++) {
                        Attr attr = (Attr) nodeAttributes.item(a);
                        attributes.put(attr.getName(), attr.getValue());
                    }
                    Node child = (Node) createNode(node.getNodeName(), attributes, result.list.get(walk));
                    Node parent = node.getParentNode();
                    if (parent == null) throw new RuntimeException("Node has no parent: " + node);
                    parent.appendChild(child);
                }
//            }
        }
    }

    private boolean isPunctuation(String s) {
        return s.length() == 1 && ",; ".indexOf(s.charAt(0)) >= 0;
    }

    private Map runMapClosures(Map map) {
        Map<String, String> out = new TreeMap<String, String>();
        for (Object entryObject : map.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            if (entry.getValue() instanceof Closure) {
                ClosureResult result = runClosure(null, (Closure) entry.getValue());
                if (result.string != null) out.put(entry.getKey().toString(), result.string);
                if (result.list != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Object member : result.list) sb.append(member.toString());
                    out.put(entry.getKey().toString(), sb.toString());
                }
            }
            else {
                out.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return out;
    }

    private ClosureResult runClosure(Node node, Closure closure) {
        ClosureResult cr = new ClosureResult();
        Object result;
        if (node != null) {
            Object oldCurrent = getCurrent();
            setCurrent(node);
            if (oldCurrent != null) setParent(oldCurrent, node);
            setClosureDelegate(closure, node);
            result = closure.call();
            setCurrent(oldCurrent);
        }
        else {
            result = closure.call();
        }
        if (result instanceof String) {
            cr.string = (String) result;
        }
        else if (result instanceof GString) {
            cr.string = result.toString();
        }
        else if (result instanceof List) {
            cr.list = unpack((List) result);
        }
        else if (result instanceof Object[]) {
            cr.list = unpack(Arrays.asList((Object[]) result));
        }
        if (cr.string != null && cr.string.trim().isEmpty()) cr.string = null;
        if (cr.list != null) {
            Iterator<String> walk = cr.list.iterator();
            while (walk.hasNext()) {
                String member = walk.next();
                if (member.trim().isEmpty()) walk.remove();
            }
            if (cr.list.isEmpty()) cr.list = null;
        }
        return cr;
    }

    private List<String> unpack(List list) {
        List<String> result = new ArrayList<String>();
        unpack(list, result);
        return result.isEmpty() ? null : result;
    }

    private void unpack(List from, List<String> to) {
        for (Object member : from) {
            if (member instanceof List) {
                unpack((List) member, to);
            }
            else if (member instanceof Object[]) {
                unpack(Arrays.asList((Object[]) member), to);
            }
            else if (member instanceof String) {
                to.add((String) member);
            }
            else if (!(member instanceof Node)) {
                to.add(member.toString());
            }
            else {
                throw new RuntimeException("unpack: " + member.getClass());
            }
        }
    }

    private static class ClosureResult {
        public String string;
        public List<String> list;

        public String toString() {
            if (string != null) {
                return String.format("String(%s)", string);
            }
            else if (list != null) {
                return String.format("List(%s)", list);
            }
            else {
                return "Empty";
            }
        }
    }

    private String getNamespace(String name) {
        int colon = name.indexOf(':');
        if (colon > 0) {
            String prefix = name.substring(0, colon);
            String uri = namespaces.get(prefix);
            if (uri == null) throw new RuntimeException("Namespace not found for prefix: " + prefix);
            return uri;
        }
        return colon > 0 ? namespaces.get(name.substring(0, colon)) : null;
    }

    private Node toNode(String text) {
        return document.createTextNode(text);
    }
}
