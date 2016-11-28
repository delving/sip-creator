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

import eu.delving.groovy.GroovyNode

/**
 * This category is used to give DSL features to the Groovy builder
 * code that does the mapping transformation.
 *
 *
 */

public class MappingCategory {

    public static class TupleMap extends TreeMap {
        @Override
        public Object get(Object key) {
            Object value = super.get(key);
            if (value == null) value = "";
            return value;
        }
    }

    public static class TupleList extends ArrayList {
        @Override
        public String toString() {
            return 'TUPLE' + super.toString();
        }
    }

    private static List unwrap(a) {
        if (!a) return new NodeList(0);
        if (a instanceof NodeList) return a;
        if (a instanceof TupleList) return a
        if (a instanceof List && ((List) a).size() == 1) return unwrap(((List) a)[0])
        NodeList list = new NodeList();
        list.add(a)
        return list;
    }

    static boolean asBoolean(List list) {
        if (list.isEmpty()) return false;
        if (list.size() == 1 && list[0] instanceof List) return list[0].asBoolean()
        return true;
    }

    static String getAt(GroovyNode node, Object what) {
        return node.toString()[what]
    }

    static int indexOf(GroovyNode node, String string) {
        return node.text().indexOf(string)
    }

    static String substring(GroovyNode node, int from) {
        return node.text().substring(from);
    }

    static String substring(GroovyNode node, int from, int to) {
        return node.text().substring(from, to);
    }

    // concatenate lists
    static Object plus(List a, List b) { // operator +
        List both = new NodeList()
        both.addAll(unwrap(a))
        both.addAll(unwrap(b))
        return both;
    }

    // make maps out of the entries in two lists
    static Object or(List a, List b) { // operator |
        a = unwrap(a)
        b = unwrap(b)
        TupleList list = new TupleList()
        Iterator aa = a.iterator()
        Iterator bb = b.iterator()
        while (aa.hasNext() || bb.hasNext()) {
            if (aa.hasNext() && bb.hasNext()) {
                def ma = aa.next()
                GroovyNode mb = (GroovyNode)bb.next()
                if (ma instanceof Map) {
                    ma[mb.getNodeName()] = mb
                    list.add(ma);
                }
                else {
                    GroovyNode na = (GroovyNode) ma
                    GroovyNode nb = (GroovyNode) mb
                    Map map = new TupleMap()
                    map[na.getNodeName()] = na
                    map[nb.getNodeName()] = nb
                    list.add(map)
                }
            }
            else if (aa.hasNext()) {
                def ma = aa.next()
                if (ma instanceof TupleMap) {
                    list.add(ma)
                }
                else {
                    GroovyNode na = (GroovyNode) ma;
                    Map map = new TupleMap()
                    map[na.getNodeName()] = na
                    list.add(map)
                }
            }
            else { // bb only
                def mb = bb.next()
                GroovyNode nb = (GroovyNode) mb;
                Map map = new TupleMap()
                map[nb.getNodeName()] = nb
                list.add(map)
            }
        }
        return list
    }

    // keepRunning a closure on each member of the list
    static List multiply(List a, Closure closure) { // operator *
        a = unwrap(a)
        List output = new ArrayList();
        for (Object child : a) {
            Object returnValue = closure.call(child)
            if (returnValue) {
                if (returnValue instanceof Object[]) {
                    output.addAll(returnValue)
                }
                else if (returnValue instanceof List) {
                    output.addAll(returnValue)
                }
                else if (returnValue instanceof String) {
                    output.add(returnValue)
                }
                else if (!(returnValue instanceof org.w3c.dom.Node)) {
                    output.add(returnValue.toString())
                }
            }
        }
        return output
    }

    // keepRunning the closure once for the concatenated values
    static List multiply(List a, String delimiter) {
        a = unwrap(a)
        Iterator walk = a.iterator();
        StringBuilder out = new StringBuilder()
        GroovyNode node = null
        while (walk.hasNext()) {
            GroovyNode listNode = (GroovyNode)walk.next()
            if (node == null) node = new GroovyNode(null, listNode.qName(), listNode.attributes(), '*replace*');
            out.append(listNode.toString())
            if (walk.hasNext()) out.append(delimiter)
        }
        if (node == null) return []
        node.setNodeValue(out.toString())
        return [node]
    }

    // call closure for the first if there is one
    static Object power(List a, Closure closure) {  // operator **
        a = unwrap(a)
        for (Object child : a) {
            closure.call(child)
            break
        }
        return null
    }

    // call closure once with all of them
    static Object rightShift(List a, Closure closure) {  // operator >>
        a = unwrap(a)
        closure.call(a);
        return null
    }

    static String sanitize(GroovyNode node) {
        return sanitize(node.toString())
    }

    static String sanitize(List list) {
        return sanitize(list.toString())
    }

    static String sanitize(String text) { // same effect as in StringUtil.sanitizeGroovy, except apostrophe removal
        text = (text =~ /\n/).replaceAll(' ')
        text = (text =~ / +/).replaceAll(' ')
        return text
    }

    static String sanitizeURI(Object object) {
        StringBuilder out = new StringBuilder()
        for (char c : object.toString().chars) {
            switch (c) {
                case ' ':
                    out.append('%20')
                    break;
                case '[':
                    out.append('%5B')
                    break;
                case ']':
                    out.append('%5D')
                    break;
                case '\\':
                    out.append('%5C')
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString()
    }
}