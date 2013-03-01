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

import java.util.*;

import static eu.delving.metadata.StringUtil.*;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CodeGenerator {
    public static final String ABSENT_IS_FALSE = "_absent_ = false";
    private RecMapping recMapping;
    private EditPath editPath;
    private CodeOut codeOut = new CodeOut();
    private boolean trace;

    public CodeGenerator(RecMapping recMapping) {
        this.recMapping = recMapping;
    }

    public CodeGenerator withEditPath(EditPath editPath) {
        this.editPath = editPath;
        return this;
    }

    public CodeGenerator withTrace(boolean trace) {
        this.trace = trace;
        return this;
    }

    public String toRecordMappingCode() {
        generate();
        return codeOut.toString();
    }

    public String toNodeMappingCode() {
        codeOut.setNodeMapping(editPath.getNodeMapping());
        generate();
        return codeOut.getNodeMappingCode();
    }

    private void generate() {
        codeOut.line("// SIP-Creator Generated Mapping Code");
        codeOut.line("// ----------------------------------");
        codeOut.line("// Discarding:");
        codeOut.line("import eu.delving.groovy.DiscardRecordException");
        codeOut.line("def discard = { reason -> throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("// Facts:");
        for (Map.Entry<String, String> entry : recMapping.getFacts().entrySet()) {
            codeOut.line(String.format("String %s = '''%s'''", entry.getKey(), entry.getValue()));
        }
        codeOut.line("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'");
        codeOut.line("// Functions from Mapping:");
        Set<String> names = new TreeSet<String>();
        for (MappingFunction function : recMapping.getFunctions()) {
            function.toCode(codeOut);
            names.add(function.name);
        }
        RecDefTree recDefTree = recMapping.getRecDefTree();
        RecDef recDef = recDefTree.getRecDef();
        if (recDef.functions != null) {
            codeOut.line("// Functions from Record Definition:");
            for (MappingFunction function : recDef.functions) {
                if (names.contains(function.name)) continue;
                function.toCode(codeOut);
                names.add(function.name);
            }
        }
        codeOut.line("// Dictionaries:");
        for (NodeMapping nodeMapping : recDefTree.getNodeMappings()) {
            StringUtil.toDictionaryCode(nodeMapping, codeOut);
        }
        codeOut.line("// DSL Category wraps Builder call:");
        codeOut.line("boolean _absent_");
        codeOut.line("org.w3c.dom.Node outputNode");
        codeOut.line_("use (MappingCategory) {");
        codeOut.line_("input * { _input ->");
        codeOut.line("_uniqueIdentifier = _input['@id'][0].toString()");
        codeOut.line("outputNode = output.");
        if (recDefTree.getRoot().isPopulated()) {
            toElementCode(recDefTree.getRoot(), new Stack<String>());
        }
        else {
            codeOut.line("no 'mapping'");
        }
        codeOut._line("}");
        codeOut.line("outputNode");
        codeOut._line("}");
        codeOut.line("// ----------------------------------");
    }


    private void toElementCode(RecDefNode recDefNode, Stack<String> groovyParams) {
        if (recDefNode.isAttr() || !recDefNode.isPopulated()) return;
        if (editPath != null && !recDefNode.getPath().isFamilyOf(editPath.getNodeMapping().outputPath)) return;
        if (recDefNode.getNodeMappings().isEmpty()) {
            if (recDefNode.isRootOpt()) {
                Set<Path> siblingPaths = getSiblingInputPathsOfChildren(recDefNode);
                if (siblingPaths != null && !siblingPaths.isEmpty()) {
                    NodeMapping nodeMapping = new NodeMapping().setOutputPath(recDefNode.getPath()).setInputPaths(siblingPaths);
                    nodeMapping.recDefNode = recDefNode;
                    codeOut.start(nodeMapping);
                    toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
                    codeOut.end(nodeMapping);
                    return;
                }
            }
            if (!recDefNode.isLeafElem()) {
                toBranchCode(recDefNode, groovyParams);
            }
            else if (recDefNode.hasActiveAttributes()) {
                startBuilderCall(recDefNode, false, groovyParams);
                codeOut.line("// no node mappings");
                codeOut._line("}");
            }
        }
        else if (editPath != null && editPath.getNodeMapping().recDefNode == recDefNode) {
            NodeMapping nodeMapping = editPath.getNodeMapping();
            codeOut.line("_absent_ = true");
            codeOut.start(nodeMapping);
            toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
            codeOut.end(nodeMapping);
            addIfAbsentCode(recDefNode, nodeMapping, groovyParams);
        }
        else {
            for (NodeMapping nodeMapping : recDefNode.getNodeMappings().values()) {
                codeOut.line("_absent_ = true");
                codeOut.start(nodeMapping);
                toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), groovyParams);
                codeOut.end(nodeMapping);
                addIfAbsentCode(recDefNode, nodeMapping, groovyParams);
            }
        }
    }

    private void addIfAbsentCode(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        List<String> groovyCode = nodeMapping.groovyCode;
        if (editPath != null) {
            if (!editPath.isGeneratedCode()) {
                String editedCode = editPath.getEditedCode(recDefNode.getPath());
                if (editedCode != null) groovyCode = StringUtil.stringToLines(editedCode);
            }
        }
        List<String> ifAbsentCode = StringUtil.getIfAbsentCode(groovyCode);
        if (ifAbsentCode != null) {
            codeOut.line_("if (_absent_) {");
            startBuilderCall(recDefNode, false, groovyParams);
            indentCode(ifAbsentCode, codeOut);
            codeOut._line("}");
            codeOut._line("}");
        }
    }

    private void toNodeMappingLoop(RecDefNode recDefNode, NodeMapping nodeMapping, Path path, Stack<String> groovyParams) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            if (recDefNode.isLeafElem()) {
                toLeafCode(recDefNode, nodeMapping, groovyParams);
            }
            else {
                toBranchCode(recDefNode, groovyParams);
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            if (groovyParams.contains(getMapName(nodeMapping))) {
                toMapNodeMapping(recDefNode, nodeMapping, groovyParams);
            }
            else {
                trace();
                codeOut.line_(
                        "%s %s { %s ->",
                        toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
                );
                groovyParams.push(getMapName(nodeMapping));
                toMapNodeMapping(recDefNode, nodeMapping, groovyParams);
                groovyParams.pop();
                codeOut._line("}");
            }
        }
        else { // path should never be empty
            Operator operator = nodeMapping.getOperator();
            if (path.size() > 2 && operator != Operator.FIRST) operator = Operator.ALL;
            String param = toLoopGroovyParam(path);
            if (groovyParams.contains(param)) {
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), groovyParams);
            }
            else {
                trace();
                codeOut.line_(
                        "%s %s { %s ->",
                        toLoopRef(path), operator.getCodeString(), param
                );
                groovyParams.push(param);
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), groovyParams);
                groovyParams.pop();
                codeOut._line("}");
            }
        }
    }

    private void toMapNodeMapping(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (recDefNode.isLeafElem()) {
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, groovyParams);
            codeOut.end(nodeMapping);
            codeOut._line("}");
        }
        else {
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    trace();
                    codeOut.line(
                            "%s '%s'",
                            sub.getTag().toBuilderCall(), sub.getOptBox()
                    );
                }
                else {
                    toElementCode(sub, groovyParams);
                }
            }
            codeOut.end(nodeMapping);
            codeOut._line("}");
        }
    }

    private void toBranchCode(RecDefNode recDefNode, Stack<String> groovyParams) {
        startBuilderCall(recDefNode, false, groovyParams);
        for (RecDefNode sub : recDefNode.getChildren()) {
            if (sub.isAttr()) continue;
            if (sub.isChildOpt()) {
                trace();
                codeOut.line(
                        "%s '%s'",
                        sub.getTag().toBuilderCall(), sub.getOptBox()
                );
            }
            else {
                toElementCode(sub, groovyParams);
            }
        }
        codeOut._line("}");
    }

    private void toLeafCode(RecDefNode recDefNode, NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (nodeMapping.hasMap()) {
            trace();
            codeOut.line_(
                    "%s %s { %s ->",
                    toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
            );
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, groovyParams);
            codeOut.end(nodeMapping);
            codeOut._line("}");
            codeOut._line("}");
        }
        else {
            startBuilderCall(recDefNode, true, groovyParams);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, groovyParams);
            codeOut.end(nodeMapping);
            codeOut._line("}");
        }
    }

    private void startBuilderCall(RecDefNode recDefNode, boolean absentFalse, Stack<String> groovyParams) {
        if (recDefNode.hasActiveAttributes()) {
            Tag tag = recDefNode.getTag();
            trace();
            codeOut.line_("%s (", tag.toBuilderCall());
            boolean comma = false;
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (!sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    if (comma) codeOut.line(",");
                    OptBox dictionaryOptBox = sub.getDictionaryOptBox();
                    if (dictionaryOptBox != null) {
                        if (recDefNode.getNodeMappings().size() == 1) {
                            NodeMapping nodeMapping = recDefNode.getNodeMappings().values().iterator().next();
                            trace();
                            codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                            toDictionaryCode(nodeMapping, groovyParams, sub.getOptBox().role);
                            codeOut._line("}");
                        }
                        else { // this is actually a kind of error:
                            trace();
                            codeOut.line("%s : '%s'", sub.getTag().toBuilderCall(), sub.getOptBox());
                        }
                    }
                    else {
                        trace();
                        codeOut.line("%s : '%s'", sub.getTag().toBuilderCall(), sub.getOptBox());
                    }
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.getNodeMappings().values()) {
                        if (comma) codeOut.line(",");
                        trace();
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        codeOut.start(nodeMapping);
                        toAttributeCode(nodeMapping, groovyParams);
                        codeOut.end(nodeMapping);
                        codeOut._line("}");
                        comma = true;
                    }
                }
            }
            codeOut._line(
                    ") { %s",
                    absentFalse ? ABSENT_IS_FALSE : ""
            ).in();
        }
        else {
            codeOut.line_(
                    "%s { %s",
                    recDefNode.getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : ""
            );
        }
    }

    private void toUserCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (editPath != null) {
            if (!editPath.isGeneratedCode()) {
                String editedCode = editPath.getEditedCode(nodeMapping.recDefNode.getPath());
                if (editedCode != null) {
                    indentCode(editedCode, codeOut);
                    return;
                }
                else if (nodeMapping.groovyCode != null) {
                    indentCode(nodeMapping.groovyCode, codeOut);
                    return;
                }
            }
        }
        else if (nodeMapping.groovyCode != null) {
            indentCode(nodeMapping.groovyCode, codeOut);
            return;
        }
        toInnerLoop(nodeMapping, getLocalPath(nodeMapping), groovyParams, OptRole.ROOT);
    }

    private void toInnerLoop(NodeMapping nodeMapping, Path path, Stack<String> groovyParams, OptRole optRole) {
        RecDefNode recDefNode = nodeMapping.recDefNode;
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            OptBox dictionaryOptBox = nodeMapping.recDefNode.getDictionaryOptBox();
            optRole = optRole == OptRole.ROOT ? OptRole.VALUE : optRole;
            if (dictionaryOptBox != null) {
                codeOut.line(
                        "lookup%s_%s(%s)",
                        dictionaryOptBox.getDictionaryName(nodeMapping.getIndexWithinNode()), optRole.getFieldName(), toLeafGroovyParam(path)
                );
            }
            else if (nodeMapping.hasMap()) {
                codeOut.line(getMapUsage(nodeMapping));
            }
            else {
                String sanitize = recDefNode.getFieldType().equalsIgnoreCase("link") ? ".sanitizeURI()" : "";
                if (path.peek().getLocalName().equals("constant")) {
                    codeOut.line("'CONSTANT'");
                }
                else if (recDefNode.hasFunction()) {
                    codeOut.line("\"${%s(%s)%s}\"", recDefNode.getFunction(), toLeafGroovyParam(path), sanitize);
                }
                else {
                    codeOut.line("\"${%s%s}\"", toLeafGroovyParam(path), sanitize);
                }
            }
        }
        else if (recDefNode.isLeafElem()) {
            toInnerLoop(nodeMapping, path.withRootRemoved(), groovyParams, optRole);
        }
        else {
            boolean needLoop;
            if (nodeMapping.hasMap()) {
                needLoop = !groovyParams.contains(getMapName(nodeMapping));
                if (needLoop) {
                    trace();
                    codeOut.line_(
                            "%s %s { %s ->",
                            toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
                    );
                }
            }
            else {
                String param = toLoopGroovyParam(path);
                needLoop = !groovyParams.contains(param);
                if (needLoop) {
                    trace();
                    codeOut.line_(
                            "%s %s { %s ->",
                            toLoopRef(path), nodeMapping.getOperator().getCodeString(), param
                    );
                }
            }
            toInnerLoop(nodeMapping, path.withRootRemoved(), groovyParams, optRole);
            if (needLoop) codeOut._line("}");
        }
    }

    private String getMapUsage(NodeMapping nodeMapping) {
        if (!nodeMapping.hasMap()) return null;
        StringBuilder usage = new StringBuilder("\"");
        Iterator<Path> walk = nodeMapping.getInputPaths().iterator();
        while (walk.hasNext()) {
            Path path = walk.next();
            usage.append(String.format("${%s['%s']}", getMapName(nodeMapping), path.peek().toMapKey()));
            if (walk.hasNext()) usage.append(" ");
        }
        usage.append("\"");
        return usage.toString();
    }

    private String getMapName(NodeMapping nodeMapping) {
        return String.format("_M%d", nodeMapping.inputPath.size());
    }

    private void toAttributeCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (!nodeMapping.recDefNode.isAttr()) return;
        toUserCode(nodeMapping, groovyParams);
    }

    private void toLeafElementCode(NodeMapping nodeMapping, Stack<String> groovyParams) {
        if (nodeMapping.recDefNode.isAttr() || !nodeMapping.recDefNode.isLeafElem())
            throw new IllegalStateException("Not a leaf element!");
        toUserCode(nodeMapping, groovyParams);
    }

    private void toDictionaryCode(NodeMapping nodeMapping, Stack<String> groovyParams, OptRole optRole) {
        toInnerLoop(nodeMapping, getLocalPath(nodeMapping), groovyParams, optRole);
    }

    private Set<Path> getSiblingInputPathsOfChildren(RecDefNode recDefNode) {
        List<NodeMapping> subMappings = new ArrayList<NodeMapping>();
        for (RecDefNode sub : recDefNode.getChildren()) sub.collectNodeMappings(subMappings);
        Set<Path> inputPaths = new TreeSet<Path>();
        Path parent = null;
        for (NodeMapping subMapping : subMappings) {
            if (parent == null) {
                parent = subMapping.inputPath.parent();
            }
            else {
                if (!subMapping.inputPath.parent().equals(parent)) return null; // different parents
            }
            inputPaths.addAll(subMapping.getInputPaths());
        }
        return inputPaths;
    }

    private Path getLocalPath(NodeMapping nodeMapping) {
        NodeMapping ancestor = getAncestorNodeMapping(nodeMapping, nodeMapping.inputPath);
        if (ancestor.inputPath.isAncestorOf(nodeMapping.inputPath)) {
            return nodeMapping.inputPath.extendAncestor(ancestor.inputPath);
        }
        else {
            return nodeMapping.inputPath;
        }
    }

    private NodeMapping getAncestorNodeMapping(NodeMapping nodeMapping, Path path) {
        for (RecDefNode ancestor = nodeMapping.recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping ancestorNodeMappings : ancestor.getNodeMappings().values()) {
                if (ancestorNodeMappings.inputPath.isAncestorOf(path)) return ancestorNodeMappings;
            }
        }
        return new NodeMapping().setInputPath(Path.create("input")).setOutputPath(nodeMapping.outputPath.takeFirst());
    }

    private void trace() {
        if (!trace) return;
        StringBuilder out = new StringBuilder("// CodeGenerator.java trace: ");
        Iterator<StackTraceElement> stack = Arrays.asList(Thread.currentThread().getStackTrace()).iterator();
        stack.next();
        while (stack.hasNext()) {
            StackTraceElement element = stack.next();
            if (!"CodeGenerator.java".equals(element.getFileName())) continue;
            out.append(' ').append(String.valueOf(element.getLineNumber()));
        }
        codeOut.line(out.toString());
    }
}
