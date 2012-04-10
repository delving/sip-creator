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
public class NodeMapping {

    @XStreamAsAttribute
    public Path inputPath;

    @XStreamAsAttribute
    public Path outputPath;

    @XStreamAsAttribute
    public Operator operator;

    @XStreamAlias("tuplePaths")
    public List<Path> tuplePaths;

    @XStreamAlias("dictionary")
    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

    @XStreamAlias("documentation")
    public List<String> documentation;

    @XStreamOmitField
    public RecDefNode recDefNode;

    @XStreamOmitField
    public CodeOut codeOut;

    @XStreamOmitField
    private SortedSet statsTreeNodes;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeMapping that = (NodeMapping) o;
        if (inputPath != null ? !inputPath.equals(that.inputPath) : that.inputPath != null) return false;
        if (outputPath != null ? !outputPath.equals(that.outputPath) : that.outputPath != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return inputPath.hashCode();
    }

    public String getDocumentation() {
        return StringUtil.linesToString(documentation);
    }

    public void setDocumentation(String documentation) {
        this.documentation = StringUtil.stringToLines(documentation);
        recDefNode.notifyNodeMappingChange(this);
    }

    public Operator getOperator() {
        return operator == null ? Operator.ALL : operator;
    }

    public void clearStatsTreeNodes() {
        statsTreeNodes = null;
    }

    public boolean hasTuple() {
        return tuplePaths != null;
    }

    public boolean hasStatsTreeNodes() {
        return statsTreeNodes != null;
    }

    public boolean hasOneStatsTreeNode() {
        return hasStatsTreeNodes() && statsTreeNodes.size() == 1;
    }

    public Object getSingleStatsTreeNode() {
        return statsTreeNodes.iterator().next();
    }

    public SortedSet getSourceTreeNodes() {
        return statsTreeNodes;
    }

    public void attachTo(RecDefNode recDefNode) {
        this.recDefNode = recDefNode;
        this.outputPath = recDefNode.getPath();
    }

    // this method should be called from exactly ONE place!
    public NodeMapping setStatsTreeNodes(SortedSet statsTreeNodes, List<Path> inputPaths) {
        if (statsTreeNodes.isEmpty()) throw new RuntimeException();
        this.statsTreeNodes = statsTreeNodes;
        setInputPaths(inputPaths);
        return this;
    }

    public NodeMapping setInputPath(Path inputPath) {
        this.inputPath = inputPath;
        return this;
    }

    public void notifyChanged() {
        if (recDefNode != null) recDefNode.notifyNodeMappingChange(this);
    }

    public NodeMapping setInputPaths(List<Path> inputPaths) {
        if (inputPaths.isEmpty()) throw new RuntimeException();
        Path parent = null;
        for (Path input : inputPaths) {
            if (parent == null) {
                parent = input.getParent();
            }
            else if (!parent.equals(input.getParent())) {
                throw new RuntimeException(String.format("Input path %s should all be from the same parent %s", input, parent));
            }
        }
        this.inputPath = inputPaths.get(0);
        if (inputPaths.size() > 1) {
            tuplePaths = new ArrayList<Path>();
            for (int walk = 1; walk < inputPaths.size(); walk++) tuplePaths.add(inputPaths.get(walk));
        }
        return this;
    }

    public List<Path> getInputPaths() {
        List<Path> inputPaths = new ArrayList<Path>();
        inputPaths.add(inputPath);
        if (tuplePaths != null) inputPaths.addAll(tuplePaths);
        Collections.sort(inputPaths);
        return inputPaths;
    }

