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

    public String toCode(RecMapping recMapping) {
        CodeOut codeOut = CodeOut.create();
        toCode(recMapping, codeOut, null);
        return codeOut.toString();
    }

    public void toCode(RecMapping recMapping, CodeOut codeOut, EditPath editPath) {
        toCode(recMapping.getRecDefTree(), codeOut, recMapping.getFunctions(), recMapping.getFacts(), editPath);
    }

    private void toCode(RecDefTree recDefTree, CodeOut codeOut, Set<MappingFunction> mappingFunctions, Map<String, String> facts, EditPath editPath) {
        codeOut.line("// SIP-Creator Generated Mapping Code");
        codeOut.line("// ----------------------------------");
        codeOut.line("// Discarding:");
        codeOut.line("import eu.delving.groovy.DiscardRecordException");
        codeOut.line("def discard = { reason -> throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIf = { thing, reason ->  if (thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("def discardIfNot = { thing, reason ->  if (!thing) throw new DiscardRecordException(reason.toString()) }");
        codeOut.line("// Facts:");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            codeOut.line(String.format("String %s = '''%s'''", entry.getKey(), entry.getValue()));
        }
        codeOut.line("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'");
        codeOut.line("// Functions from Mapping:");
        Set<String> names = new TreeSet<String>();
        for (MappingFunction function : mappingFunctions) {
            function.toCode(codeOut);
            names.add(function.name);
        }
        if (recDefTree.getRecDef().functions != null) {
            codeOut.line("// Functions from Record Definition:");
            for (MappingFunction function : recDefTree.getRecDef().functions) {
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
        codeOut.line("_uniqueIdentifier = _input._id[0].toString()");
        codeOut.line("outputNode = output.");
        if (recDefTree.getRoot().isPopulated()) {
            toElementCode(recDefTree.getRoot(), codeOut, new Stack<String>(), editPath);
        }
        else {
            codeOut.line("no 'mapping'");
        }
        codeOut._line("}");
        codeOut.line("outputNode");
        codeOut._line("}");
        codeOut.line("// ----------------------------------");
    }


    private void toElementCode(RecDefNode recDefNode, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (recDefNode.isAttr() || !recDefNode.isPopulated()) return;
        if (editPath != null && !recDefNode.getPath().isFamilyOf(editPath.getNodeMapping().outputPath)) return;
        if (recDefNode.getNodeMappings().isEmpty()) {
            if (recDefNode.isRootOpt()) {
                Set<Path> siblingPaths = getSiblingInputPathsOfChildren(recDefNode);
                if (siblingPaths != null && !siblingPaths.isEmpty()) {
                    NodeMapping nodeMapping = new NodeMapping().setOutputPath(recDefNode.getPath()).setInputPaths(siblingPaths);
                    nodeMapping.recDefNode = recDefNode;
                    codeOut.start(nodeMapping);
                    toNodeMappingLoop(recDefNode, codeOut, nodeMapping, getLocalPath(nodeMapping), groovyParams, editPath);
                    codeOut.end(nodeMapping);
                    return;
                }
            }
            if (!recDefNode.isLeafElem()) {
                toBranchCode(recDefNode, codeOut, groovyParams, editPath);
            }
            else if (recDefNode.hasActiveAttributes()) {
                startBuilderCall(recDefNode, codeOut, false, "R0", groovyParams, editPath);
                codeOut.line("// no node mappings");
                codeOut._line("} // R0");
            }
        }
        else if (editPath != null && editPath.getNodeMapping().recDefNode == recDefNode) {
            NodeMapping nodeMapping = editPath.getNodeMapping();
            codeOut.line("_absent_ = true");
            codeOut.start(nodeMapping);
            toNodeMappingLoop(recDefNode, codeOut, nodeMapping, getLocalPath(nodeMapping), groovyParams, editPath);
            codeOut.end(nodeMapping);
            addIfAbsentCode(recDefNode, codeOut, nodeMapping, groovyParams, editPath);
        }
        else {
            for (NodeMapping nodeMapping : recDefNode.getNodeMappings().values()) {
                codeOut.line("_absent_ = true");
                codeOut.start(nodeMapping);
                toNodeMappingLoop(recDefNode, codeOut, nodeMapping, getLocalPath(nodeMapping), groovyParams, editPath);
                codeOut.end(nodeMapping);
                addIfAbsentCode(recDefNode, codeOut, nodeMapping, groovyParams, editPath);
            }
        }
    }

    private void addIfAbsentCode(RecDefNode recDefNode, CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
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
            startBuilderCall(recDefNode, codeOut, false, "R0a", groovyParams, editPath);
            indentCode(ifAbsentCode, codeOut);
            codeOut._line("} // R0a");
            codeOut._line("}");
        }
    }

    private void toNodeMappingLoop(RecDefNode recDefNode, CodeOut codeOut, NodeMapping nodeMapping, Path path, Stack<String> groovyParams, EditPath editPath) {
        if (path.isEmpty()) throw new RuntimeException("Empty path");
        if (path.size() == 1) {
            if (recDefNode.isLeafElem()) {
                toLeafCode(recDefNode, codeOut, nodeMapping, groovyParams, editPath);
            }
            else {
                toBranchCode(recDefNode, codeOut, groovyParams, editPath);
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            if (groovyParams.contains(getMapName(nodeMapping))) {
                toMapNodeMapping(recDefNode, codeOut, nodeMapping, groovyParams, editPath);
            }
            else {
                codeOut.line_(
                        "%s %s { %s -> // R1",
                        toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
                );
                groovyParams.push(getMapName(nodeMapping));
                toMapNodeMapping(recDefNode, codeOut, nodeMapping, groovyParams, editPath);
                groovyParams.pop();
                codeOut._line("} // R1");
            }
        }
        else { // path should never be empty
            Operator operator = nodeMapping.getOperator();
            if (path.size() > 2 && operator != Operator.FIRST) operator = Operator.ALL;
            String param = toLoopGroovyParam(path);
            if (groovyParams.contains(param)) {
                toNodeMappingLoop(recDefNode, codeOut, nodeMapping, path.withRootRemoved(), groovyParams, editPath);
            }
            else {
                codeOut.line_(
                        "%s %s { %s -> // R6",
                        toLoopRef(path), operator.getCodeString(), param
                );
                groovyParams.push(param);
                toNodeMappingLoop(recDefNode, codeOut, nodeMapping, path.withRootRemoved(), groovyParams, editPath);
                groovyParams.pop();
                codeOut._line("} // R6");
            }
        }
    }

    private void toMapNodeMapping(RecDefNode recDefNode, CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
        if (recDefNode.isLeafElem()) {
            startBuilderCall(recDefNode, codeOut, true, "R3", groovyParams, editPath);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R3");
        }
        else {
            startBuilderCall(recDefNode, codeOut, true, "R4", groovyParams, editPath);
            codeOut.start(nodeMapping);
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    codeOut.line(
                            "%s '%s' // R5",
                            sub.getTag().toBuilderCall(), sub.getOptBox()
                    );
                }
                else {
                    toElementCode(sub, codeOut, groovyParams, editPath);
                }
            }
            codeOut.end(nodeMapping);
            codeOut._line("} // R4");
        }
    }

    private void toBranchCode(RecDefNode recDefNode, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        startBuilderCall(recDefNode, codeOut, false, "R8", groovyParams, editPath);
        for (RecDefNode sub : recDefNode.getChildren()) {
            if (sub.isAttr()) continue;
            if (sub.isChildOpt()) {
                codeOut.line(
                        "%s '%s' // R9",
                        sub.getTag().toBuilderCall(), sub.getOptBox()
                );
            }
            else {
                toElementCode(sub, codeOut, groovyParams, editPath);
            }
        }
        codeOut._line("} // R8");
    }

    private void toLeafCode(RecDefNode recDefNode, CodeOut codeOut, NodeMapping nodeMapping, Stack<String> groovyParams, EditPath editPath) {
        if (nodeMapping.hasMap()) {
            codeOut.line_(
                    "%s %s { %s -> // R10",
                    toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
            );
            startBuilderCall(recDefNode, codeOut, true, "R11", groovyParams, editPath);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R11");
            codeOut._line("} // R10");
        }
        else {
            startBuilderCall(recDefNode, codeOut, true, "R12", groovyParams, editPath);
            codeOut.start(nodeMapping);
            toLeafElementCode(nodeMapping, codeOut, groovyParams, editPath);
            codeOut.end(nodeMapping);
            codeOut._line("} // R12");
        }
    }

    private void startBuilderCall(RecDefNode recDefNode, CodeOut codeOut, boolean absentFalse, String comment, Stack<String> groovyParams, EditPath editPath) {
        if (recDefNode.hasActiveAttributes()) {
            Tag tag = recDefNode.getTag();
            codeOut.line_(
                    "%s ( // %s%s",
                    tag.toBuilderCall(), comment, recDefNode.isRootOpt() ? "(opt)" : ""
            );
            boolean comma = false;
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (!sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    if (comma) codeOut.line(",");
                    OptBox dictionaryOptBox = sub.getDictionaryOptBox();
                    if (dictionaryOptBox != null) {
                        if (recDefNode.getNodeMappings().size() == 1) {
                            NodeMapping nodeMapping = recDefNode.getNodeMappings().values().iterator().next();
                            codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                            toDictionaryCode(nodeMapping, codeOut, groovyParams, sub.getOptBox().role);
                            codeOut._line("}");
                        }
                        else { // this is actually a kind of error:
                            codeOut.line("%s : '%s' // %sc", sub.getTag().toBuilderCall(), sub.getOptBox(), comment);
                        }
                    }
                    else {
                        codeOut.line("%s : '%s' // %sc", sub.getTag().toBuilderCall(), sub.getOptBox(), comment);
                    }
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.getNodeMappings().values()) {
                        if (comma) codeOut.line(",");
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        codeOut.start(nodeMapping);
                        toAttributeCode(nodeMapping, codeOut, groovyParams, editPath);
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
                    "%s { %s // %s%s",
                    recDefNode.getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : "", comment, recDefNode.isRootOpt() ? "(opt)" : ""
            );
        }
    }

    private void toUserCode(NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
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
        toInnerLoop(nodeMapping, codeOut, getLocalPath(nodeMapping), groovyParams, OptRole.ROOT);
    }

    private void toInnerLoop(NodeMapping nodeMapping, CodeOut codeOut, Path path, Stack<String> groovyParams, OptRole optRole) {
        RecDefNode recDefNode = nodeMapping.recDefNode;
        if (path.isEmpty()) throw new RuntimeException();
        if (path.size() == 1) {
            OptBox dictionaryOptBox = nodeMapping.recDefNode.getDictionaryOptBox();
            optRole = optRole == OptRole.ROOT ? OptRole.VALUE : optRole;
            if (dictionaryOptBox != null) {
                codeOut.line(
                        "lookup%s_%s(%s)",
                        dictionaryOptBox.getDictionaryName(), optRole.getFieldName(), toLeafGroovyParam(path)
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
            toInnerLoop(nodeMapping, codeOut, path.withRootRemoved(), groovyParams, optRole);
        }
        else {
            boolean needLoop;
            if (nodeMapping.hasMap()) {
                needLoop = !groovyParams.contains(getMapName(nodeMapping));
                if (needLoop) {
                    codeOut.line_(
                            "%s %s { %s -> // N0a",
                            toMapExpression(nodeMapping), nodeMapping.getOperator().getCodeString(), getMapName(nodeMapping)
                    );
                }
            }
            else {
                String param = toLoopGroovyParam(path);
                needLoop = !groovyParams.contains(param);
                if (needLoop) {
                    codeOut.line_(
                            "%s %s { %s -> // N0b",
                            toLoopRef(path), nodeMapping.getOperator().getCodeString(), param
                    );
                }
            }
            toInnerLoop(nodeMapping, codeOut, path.withRootRemoved(), groovyParams, optRole);
            if (needLoop) codeOut._line("} // N0");
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

    private void toAttributeCode(NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (!nodeMapping.recDefNode.isAttr()) return;
        toUserCode(nodeMapping, codeOut, groovyParams, editPath);
    }

    private void toLeafElementCode(NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, EditPath editPath) {
        if (nodeMapping.recDefNode.isAttr() || !nodeMapping.recDefNode.isLeafElem()) return;
        toUserCode(nodeMapping, codeOut, groovyParams, editPath);
    }

    private void toDictionaryCode(NodeMapping nodeMapping, CodeOut codeOut, Stack<String> groovyParams, OptRole optRole) {
        toInnerLoop(nodeMapping, codeOut, getLocalPath(nodeMapping), groovyParams, optRole);
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

}
