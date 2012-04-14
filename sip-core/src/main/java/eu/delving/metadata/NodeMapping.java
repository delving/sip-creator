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

import static eu.delving.metadata.StringUtil.*;

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

    public List<Path> siblings;

    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

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

    public boolean isVirtual() {
        return recDefNode.getNodeMappings().isEmpty();
    }

    public String getDocumentation() {
        return linesToString(documentation);
    }

    public void setDocumentation(String documentation) {
        this.documentation = stringToLines(documentation);
        recDefNode.notifyNodeMappingChange(this);
    }

    public Operator getOperator() {
        return operator == null ? Operator.ALL : operator;
    }

    public void clearStatsTreeNodes() {
        statsTreeNodes = null;
    }

    public boolean hasMap() {
        return siblings != null;
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
            siblings = new ArrayList<Path>();
            for (int walk = 1; walk < inputPaths.size(); walk++) siblings.add(inputPaths.get(walk));
        }
        return this;
    }

    public List<Path> getInputPaths() {
        List<Path> inputPaths = new ArrayList<Path>();
        inputPaths.add(inputPath);
        if (siblings != null) inputPaths.addAll(siblings);
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
            groovyCode = stringToLines(codeString);
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
                    indentCode(editPath.getEditedCode(outputPath), codeOut);
                    return;
                }
                else if (groovyCode != null) {
                    indentCode(groovyCode, codeOut);
                    return;
                }
            }
        }
        else if (groovyCode != null) {
            indentCode(groovyCode, codeOut);
            return;
        }
        toInnerLoop(getLocalPath(), groovyParams);
    }

    private void toInnerLoop(Path path, Stack<String> groovyParams) {
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            if (dictionary != null) {
                codeOut.line("from%s(%s)", toDictionaryName(this), toLeafGroovyParam(path));
            }
            else if (hasMap()) {
                codeOut.line(getMapUsage());
            }
            else {
                codeOut.line("\"${%s}\"", toLeafGroovyParam(path));
            }
        }
        else if (recDefNode.isLeafElem()) {
            toInnerLoop(path.chop(-1), groovyParams);
        }
        else {
            boolean needLoop;
            if (hasMap()) {
                needLoop = !groovyParams.contains(getMapName());
                if (needLoop) {
                    codeOut.line_(
                            "%s %s { %s ->",
                            toMapExpression(this), getOperator().getChar(), getMapName());
                }
            }
            else {
                String param = toLoopGroovyParam(path);
                needLoop = !groovyParams.contains(param);
                if (needLoop) {
                    codeOut.line_("%s %s { %s ->", toLoopRef(path), getOperator().getChar(), param);
                }
            }
            toInnerLoop(path.chop(-1), groovyParams);
            if (needLoop) codeOut._line("}");
        }
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
        variables.add("_uniqueIdentifier");
        Path back = inputPath.copy();
        while (!back.isEmpty()) {
            variables.add(toGroovyParam(back.peek()));
            back = back.shorten();
        }
        return variables;
    }

    private String getMapUsage() {
        if (!hasMap()) return null;
        StringBuilder usage = new StringBuilder("\"");
        Iterator<Path> walk = getInputPaths().iterator();
        while (walk.hasNext()) {
            Path path = walk.next();
            usage.append(String.format("${%s['%s']}", getMapName(), path.peek().toMapKey()));
            if (walk.hasNext()) usage.append(" ");
        }
        usage.append("\"");
        return usage.toString();
    }

    public String getMapName() {
        return String.format("_M%d", inputPath.size());
    }

    public String toString() {
        if (recDefNode == null) return "No RecDefNode";
        String input = inputPath.getTail();
        if (hasMap()) {
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
}