    public NodeMapping setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
        return this;
    }

    public boolean setDictionaryDomain(Collection<String> domainValues) {
        boolean changed = false;
        if (dictionary == null) {
            dictionary = new TreeMap<String, String>();
            changed = true;
        }
        for (String key : domainValues)
            if (!dictionary.containsKey(key)) {
                dictionary.put(key, "");
                changed = true;
            }
        Set<String> unused = new HashSet<String>(dictionary.keySet());
        unused.removeAll(domainValues);
        for (String unusedKey : unused) {
            dictionary.remove(unusedKey);
            changed = true;
        }
        groovyCode = null;
        return changed;
    }

    public boolean removeDictionary() {
        if (dictionary == null) return false;
        dictionary = null;
        groovyCode = null;
        return true;
    }

    public boolean generatedCodeLooksLike(String codeString, RecMapping recMapping) {
        if (codeString == null) return false;
        List<String> list = Arrays.asList(getCode(getGeneratorEditPath(), recMapping).split("\n"));
        Iterator<String> walk = list.iterator();
        return isSimilar(codeString, walk);
    }

    public String getCode(EditPath editPath, RecMapping recMapping) {
        recMapping.toCode(editPath);
        return codeOut.toString();
    }

    public void setGroovyCode(String codeString, RecMapping recMapping) {
        if (codeString == null || generatedCodeLooksLike(codeString, recMapping)) {
            if (groovyCode != null) {
                groovyCode = null;
                recDefNode.notifyNodeMappingChange(this);
            }
        }
        else if (groovyCode == null || !isSimilar(codeString, groovyCode.iterator())) {
            groovyCode = StringUtil.stringToLines(codeString);
            recDefNode.notifyNodeMappingChange(this);
        }
    }

    public void toAttributeCode(Stack<String> groovyParams, EditPath editPath) {
        if (!recDefNode.isAttr()) return;
        toUserCode(groovyParams, editPath);
    }

    public void toLeafElementCode(Stack<String> groovyParams, EditPath editPath) {
        if (recDefNode.isAttr() || !recDefNode.isLeafElem()) return;
        toUserCode(groovyParams, editPath);
    }

    public boolean isUserCodeEditable() {
        return recDefNode.isAttr() || recDefNode.isLeafElem();
    }

    private boolean isSimilar(String codeString, Iterator<String> walk) {
        for (String line : codeString.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!walk.hasNext()) return false;
            while (walk.hasNext()) {
                String otherLine = walk.next().trim();
                if (otherLine.isEmpty()) continue;
                if (!otherLine.equals(line)) return false;
                break;
            }
        }
        return !walk.hasNext();
    }

    private void toUserCode(Stack<String> groovyParams, EditPath editPath) {
        if (editPath != null) {
            if (!editPath.generated()) {
                if (editPath.getEditedCode(outputPath) != null) {
                    StringUtil.indentCode(editPath.getEditedCode(outputPath), codeOut);
                    return;
                }
                else if (groovyCode != null) {
                    StringUtil.indentCode(groovyCode, codeOut);
                    return;
                }
            }
        }
        else if (groovyCode != null) {
            StringUtil.indentCode(groovyCode, codeOut);
            return;
        }
        toInnerLoop(getLocalPath(), groovyParams);
    }

    private void toInnerLoop(Path path, Stack<String> groovyParams) {
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            Tag inner = path.getTag(0);
            if (dictionary != null) {
                codeOut.line("from%s(%s)", getDictionaryName(), inner.toGroovyParam());
            }
            else if (tuplePaths != null) {
                codeOut.line(getTupleUsage());
            }
            else {
                codeOut.line("\"${%s}\"", inner.toGroovyParam());
            }
        }
        else if (recDefNode.isLeafElem()) {
            toInnerLoop(path.chop(-1), groovyParams);
        }
        else {
            boolean needLoop;
            if (tuplePaths != null) {
                needLoop = !groovyParams.contains(getTupleName());
                if (needLoop) {
                    codeOut.line_("%s %s { %s ->", getTupleExpression(), getOperator().getChar(), getTupleName());
                }
            }
            else {
                Tag outer = path.getTag(0);
                Tag inner = path.getTag(1);
                needLoop = !groovyParams.contains(inner.toGroovyParam());
                if (needLoop) {
                    codeOut.line_("%s%s %s { %s ->", outer.toGroovyParam(), inner.toGroovyRef(), getOperator().getChar(), inner.toGroovyParam());
                }
            }
            toInnerLoop(path.chop(-1), groovyParams);
            if (needLoop) codeOut._line("}");
        }
    }

    public void generateDictionaryCode(CodeOut codeOut) {
        if (dictionary == null) return;
        String name = getDictionaryName();
        codeOut.line_(String.format("def %s = [", name));
        Iterator<Map.Entry<String, String>> walk = dictionary.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<String, String> entry = walk.next();
            codeOut.line(String.format("'''%s''':'''%s'''%s",
                    StringUtil.sanitizeGroovy(entry.getKey()),
                    StringUtil.sanitizeGroovy(entry.getValue()),
                    walk.hasNext() ? "," : ""
            ));
        }
        codeOut._line("]");
        codeOut.line_("def from%s = { value ->", name);
        codeOut.line_("if (value) {");
        codeOut.line("def v = %s[value.sanitize()];", name);
        codeOut.line_("if (v) {");
        codeOut.line_("if (v.endsWith(':')) {");
        codeOut.line("return \"${v} ${value}\"");
        codeOut._line("} else {").in();
        codeOut.line("return v");
        codeOut._line("}");
        codeOut._line("}");
        codeOut._line("}");
        codeOut.line("return ''");
        codeOut._line("}");
    }

    public Path getLocalPath() {
        NodeMapping ancestor = getAncestorNodeMapping(inputPath);
        if (ancestor.inputPath.isAncestorOf(inputPath)) {
            Path contained = inputPath.minusAncestor(ancestor.inputPath);
            return contained.prefixWith(ancestor.inputPath.peek());
        }
        else {
            return inputPath;
        }
    }

    public List<String> getContextVariables() {
        List<String> variables = new ArrayList<String>();
        Path back = inputPath.copy();
        while (!back.isEmpty()) variables.add(back.pop().toGroovyParam());
        return variables;
    }

    public String getTupleName() {
        StringBuilder name = new StringBuilder();
        for (Path inputPath : getInputPaths()) {
            Tag inner = inputPath.peek();
            name.append(inner.toGroovyParam());
        }
        return name.toString();
    }

    public String getTupleExpression() {
        if (tuplePaths == null) return null;
        StringBuilder tuple = new StringBuilder("(");
        Iterator<Path> walk = getInputPaths().iterator();
        while (walk.hasNext()) {
            Path inputPath = walk.next();
            if (inputPath.size() < 2) throw new RuntimeException("Path too short");
            Tag outer = inputPath.getTag(-2);
            Tag inner = inputPath.getTag(-1);
            tuple.append(outer.toGroovyParam()).append(inner.toGroovyRef());
            if (walk.hasNext()) tuple.append(" | ");
        }
        tuple.append(")");
        return tuple.toString();
    }

    public String toString() {
        if (recDefNode == null) return "No RecDefNode";
        String input = inputPath.getTail();
        if (tuplePaths != null) {
            StringBuilder out = new StringBuilder();
            Iterator<Path> walk = getInputPaths().iterator();
            while (walk.hasNext()) {
                out.append(walk.next().getTail());
                if (walk.hasNext()) out.append(", ");
            }
            input = out.toString();
        }
        if (groovyCode == null) {
            return String.format("<html><p>%s &larr; %s</p>", recDefNode.toString(), input);
        }
        else {
            return String.format("<html><b>%s &larr; %s</b>", recDefNode.toString(), input);
        }
    }

    private String getTupleUsage() {
        if (tuplePaths == null) return null;
        String name = getTupleName();
        int size = tuplePaths.size() + 1;
        StringBuilder usage = new StringBuilder("\"");
        for (int walk = 0; walk < size; walk++) {
            usage.append(String.format("${%s[%d]}", name, walk));
            if (walk < size - 1) usage.append(" ");
        }
        usage.append("\"");
        return usage.toString();
    }

    private NodeMapping getAncestorNodeMapping(Path path) {
        for (RecDefNode ancestor = recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping nodeMapping : ancestor.getNodeMappings().values()) {
                if (nodeMapping.inputPath.isAncestorOf(path)) return nodeMapping;
            }
        }
        return new NodeMapping().setInputPath(Path.create("input")).setOutputPath(outputPath.chop(1));
    }

    private EditPath getGeneratorEditPath() {
        return new EditPath() {
            @Override
            public Path getPath() {
                return outputPath;
            }

            @Override
            public String getEditedCode(Path path) {
                return null;
            }

            @Override
            public boolean generated() {
                return true;
            }
        };
    }

    private static final Hasher HASHER = new Hasher();

    private String getDictionaryName() {
        return "Dictionary" + HASHER.getHashString(outputPath.toString()).substring(16);
    }
}

