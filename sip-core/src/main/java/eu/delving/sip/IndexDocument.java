package eu.delving.sip;

import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.Tag;
import groovy.util.Node;
import groovy.xml.QName;

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

    public static IndexDocument fromNode(Node inputNode, RecordDefinition recordDefinition) {
        IndexDocument doc = new IndexDocument();
        List traversal = inputNode.depthFirst();
        for (Object nodeObject : traversal) {
            Node node = (Node) nodeObject;
            if (node.value() instanceof List) continue; // not a field
            if (node.value() instanceof Node) {
                throw new RuntimeException("Expected a text value");
            }
            doc.put(mungePath(node, recordDefinition), node.text());
        }
        return doc;
    }

    private static String mungePath(Node node, RecordDefinition recordDefinition) {
        QName qname = (QName) node.name();
        Path path = new Path().extend(Tag.element("record")).extend(Tag.element(qname.getPrefix(), qname.getLocalPart()));
        FieldDefinition fieldDefinition = recordDefinition.getFieldDefinition(path);
        if (fieldDefinition == null || fieldDefinition.fieldType == null) {
            return String.format("%s_%s_text", qname.getPrefix(), qname.getLocalPart());
        }
        else {
            return String.format("%s_%s_%s", qname.getPrefix(), qname.getLocalPart(), fieldDefinition.fieldType);
        }
    }

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

