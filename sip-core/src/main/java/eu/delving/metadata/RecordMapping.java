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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 * A groovy mapping based on a model.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("record-mapping")
public class RecordMapping {

    @XStreamAsAttribute
    String prefix;

    @Deprecated
    @XStreamAlias("records-normalized")
    int recordsNormalized;

    @Deprecated
    @XStreamAlias("records-discarded")
    int recordsDiscarded;

    @Deprecated
    @XStreamAlias("normalize-time")
    long normalizeTime;

    @XStreamAlias("facts")
    Map<String, String> facts = new HashMap<String, String>();

    @XStreamAlias("field-mappings")
    Map<String, FieldMapping> fieldMappings = new HashMap<String, FieldMapping>();

    @XStreamOmitField
    private RecordDefinition recordDefinition;

    public RecordMapping(String prefix, RecordDefinition recordDefinition) {
        this.prefix = prefix;
        this.recordDefinition = recordDefinition;
    }

    public void setRecordDefinition(RecordDefinition recordDefinition) {
        this.recordDefinition = recordDefinition;
        Set<String> notFound = new TreeSet<String>();
        for (Map.Entry<String, FieldMapping> entry : fieldMappings.entrySet()) {
            Path path = new Path(entry.getKey());
            FieldDefinition fieldDefinition = recordDefinition.getFieldDefinition(path);
            if (fieldDefinition == null) {
                notFound.add(entry.getKey());
            }
            entry.getValue().fieldDefinition = fieldDefinition;
        }
        for (String key : notFound) {
            fieldMappings.remove(key);
        }
    }

