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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import eu.delving.groovy.GroovyVariable;

import java.util.*;

/**
 * This class describes how one node is transformed into another, which is part of mapping
 * one hierarchy onto another.  It can contain a dictionary, as well as a snippet
 * of Groovy code.
 * <p/>
 * Instances of this class are placed in the RecDefNode elements of the record definition
 * so that that data structure can be used as a scaffolding to recursively write the code
 * for the Groovy builder.
 * <p/>
 * Instances are also stored in a list in the RecMapping, and upon reading a mapping they
 * are distributed into the local prototype instance of the record definition data structure.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("node-mapping")
public class NodeMapping {

    @XStreamAsAttribute
    public Path inputPath;

    @XStreamAsAttribute
    public Path outputPath;

    @XStreamAlias("dictionary")
    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

    @XStreamOmitField
    public RecDefNode recDefNode;

    public void attachTo(RecDefNode recDefNode) {
        this.recDefNode = recDefNode;
        this.outputPath = recDefNode.getPath();
    }

    public NodeMapping setInputPath(Path inputPath) {
        this.inputPath = inputPath;
        return this;
    }

    public void clearDictionary() {
        dictionary = null;
    }

    public void clearCode() {
        groovyCode = null;
    }

    public void addCodeLine(String line) {
        if (groovyCode == null) {
            groovyCode = new ArrayList<String>();
        }
        groovyCode.add(line.trim());
    }

    public void setDictionaryDomain(Set<String> domainValues) {
        if (dictionary == null) dictionary = new TreeMap<String, String>();
        for (String key : domainValues) if (!dictionary.containsKey(key)) dictionary.put(key, "");
        Set<String> unused = new HashSet<String>(dictionary.keySet());
        unused.removeAll(domainValues);
        for (String unusedKey : unused) dictionary.remove(unusedKey);
    }

    public boolean codeLooksLike(String codeString) {
        Iterator<String> walk = groovyCode.iterator();
        for (String line : codeString.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (!walk.hasNext()) {
                    return false;
                }
                String codeLine = walk.next();
                if (!codeLine.equals(line)) {
                    return false;
                }
            }
        }
        return !walk.hasNext();
    }

    public void setGroovyCode(String groovyCode) {
        this.groovyCode = null;
        for (String line : groovyCode.split("\n")) addCodeLine(line);
    }

    public void toCode(RecDefTree.Out out, String editedCode) {
        if (dictionary != null) {
//            out.line("lookup%s(%s)", getDictionaryName(), getVariableName());
            out.line("'dictionary'");
        }
        else if (groovyCode != null) {
            for (String codeLine : groovyCode) {
                if (codeIndent(codeLine) < 0) out.after();
                out.line(codeLine);
                if (codeIndent(codeLine) > 0) out.before();
            }
        }
        else {
//            out.line(getVariableName());
            out.line("\"It's ${dogExists}!\"");
        }
    }

    public void generateDictionaryCode(RecDefTree.Out out) {
        if (dictionary == null) return;
        String name = getDictionaryName();
        out.line(String.format("def dictionary%s = [", name));
        out.before();
        Iterator<Map.Entry<String, String>> walk = dictionary.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<String, String> entry = walk.next();
            out.line(String.format("'''%s''':'''%s'''%s",
                    Sanitizer.sanitizeGroovy(entry.getKey()),
                    Sanitizer.sanitizeGroovy(entry.getValue()),
                    walk.hasNext() ? "," : ""
            ));
        }
        out.after();
        out.line("]");
        out.line("def lookup%s = { value ->", name);
        out.before();
        out.line("if (value) {");
        out.before();
        out.line("def v = dictionary%s[value.sanitize()];", name);
        out.line("if (v) {");
        out.before();
        out.line("if (v.endsWith(':')) {");
        out.before();
        out.line("return \"${v} ${value}\"");
        out.after();
        out.line("} else {");
        out.before();
        out.line("return v");
        out.after();
        out.line("}");
        out.after();
        out.line("}");
        out.after();
        out.line("}");
        out.line("return ''");
        out.after();
        out.line("}");
    }

    public String getVariableName() {
        NodeMapping ancestor = getAncestorNodeMapping();
        if (ancestor != null) {
            return GroovyVariable.name(ancestor.inputPath, inputPath);
        }
        else {
            return GroovyVariable.name(inputPath);
        }
    }

    public String getParamName() {
        NodeMapping ancestor = getAncestorNodeMapping();
        if (ancestor == null) throw new RuntimeException("Not sure what to do");
        return GroovyVariable.paramName(ancestor.inputPath);
    }

    public String toString() {
        return outputPath.toString();
    }

    private NodeMapping getAncestorNodeMapping() {
        for (RecDefNode ancestor = recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getNodeMapping() != null) return ancestor.getNodeMapping();
        }
        return null;
    }

    private String getDictionaryName() {
        return outputPath.toString().replaceAll("[@:/]", "_");
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

}

