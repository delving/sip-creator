/*
 * Copyright 2010 DELVING BV
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
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parse, filter, validate a record
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordValidator {
    private Logger log = Logger.getLogger(getClass());
    private Uniqueness idUniqueness;
    private Script script;
    private RecordDefinition recordDefinition;
    private Map<Path, FieldDefinition> fieldDefinitionCache = new TreeMap<Path, FieldDefinition>();

    public RecordValidator(GroovyCodeResource groovyCodeResource, RecordDefinition recordDefinition) {
        this.recordDefinition = recordDefinition;
        this.script = groovyCodeResource.createValidationScript(recordDefinition.validation);
    }

    public void guardUniqueness(Uniqueness uniqueness) {
        this.idUniqueness = uniqueness;
    }

    public interface ValidationReference {
        boolean allowOption(Node node);
        boolean isUnique(String string);
    }

    public void validateRecord(Node record, int recordNumber) throws ValidationException {
        sanitizeRecord(record);
        Binding binding = new Binding();
        binding.setVariable("record", record);
        binding.setVariable("validationReference", new ValidationReference() {
            @Override
            public boolean allowOption(Node node) {
                Path path = nodeToPath(node);
                FieldDefinition definition = fieldDefinitionCache.get(path);
                if (definition == null) {
                    fieldDefinitionCache.put(path, definition = recordDefinition.getFieldDefinition(path));
                }
                return definition.allowOption(node.value().toString());
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
            path.push(Tag.create((String)node.name()));
        }
        else if (node.name() instanceof QName) {
            QName q = (QName) node.name();
            path.push(Tag.create(q.getPrefix(), q.getLocalPart()));
        }
        else {
            throw new IllegalStateException("Node name type is not recognized: "+node.name().getClass());
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
                Node next = (Node)walk.next();
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
            if (comp != 0) {
                return comp;
            }
            return valueToString(nodeA.value()).compareTo(valueToString(nodeB.value()));
        }
    };

    private static String valueToString(Object object) {
        if (object instanceof String) {
            return (String)object;
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
            throw new IllegalStateException("Could not deal with class "+object.getClass());
        }
    }
}