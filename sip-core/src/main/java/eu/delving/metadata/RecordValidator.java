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
import groovy.lang.Binding;
import groovy.lang.Script;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.QName;
import org.apache.log4j.Logger;

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
    private Uniqueness idUniqueness; // todo: rebuild uniqueness test
    private Script script;

    public RecordValidator(GroovyCodeResource groovyCodeResource, RecordDefinition recordDefinition) {
        String wrappedValidationCode = String.format("use (ValidationCategory) {\n%s\n}", recordDefinition.validation);
        this.script = groovyCodeResource.createShell().parse(wrappedValidationCode);
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
        Path path = new Path();
        Map<Path, Counter> counters = new TreeMap<Path, Counter>();
        sanitizeNode(record, path, counters);
    }

    private void sanitizeNode(Node node, Path path, Map<Path, Counter> counters) {
        if (node.name() instanceof String) {
            path.push(Tag.create((String) node.name()));
        }
        else {
            QName name = (QName) node.name();
            path.push(Tag.create(name.getPrefix(), name.getLocalPart()));
        }
        if (node.value() instanceof List) {
            for (Object nodeObject : new NodeList(((List) node.value()))) { // cloning to avoid concurrent modification
                sanitizeNode((Node) nodeObject, path, counters);
            }
        }
        else {
            String value = node.toString();
            if (value.isEmpty()) {
                node.parent().remove(node);
            }
            else {
                node.setValue(value);
            }
        }
        path.pop();
    }

    private static class Counter {
        int count;
    }

    /*
    private void validateCardinalities(Map<Path, Counter> counters, List<String> problems) {
        Map<String, Boolean> requiredGroupMap = new TreeMap<String, Boolean>();
        for (FieldDefinition fieldDefinition : validatableFields) {
            if (fieldDefinition.validation.requiredGroup != null) {
                requiredGroupMap.put(fieldDefinition.validation.requiredGroup, false);
            }
            Counter counter = counters.get(fieldDefinition.path);
            if (!fieldDefinition.validation.multivalued && counter != null && counter.count > 1) {
                problems.add(String.format("Single-valued field [%s] has more than one value", fieldDefinition.path));
            }
        }
        for (Map.Entry<Path, Counter> entry : counters.entrySet()) {
            FieldDefinition fieldDefinition = recordDefinition.getFieldDefinition(entry.getKey());
            if (fieldDefinition.validation != null) {
                if (fieldDefinition.validation.requiredGroup != null) {
                    requiredGroupMap.put(fieldDefinition.validation.requiredGroup, true);
                }
            }
        }
        for (Map.Entry<String, Boolean> entry : requiredGroupMap.entrySet()) {
            if (!entry.getValue()) {
                problems.add(String.format("Required field violation for [%s]", entry.getKey()));
            }
        }
    }

    private void validateDocument(Document document, List<String> problems, Set<String> entries, Map<Path, Counter> counters) {
        Element rootElement = document.getRootElement();
        if (!rootElement.getQName().getName().equals("validate")) {
            throw new RuntimeException("Root element should be 'validate'");
        }
        Element recordElement = rootElement.element("record");
        validateElement(recordElement, new Path(), problems, entries, counters);
    }

    private boolean validateElement(Element element, Path path, List<String> problems, Set<String> entries, Map<Path, Counter> counters) {
        path.push(Tag.create(element.getNamespacePrefix(), element.getName()));
        boolean hasElements = false;
        Iterator walk = element.elementIterator();
        while (walk.hasNext()) {
            Element subelement = (Element) walk.next();
            boolean remove = validateElement(subelement, path, problems, entries, counters);
            if (remove) {
                walk.remove();
            }
            hasElements = true;
        }
        if (!hasElements) {
            boolean fieldRemove = validatePath(element.getTextTrim(), path, problems, entries, counters);
            path.pop();
            return fieldRemove;
        }
        path.pop();
        return false;
    }

    private boolean validatePath(String text, Path path, List<String> problems, Set<String> entries, Map<Path, Counter> counters) {
        FieldDefinition field = recordDefinition.getFieldDefinition(path);
        if (field == null) {
            problems.add(String.format("No field definition found for path [%s]", path));
            return true;
        }
        String entryString = field + "=" + text;
        if (text.isEmpty() || entries.contains(entryString)) {
            return true;
        }
        else {
            entries.add(entryString);
            Counter counter = counters.get(field.path);
            if (counter == null) {
                counters.put(field.path, counter = new Counter());
            }
            counter.count++;
            validateField(text, field, problems);
            return false;
        }
    }

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