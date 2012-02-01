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

    public NodeMapping setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public void addCodeLine(String line) {
        if (groovyCode == null) groovyCode = new ArrayList<String>();
        line = line.trim();
        if (!line.isEmpty()) groovyCode.add(line);
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
            if (line.isEmpty()) continue;
            if (!walk.hasNext()) return false;
            if (!walk.next().equals(line)) return false;
        }
        return !walk.hasNext();
    }

    public void setGroovyCode(String groovyCode) {
        this.groovyCode = null;
        for (String line : groovyCode.split("\n")) addCodeLine(line);
        recDefNode.notifyNodeMappingChange(this);
    }

    public void toLeafCode(Out out, String editedCode) {
        if (recDefNode.isAttr()) {
            out.line_("%s : {", recDefNode.getTag().toBuilderCall());
            toUserCode(out, editedCode);
            out._line("}");
        }
        else if (recDefNode.isLeafElem()) {
            toUserCode(out, editedCode);
        }
        else {
            throw new RuntimeException("Should not call this");
        }
    }

    public String getUserCode(String editedCode) {
        Out out = new Out();
        toUserCode(out, editedCode);
        return out.toString();
    }

    public boolean isUserCodeEditable() {
        return recDefNode.isAttr() || recDefNode.isLeafElem();
    }

    private void toUserCode(Out out, String editedCode) {
        if (editedCode != null) {
            StringUtil.indentCode(editedCode, out);
        }
        else if (groovyCode != null) {
            StringUtil.indentCode(groovyCode, out);
        }
        else {
            toInnerLoop(getLocalPath(), out);
        }
    }

    private void toInnerLoop(Path path, Out out) {
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            Tag inner = path.getTag(0);
            if (dictionary != null) {
                out.line("from%s(%s)", getDictionaryName(), inner.toGroovyParam());
            }
            else {
                out.line("\"${%s}\"", inner.toGroovyParam());
            }
        }
        else if (recDefNode.isLeafElem()) {
            toInnerLoop(path.chop(-1), out);
        }
        else {
            Tag outer = path.getTag(0);
            Tag inner = path.getTag(1);
            out.line_("%s%s * { %s ->", outer.toGroovyParam(), inner.toGroovyRef(), inner.toGroovyParam());
            toInnerLoop(path.chop(-1), out);
            out._line("}");
        }
    }

    public void generateDictionaryCode(Out out) {
        if (dictionary == null) return;
        String name = getDictionaryName();
        out.line_(String.format("def %s = [", name));
        Iterator<Map.Entry<String, String>> walk = dictionary.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<String, String> entry = walk.next();
            out.line(String.format("'''%s''':'''%s'''%s",
                    StringUtil.sanitizeGroovy(entry.getKey()),
                    StringUtil.sanitizeGroovy(entry.getValue()),
                    walk.hasNext() ? "," : ""
            ));
        }
        out._line("]");
        out.line_("def from%s = { value ->", name);
        out.line_("if (value) {");
        out.line("def v = %s[value.sanitize()];", name);
        out.line_("if (v) {");
        out.line_("if (v.endsWith(':')) {");
        out.line("return \"${v} ${value}\"");
        out._line("} else {").in();
        out.line("return v");
        out._line("}");
        out._line("}");
        out._line("}");
        out.line("return ''");
        out._line("}");
    }

    public Path getLocalPath() {
        NodeMapping ancestor = getAncestorNodeMapping();
        Path contained = inputPath.minusAncestor(ancestor.inputPath);
        return contained.prefixWith(ancestor.inputPath.peek());
    }

    public List<String> getContextVariables() {
        List<String> variables = new ArrayList<String>();
        Path back = inputPath.copy();
        while (!back.isEmpty()) variables.add(back.pop().toGroovyParam());
        return variables;
    }

    public String toString() {
        return String.format("[%s] => [%s]", inputPath.getTail(), outputPath.getTail());
    }

    private NodeMapping getAncestorNodeMapping() {
        for (RecDefNode ancestor = recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping nodeMapping : ancestor.getNodeMappings().values()) {
                if (nodeMapping.inputPath.isAncestorOf(inputPath)) return nodeMapping;
            }
        }
        return new NodeMapping().setInputPath(Path.create("input")).setOutputPath(outputPath.chop(1));
    }

    private static final Hasher HASHER = new Hasher();

    private String getDictionaryName() {
        return "Dict" + HASHER.getHashString(outputPath.toString()).substring(16);
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