    public RecordDefinition getRecordDefinition() {
        return recordDefinition;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getFact(String fieldName) {
        String value = facts.get(fieldName);
        if (value == null) {
            facts.put(fieldName, value = "");
        }
        return value;
    }

    public boolean setFact(String fieldName, String value) {
        String existing = facts.get(fieldName);
        if (existing == null || !value.equals(existing)) {
            facts.put(fieldName, value);
            return true;
        }
        else {
            return false;
        }
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public Set<FieldMapping> getFieldMappings() {
        return new TreeSet<FieldMapping>(fieldMappings.values());
    }

    public void applyTemplate(RecordMapping template) {
        if (!fieldMappings.isEmpty()) {
            throw new RuntimeException("Field mappings must be empty to apply template");
        }
        for (FieldMapping fieldMapping : template.getFieldMappings()) {
            fieldMappings.put(fieldMapping.getDefinition().path.toString(), fieldMapping);
        }
    }

    public FieldMapping getFieldMapping(String path) {
        return fieldMappings.get(path);
    }

    public String toDisplayCode() {
        return toCode(null, false, null);
    }

    public String toCompileCode() {
        return toCode(null, true, null);
    }

    public String toDisplayCode(Path selectedPath) {
        return toCode(selectedPath, false, null);
    }

    public String toCompileCode(Path selectedPath) {
        return toCode(selectedPath, true, null);
    }

    public String toCompileCode(Path selectedPath, String editedCode) {
        return toCode(selectedPath, true, editedCode);
    }

    public String toString() {
        return stream().toXML(this);
    }

    // === private

    private String toCode(Path selectedPath, boolean forCompile, String editedCode) {
        final StringBuilder stringBuilder = new StringBuilder();
        Out out = new Out() {
            int indentLevel;

            @Override
            public void line(String line) {
                for (int walk = 0; walk < indentLevel; walk++) {
                    stringBuilder.append('\t');
                }
                stringBuilder.append(line).append('\n');
            }

            @Override
            public void indent(int change) {
                indentLevel += change;
            }
        };
        if (forCompile) {
            out.line("// Groovy Mapping Code - Generated by SIP-Creator\n");
            out.line("// Facts\n");
            for (Map.Entry<String, String> factEntry : facts.entrySet()) {
                out.line(String.format(
                        "def %s = '''%s'''\n",
                        factEntry.getKey(),
                        Sanitizer.sanitizeGroovy(factEntry.getValue())
                ));
            }
            out.line("\n// Dictionaries\n");
            for (Map.Entry<String, FieldMapping> fieldMappingEntry : fieldMappings.entrySet()) {
                if (fieldMappingEntry.getValue().dictionary != null) {
                    String name = fieldMappingEntry.getValue().getFieldNameString();
                    out.line(String.format(
                            "def %s_Dictionary = [\n",
                            name
                    ));
                    out.indent(1);
                    Iterator<Map.Entry<String, String>> walk = fieldMappingEntry.getValue().dictionary.entrySet().iterator();
                    while (walk.hasNext()) {
                        Map.Entry<String, String> entry = walk.next();
                        out.line(String.format(
                                "'''%s''':'''%s'''%s",
                                Sanitizer.sanitizeGroovy(entry.getKey()),
                                Sanitizer.sanitizeGroovy(entry.getValue()),
                                walk.hasNext() ? "," : ""
                        ));
                    }
                    out.indent(-1);
                    out.line("]");
                    out.line("// lookup closure:");
                    out.line(String.format(
                            "def %s_lookup = { " +
                                    "value -> " +
                                    "if (value) { " +
                                    "    def v = %s_Dictionary[value.sanitize()]; " +
                                    "    if (v) { " +
                                    "       if (v.endsWith(':')) { " +
                                    "           \"${v} ${value}\" " +
                                    "       }" +
                                    "       else {" +
                                    "           v " +
                                    "       }" +
                                    "    }" +
                                    "    else {" +
                                    "       '' " +
                                    "    } " +
                                    "} " +
                                    "else { " +
                                    "    '' " +
                                    "} }\n",
                            name, name
                    ));
                }
            }
            out.line("// Builder to create the record\n");
        }
        if (editedCode == null) {
            if (forCompile) {
                out.line("use (MappingCategory) {");
                out.indent(1);
                out.line("output.");
                out.indent(1);
            }
            Set<String> usedPaths = new TreeSet<String>();
            toCode(new Path(), recordDefinition.root, out, usedPaths, selectedPath, forCompile);
            if (forCompile) {
                out.indent(-1);
                out.line("}");
                out.indent(-1);
            }
            if (selectedPath == null && usedPaths.size() != fieldMappings.size()) {
                Set<String> unusedPaths = new TreeSet<String>(fieldMappings.keySet());
                unusedPaths.removeAll(usedPaths);
                Logger.getLogger(getClass()).warn("unused paths: " + unusedPaths);
            }
        }
        else {
            if (forCompile) {
                out.line("use (MappingCategory) {");
                out.indent(1);
                out.line("output.");
                out.line(String.format("%s {", recordDefinition.root.getTag()));
            }
            for (String line : editedCode.split("\n")) {
                out.line(line);
            }
            if (forCompile) {
                out.line("}");
                out.indent(-1);
                out.line("}");
            }
        }
        return stringBuilder.toString();
    }

    private void toCode(Path path, ElementDefinition element, Out out, Set<String> usedPaths, Path selectedPath, boolean forCompile) {
        if (selectedPath != null && !selectedPath.toString().startsWith(path.toString())) {
            return;
        }
        if (forCompile) {
            out.line(String.format("%s {", element.getTag()));
            out.indent(1);
        }
        if (element.elements != null) {
            for (ElementDefinition subNode : element.elements) {
                toCode(path.extend(element.getTag()), subNode, out, usedPaths, selectedPath, forCompile);
            }
        }
        if (element.fields != null) {
            for (FieldDefinition fieldDefinition : element.fields) {
                toCode(path.extend(element.getTag()), fieldDefinition, out, usedPaths, selectedPath);
            }
        }
        if (forCompile) {
            out.indent(-1);
            out.line("}");
        }
    }

    private void toCode(Path path, FieldDefinition field, Out out, Set<String> usedPaths, Path selectedPath) {
        Path fieldPath = path.extend(field.getTag());
        if (selectedPath != null && !selectedPath.equals(fieldPath)) {
            return;
        }
        FieldMapping fieldMapping = fieldMappings.get(fieldPath.toString());
        if (fieldMapping != null) {
            usedPaths.add(fieldPath.toString());
            if (fieldMapping.code != null) {
                for (String line : fieldMapping.code) {
                    if (codeIndent(line) < 0) {
                        out.indent(-1);
                    }
                    out.line(line);
                    if (codeIndent(line) > 0) {
                        out.indent(1);
                    }
                }
            }
        }
    }

    private static int codeIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            switch (c) {
                case '}':
                    indent--;
                    break;
                case '{':
                    indent++;
                    break;
            }
        }
        return indent;
    }

    interface Out {
        void line(String line);

        void indent(int change);
    }

    public static void write(RecordMapping mapping, OutputStream out) {
        try {
            Writer outWriter = new OutputStreamWriter(out, "UTF-8");
            stream().toXML(mapping, outWriter);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toXml(RecordMapping mapping) {
        return stream().toXML(mapping);
    }

    public static RecordMapping read(InputStream is, MetadataModel metadataModel) throws MetadataException {
        try {
            Reader isReader = new InputStreamReader(is, "UTF-8");
            RecordMapping recordMapping = (RecordMapping) stream().fromXML(isReader);
            RecordDefinition recordDefinition = metadataModel.getRecordDefinition(recordMapping.prefix);
            recordMapping.setRecordDefinition(recordDefinition);
            return recordMapping;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static RecordMapping read(String string, MetadataModel metadataModel) throws MetadataException {
        RecordMapping recordMapping = (RecordMapping) stream().fromXML(string);
        RecordDefinition recordDefinition = metadataModel.getRecordDefinition(recordMapping.prefix);
        recordMapping.setRecordDefinition(recordDefinition);
        return recordMapping;
    }

    static XStream stream() {
        XStream stream = new XStream();
        stream.processAnnotations(RecordMapping.class);
        return stream;
    }

}
