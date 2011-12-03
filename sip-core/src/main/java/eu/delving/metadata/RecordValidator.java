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

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.GroovyNode;
import groovy.lang.Binding;
import groovy.lang.GString;
import groovy.lang.Script;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.QName;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * This class validates a record which emerges from the Groovy builder code, which is
 * in the form of a composite hierarchical structure of Groovy's Node instances.
 *
 * todo: This class is supposed to run some Groovy validation code, but the switch to
 * todo: hierarchical has suspended that for now.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecordValidator {
    private Uniqueness idUniqueness;
    private Script script;
    private RecMapping recMapping;

    public interface ValidationReference {
        boolean allowOption(Node node);

        boolean isUnique(String string);
    }

    public RecordValidator(GroovyCodeResource groovyCodeResource, RecMapping recMapping) {
        this.recMapping = recMapping;
        this.script = groovyCodeResource.createValidationScript(""); // todo: validation code?
    }

    public void guardUniqueness(Uniqueness uniqueness) {
        this.idUniqueness = uniqueness;
    }

    public void validateRecord(Node record, int recordNumber) throws ValidationException {
        sanitizeRecord(record);
        Binding binding = new Binding();
        binding.setVariable("record", record);
        binding.setVariable("validationReference", new ValidationReference() {
            @Override
            public boolean allowOption(Node node) {
                Path path = nodeToPath(node);
                RecDefNode recDefNode = recMapping.getRecDefTree().getRecDefNode(path);
                if (recDefNode == null) throw new RuntimeException();
                return recDefNode.allowOption(node.value().toString());
            }

            @Override
            public boolean isUnique(String string) {
                return idUniqueness == null || !idUniqueness.isRepeated(string);
            }
        });
        script.setBinding(binding);
        try {
            script.run();
        }
        catch (AssertionError e) {
            throw new ValidationException(e, record, recordNumber);
        }
    }

    private Path nodeToPath(Node node) {
        Path path = new Path();
        nodeToPath(node, path);
        return path;
    }

    private void nodeToPath(Node node, Path path) {
        if (node.parent() != null) {
            nodeToPath(node.parent(), path);
        }
        if (node.name() instanceof String) {
            path.push(Tag.element((String) node.name()));
        }
        else if (node.name() instanceof QName) {
            QName q = (QName) node.name();
            path.push(Tag.element(q.getPrefix(), q.getLocalPart()));
        }
        else {
            throw new IllegalStateException("Node name type is not recognized: " + node.name().getClass());
        }
    }

    private void sanitizeRecord(Node record) {
        sanitizeNode(record);
    }

    @SuppressWarnings("unchecked")
    private void sanitizeNode(Node node) {
        if (node.value() instanceof List) {
            for (Object nodeObject : new NodeList(((List) node.value()))) { // cloning to avoid concurrent modification
                sanitizeNode((Node) nodeObject);
            }
            List list = (List) node.value(); // after cleaning
            Collections.sort(list, NODE_COMPARATOR);
            Node current = null;
            Iterator walk = list.iterator();
            while (walk.hasNext()) {
                Node next = (Node) walk.next();
                if (current != null && current.name().equals(next.name()) && current.value().equals(next.value())) {
                    walk.remove();
                }
                else {
                    current = next;
                }
            }
        }
        else {
            String valueString = valueToString(node.value());
            if (valueString.isEmpty()) {
                node.parent().remove(node);
            }
            else {
                node.setValue(valueString);
            }
        }
    }

    private static class Counter {
        int count;
    }

    private static Comparator<Object> NODE_COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object a, Object b) {
            Node nodeA = (Node) a;
            Node nodeB = (Node) b;
            QName nameA = (QName) nodeA.name();
            QName nameB = (QName) nodeB.name();
            int comp = nameA.toString().compareTo(nameB.toString());
            if (comp != 0) return comp;
            String valueA = valueToString(nodeA.value());
            String valueB = valueToString(nodeB.value());
            if (valueA == null || valueB == null) return 0;
            return ((String)nodeA.value()).compareTo((String)nodeB.value());
        }
    };

    private static String valueToString(Object object) {
        if (object instanceof String) {
            return (String) object;
        }
        else if (object instanceof GString) {
            return object.toString();
        }
        else if (object instanceof GroovyNode) {
            return valueToString(((GroovyNode) object).value());
        }
        else if (object instanceof Node) {
            return valueToString(((Node) object).value());
        }
        else {
            return null;
        }
    }
}