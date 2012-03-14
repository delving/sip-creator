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

package eu.delving.sip;

import eu.delving.metadata.RecDefTree;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This is an object which contains the output of mapping/validation followed by
 * some path munging, so that it can be used as food for the indexing machine.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class IndexDocument {
    private Map<String, List<Value>> map = new TreeMap<String, List<Value>>();

    public static IndexDocument fromNode(Node inputNode, RecDefTree recordDefinition) {
        IndexDocument doc = new IndexDocument();
        NodeList childNodes = inputNode.getChildNodes();
        for (int walk=0; walk<childNodes.getLength(); walk++) {
            Node child = childNodes.item(walk);
            System.out.println(child);
            // todo: traverse the node tree building the doc
        }
//        for (Object nodeObject : traversal) {
//            Node node = (Node) nodeObject;
//            if (node.value() instanceof List) continue; // not a field
//            if (node.value() instanceof Node) {
//                throw new RuntimeException("Expected a text value");
//            }
//            FieldDefinition definition = getFieldDefinition(qname, recordDefinition);
//            String fieldType = definition == null ? null : definition.fieldType;
//            String value = node.text();
//            if (fieldType == null) fieldType = "text";
//            doc.put(String.format("%s_%s_%s", qname.getPrefix(), qname.getLocalPart(), fieldType), value);
//            if (definition != null) {
//                if (definition.summaryField != null) doc.put(definition.summaryField.tag, value);
//            }
//        }
        return doc;
    }

//    private static String mungePath(QName qname, FieldDefinition fieldDefinition) {
//        if (fieldDefinition == null || fieldDefinition.fieldType == null) {
//            return String.format("%s_%s_text", qname.getPrefix(), qname.getLocalPart());
//        }
//        else {
//            return String.format("%s_%s_%s", qname.getPrefix(), qname.getLocalPart(), fieldDefinition.fieldType);
//        }
//    }
//
//    private static RecDefNode getFieldDefinition(QName qname, RecordDefinition recordDefinition) {
//        Path path = new Path().extend(Tag.element("record")).extend(Tag.element(qname.getPrefix(), qname.getLocalPart()));
//        return recordDefinition.getFieldDefinition(path);
//    }

    private IndexDocument() {
    }

    public void put(String key, String value) {
        List<Value> list = map.get(key);
        if (list == null) {
            list = new ArrayList<Value>(4);
            map.put(key, list);
        }
        list.add(new Value(value));
    }

    public Map<String, List<Value>> getMap() {
        return map;
    }

    public class Value {
        // attributes as well sometime in the future
        private String text;

        public Value(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder("IndexDocument {\n");
        for (Map.Entry<String, List<Value>> entry : map.entrySet()) {
            if (entry.getValue().size() == 1) {
                out.append(String.format("\t%s -> %s\n", entry.getKey(), entry.getValue().get(0)));
            }
            else {
                out.append(String.format("\t%s ->\n", entry.getKey()));
                for (Value value : entry.getValue()) {
                    out.append(String.format("\t\t%s\n", value.toString()));
                }
            }
        }
        out.append("}\n");
        return out.toString();
    }
}

