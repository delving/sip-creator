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

/**
 * Parse, filter, validate a record
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordValidator {
    private Logger log = Logger.getLogger(getClass());
    private Uniqueness idUniqueness; // todo: rebuild uniqueness test
    private Script script;

    public RecordValidator(GroovyCodeResource groovyCodeResource, RecordDefinition recordDefinition) {
        this.script = groovyCodeResource.createValidationScript(recordDefinition.validation);
    }

    public void guardUniqueness(Uniqueness uniqueness) {
        this.idUniqueness = uniqueness;
    }

    public void validateRecord(Node record, int recordNumber) throws ValidationException {
        sanitizeRecord(record);
        Binding binding = new Binding();
        binding.setVariable("record", record);
        script.setBinding(binding);
        try {
            script.run();
        }
        catch (AssertionError e) {
            throw new ValidationException(e, record, recordNumber);
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

    /*
    todo: think of something for options, which are now in FieldDefinition
    todo: uniqueness
    private void validateField(String text, FieldDefinition fieldDefinition, List<String> problems) {
        FieldDefinition.Validation validation = fieldDefinition.validation;
        if (validation != null) {
            if (validation.hasOptions()) {
                if (!validation.allowOption(text)) {
                    problems.add(String.format("Value for [%s] was [%s] which does not belong to [%s]", fieldDefinition.path, text, validation.getOptionsString()));
                }
            }
            if (validation.url) {
                try {
                    new URL(text);
                }
                catch (MalformedURLException e) {
                    problems.add(String.format("URL value for [%s] was [%s] which is malformed", fieldDefinition.path, text));
                }
            }
            if (validation.id && idUniqueness != null) {
                if (idUniqueness.isRepeated(text)) {
                    problems.add(String.format("Identifier [%s] must be unique but the value [%s] appears more than once", fieldDefinition.path, text));
                }
            }
        }
    }
    */


}