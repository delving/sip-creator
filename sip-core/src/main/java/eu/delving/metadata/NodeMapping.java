/*
 * Copyright 2011, 2012 Delving BV
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static eu.delving.metadata.NodeMappingChange.CODE;
import static eu.delving.metadata.NodeMappingChange.DOCUMENTATION;
import static eu.delving.metadata.StringUtil.getConstantFromGroovyCode;
import static eu.delving.metadata.StringUtil.isSimilarCode;
import static eu.delving.metadata.StringUtil.linesToString;
import static eu.delving.metadata.StringUtil.stringToLines;

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
 */
@XStreamAlias("node-mapping")
public class NodeMapping {

    @XStreamOmitField
    public boolean inputPathMissing;

    public static NodeMapping forConstant(String value) {
        NodeMapping nm = new NodeMapping();
        nm.setInputPath(Path.create().child(Tag.element("constant")));
        nm.setGroovyCode(String.format("'%s'", value));
        return nm;
    }

    @XStreamAsAttribute
    public Path inputPath;

    @XStreamAsAttribute
    public Path outputPath;

    public List<Path> siblings;

    @XStreamAsAttribute
    public Operator operator;

    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

    public List<String> documentation;

    @XStreamOmitField
    public RecDefNode recDefNode;

    @XStreamOmitField
    private SortedSet sourceTreeNodes;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeMapping that = (NodeMapping) o;
        return !(inputPath != null ? !inputPath.equals(that.inputPath) : that.inputPath != null)
                && !(outputPath != null ? !outputPath.equals(that.outputPath) : that.outputPath != null);
    }

    @Override
    public int hashCode() {
        return inputPath.hashCode() + outputPath.hashCode();
    }

    public boolean isConstant() {
        return inputPath != null && "/constant".equals(inputPath.toString());
    }

    public String getConstantValue() {
        if (groovyCode == null || groovyCode.isEmpty()) return "CONSTANT";
        return getConstantFromGroovyCode(groovyCode);
    }

    public int getIndexWithinNode() {
        int index = 0;
        for (NodeMapping nodeMapping : recDefNode.getNodeMappings().values()) {
            if (this == nodeMapping) break;
            index++;
        }
        return index;
    }

    public String getGroovyCode() {
        return groovyCode == null ? null : linesToString(groovyCode);
    }

    public String getDocumentation() {
        return linesToString(documentation);
    }

    public void setDocumentation(String documentation) {
        this.documentation = stringToLines(documentation);
        notifyChanged(DOCUMENTATION);
    }

    public Operator getOperator() {
        if (recDefNode.hasOperator() && operator == null) return recDefNode.getOperator();
        return operator == null ? Operator.ALL : operator;
    }

    public void clearSourceTreeNodes() {
        sourceTreeNodes = null;
    }

    public boolean hasMap() {
        return siblings != null;
    }

    public boolean hasSourceTreeNodes() {
        return sourceTreeNodes != null;
    }

    public boolean hasOneSourceTreeNode() {
        return hasSourceTreeNodes() && sourceTreeNodes.size() == 1;
    }

    public Object getSingleSourceTreeNode() {
        Iterator walk = sourceTreeNodes.iterator();
        return walk.hasNext() ? walk.next() : null;
    }

    public boolean valueHasDictionary() {
        if (hasOptList()) {
            OptList optList = recDefNode.getOptList();
            if (optList.valuePresent) {
                for (RecDefNode kid : recDefNode.getChildren()) {
                    if (!kid.getTag().equals(optList.value)) continue;
                    for (NodeMapping kidNodeMapping : kid.getNodeMappings().values()) {
                        if (kidNodeMapping.hasDictionary()) {
                            return true;
                        }
                    }
                }
            }
            else {
                return hasDictionary();
            }
        }
        return false;
    }

    public boolean hasDictionary() {
        return dictionary != null;
    }

    public boolean hasOptList() {
        return recDefNode.getOptList() != null;
    }

    public OptList getOptList() {
        return recDefNode.getOptList();
    }

    public List<String> getOptListValues() {
        List<String> values = new ArrayList<String>();
        for (OptList.Opt opt : recDefNode.getOptList().opts) values.add(opt.value);
        return values;
    }

    public SortedSet getSourceTreeNodes() {
        return sourceTreeNodes;
    }

    public void attachTo(RecDefNode recDefNode) {
        this.recDefNode = recDefNode;
        this.outputPath = recDefNode.getPath();
    }

    // this method should be called from exactly ONE place!
    public NodeMapping setStatsTreeNodes(SortedSet statsTreeNodes, List<Path> inputPaths) {
        if (statsTreeNodes.isEmpty()) throw new RuntimeException();
        this.sourceTreeNodes = statsTreeNodes;
        setInputPaths(inputPaths);
        return this;
    }

    public NodeMapping setInputPath(Path inputPath) {
        this.inputPath = inputPath;
        return this;
    }

    public void notifyChanged(NodeMappingChange change) {
        if (recDefNode != null) recDefNode.notifyNodeMappingChange(this, change);
    }

    public NodeMapping setInputPaths(Collection<Path> inputPaths) {
        if (inputPaths.isEmpty()) {
            throw new RuntimeException("Trying to set input paths but there are none!");
        }
        Path parent = null;
        for (Path input : inputPaths) {
            if (parent == null) {
                parent = input.parent();
            }
            else if (!parent.equals(input.parent())) {
                throw new RuntimeException(String.format("Input path %s should all be from the same parent %s", input, parent));
            }
        }
        Iterator<Path> pathWalk = inputPaths.iterator();
        this.inputPath = pathWalk.next();
        if (pathWalk.hasNext()) {
            siblings = new ArrayList<Path>();
            while (pathWalk.hasNext()) siblings.add(pathWalk.next());
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

    public boolean codeLooksLike(String codeString) {
        return groovyCode == null || isSimilarCode(codeString, groovyCode);
    }

    public void setGroovyCode(String codeString) {
        if (codeString == null || codeString.trim().isEmpty()) {
            if (groovyCode != null) {
                groovyCode = null;
                notifyChanged(CODE);
            }
        }
        else {
            if (groovyCode == null || !codeLooksLike(codeString)) {
                groovyCode = stringToLines(codeString);
                notifyChanged(CODE);
            }
        }
    }

    public boolean isUserCodeEditable() {
        return recDefNode.isAttr() || recDefNode.isLeafElem();
    }

    public String toSortString(boolean sourceTargetOrdering) {
        String input = createInputString();
        String targetTail = recDefNode.getPath().getTail();
        if (input.equals(targetTail)) {
            return input;
        }
        else if (sourceTargetOrdering) {
            return String.format("%s >> %s", input, targetTail);
        }
        else {
            return String.format("%s << %s", targetTail, input);
        }
    }

    public String createInputString() {
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
        return input;
    }

    public String toString() {
        if (recDefNode == null) return "No RecDefNode";
        return recDefNode.toString();
    }
}

