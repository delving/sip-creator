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
public class NodeMapping implements Comparable<NodeMapping> {

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

    @XStreamOmitField
    public Object statsTreeNode;

    @Override
    public boolean equals(Object o) {
        return o instanceof NodeMapping && ((NodeMapping) o).inputPath.equals(inputPath);
    }

    @Override
    public int hashCode() {
        return inputPath.hashCode();
    }

    public void attachTo(RecDefNode recDefNode) {
        this.recDefNode = recDefNode;
        this.outputPath = recDefNode.getPath();
    }

    public NodeMapping setStatsTreeNode(Object statsTreeNode) {
        this.statsTreeNode = statsTreeNode;
        return this;
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

    public void setDictionaryDomain(Collection<String> domainValues) {
        if (dictionary == null) dictionary = new TreeMap<String, String>();
        for (String key : domainValues) if (!dictionary.containsKey(key)) dictionary.put(key, "");
        Set<String> unused = new HashSet<String>(dictionary.keySet());
        unused.removeAll(domainValues);
        for (String unusedKey : unused) dictionary.remove(unusedKey);
    }

    public boolean codeLooksLike(String codeString) {
        if (groovyCode == null) return false;
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

    public void toLeafCode(Out out, String editedCode) {
        // todo: use editedCode
        if (recDefNode.isAttr() || recDefNode.isSingular()) {
            out.line("%s : { ", recDefNode.getTag().toBuilderCall());
            out.before();
            toUserCode(out, true);
            out.after();
            out.line("}");
        }
        else {
            out.line("%s * { %s ->", getVariableName(false), getParamName());
            out.before();
            out.line("%s {", recDefNode.getTag().toBuilderCall());
            out.before();
            toUserCode(out, false);
            out.after();
            out.line("}");
            out.after();
            out.line("}");
        }
    }

    public String getUserCode() {
        Out out = new Out();
        toUserCode(out, false);
        return out.toString();
    }

    private void toUserCode(Out out, boolean grabFirst) {
        if (groovyCode != null) {
            indentCode(groovyCode, out);
        }
        else if (dictionary != null) {
            out.line("from%s(%s%s)", getDictionaryName(), getVariableName(true), grabFirst ? "[0]" : "");
        }
        else {
            // todo: show these somewhere in the GUI
            for (String v : getContextVariables()) {
                out.line("// "+v);
            }
            
            out.line("\"${%s%s}\"", getVariableName(true), grabFirst ? "[0]" : "");
        }
    }

    private static void indentCode(List<String> code, Out out) {
        for (String codeLine : code) {
            if (codeIndent(codeLine) < 0) out.after();
            out.line(codeLine);
            if (codeIndent(codeLine) > 0) out.before();
        }
    }

    public void generateDictionaryCode(Out out) {
        if (dictionary == null) return;
        String name = getDictionaryName();
        out.line(String.format("def %s = [", name));
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
        out.line("def from%s = { value ->", name);
        out.before();
        out.line("if (value) {");
        out.before();
        out.line("def v = %s[value.sanitize()];", name);
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

    public String getVariableName(boolean includeSelf) {
        NodeMapping ancestor = getAncestorNodeMapping(includeSelf);
        if (ancestor != null) {
            return GroovyVariable.name(ancestor.inputPath, inputPath);
        }
        else {
            return GroovyVariable.name(inputPath);
        }
    }
    
    public List<String> getContextVariables() {
        List<String> variables = new ArrayList<String>();
        for (RecDefNode ancestor = this.recDefNode; ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping nodeMapping : ancestor.getNodeMappings().values()) {
                variables.add(nodeMapping.getParamName());
            }
        }
        return variables;
    }

    public String getParamName() {
        return GroovyVariable.paramName(inputPath);
    }

    public String toString() {
        return String.format("[%s] => [%s]", inputPath.getTail(), outputPath.getTail());
    }

    private NodeMapping getAncestorNodeMapping(boolean includeSelf) {
        RecDefNode start = includeSelf ? recDefNode : recDefNode.getParent();
        for (RecDefNode ancestor = start; ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping nodeMapping : ancestor.getNodeMappings().values()) {
                if ((includeSelf && nodeMapping.inputPath.equals(inputPath)) ||
                        (nodeMapping.inputPath.isAncestorOf(inputPath))) {
                    return nodeMapping;
                }
            }
        }
        return null;
    }

    private static final Hasher HASHER = new Hasher();

    private String getDictionaryName() {
        return "Dict" + HASHER.getHashString(outputPath.toString()).substring(16);
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

    /**
     * For comparing node mappings within a RecDefNode
     *
     * @param nodeMapping who to compare with
     * @return true if the input paths were the same
     */
    @Override
    public int compareTo(NodeMapping nodeMapping) {
        return this.inputPath.compareTo(nodeMapping.inputPath);
    }

}

