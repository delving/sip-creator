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

import eu.delving.groovy.DiscardRecordException
import eu.delving.groovy.GroovyNode

/**
 * This category is used to give DSL features to the Groovy builder
 * code that does the mapping transformation.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCategory {

    private static NodeList unwrap(a) {
        if (!a) return new NodeList(0);
        if (a instanceof NodeList) return a;
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

    static void discard(Boolean condition, String why) {
        if (condition) throw new DiscardRecordException(why)
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

    static List ifAbsentUse(List list, Object factVariable) {
        if (!list) {
            list += factVariable
        }
        else if (list.size() == 1) {
            GroovyNode node = (GroovyNode) list[0]
            if (!node.text()) {
                list += factVariable
            }
        }
        return list
    }

    // concatenate lists
    static Object plus(List a, List b) { // operator +
        List both = new NodeList()
        both.addAll(unwrap(a))
        both.addAll(unwrap(b))
        return both;
    }

    // make tuples out of the entries in two lists
    static Object or(List a, List b) { // operator |
        a = unwrap(a)
        b = unwrap(b)
        List tupleList = new NodeList()
        int max = Math.min(a.size(), b.size());
        for (Integer index: 0..(max - 1)) {
            if (a[index] instanceof List) {
                tupleList.add(a[index] += b[index]);
            }
            else {
                tupleList.add([a[index], b[index]])
            }
        }
        return tupleList
    }

    // run a closure on each member of the list
    static List multiply(List a, Closure closure) { // operator *
        a = unwrap(a)
        List output = new ArrayList();
        for (Object child: a) {
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

    // run the closure once for the concatenated values
    static List concat(List a, String delimiter) {
        a = unwrap(a)
        Iterator walk = a.iterator();
        StringBuilder out = new StringBuilder()
        while (walk.hasNext()) {
            out.append(walk.next())
            if (walk.hasNext()) out.append(delimiter)
        }
        return [out.toString()]
    }

    // call closure for the first if there is one
    static Object power(List a, Closure closure) {  // operator **
        a = unwrap(a)
        for (Object child: a) {
            closure.call(child)
            break
        }
        return null
    }

    static List toId(a, spec) {
        a = unwrap(a)
        String identifier = a[0].toString()
        if (!spec) throw new MissingPropertyException("spec", String.class)
        if (!identifier) throw new MissingPropertyException("Identifier passed to toId", String.class)
        def uriBytes = identifier.toString().getBytes("UTF-8");
        def digest = java.security.MessageDigest.getInstance("SHA-1")
        def hash = new StringBuilder()
        for (Byte b in digest.digest(uriBytes)) {
            hash.append('0123456789ABCDEF'[(b & 0xF0) >> 4])
            hash.append('0123456789ABCDEF'[b & 0x0F])
        }
        return ["$spec/$hash"]
    }

    static List toLocalId(a, spec) {
        a = unwrap(a)
        String identifier = a[0].toString()
        if (!spec) throw new MissingPropertyException("spec", String.class)
        if (!identifier) throw new MissingPropertyException("Identifier passed to toId", String.class)
        return ["$spec/$identifier"]
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
}