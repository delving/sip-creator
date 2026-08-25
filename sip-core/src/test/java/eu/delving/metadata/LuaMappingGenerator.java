/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.metadata;

import static eu.delving.metadata.GroovySnippetToLua.luaString;
import static eu.delving.metadata.StringUtil.tagToVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates a {@code mapping.lua} module from a rec-mapping, for the Lua
 * mapping-engine feasibility spike (Task 8).
 *
 * <p>This is a Lua-emitting mirror of {@link CodeGenerator}'s walk: the same
 * recursion over {@link RecDefTree} ({@code toElementCode} -&gt;
 * {@code toNodeMappingLoop} -&gt; {@code startBuilderCall} -&gt;
 * {@code toUserCode} -&gt; {@code toInnerLoop}), emitting
 * {@code builder}/{@code node}/{@code stdlib} calls from
 * {@code lua-poc/engine/} instead of Groovy builder syntax, with user snippets
 * passed through {@link GroovySnippetToLua}.
 *
 * <p>The emitted module is a function of one argument:
 * <pre>
 *   local map = dofile("mapping.lua")
 *   local rdfxml = map(record_xml_string)
 * </pre>
 *
 * <h2>Deliberate divergences from CodeGenerator, and why</h2>
 * <ul>
 *   <li><b>Multi-attribute elements.</b> {@code CodeGenerator.startBuilderCall}
 *       re-emits {@code 'tag' (} once per mapped attribute (the
 *       {@code codeOut.line_("%s (", tag.toBuilderCall())} inside the per-mapping
 *       loop), which only produces valid Groovy for a single mapped attribute.
 *       This generator emits one {@code b:elem} with an attribute table, which
 *       is what the Groovy would have meant. No golden case has two mapped
 *       attributes on one element, so this divergence is untested by the
 *       goldens — it is recorded, not verified.</li>
 *   <li><b>{@code _absent_} / {@code ifAbsent}.</b> Not emitted; a mapping
 *       carrying {@code ifAbsent} code raises rather than silently dropping the
 *       fallback (spec section 3.7).</li>
 *   <li><b>Option lists and dictionaries</b> (spec section 7.2/7.3) raise:
 *       {@code _optLookup} is host state the spike's Lua engine has no
 *       equivalent for.</li>
 * </ul>
 *
 * <p>Everything it cannot express raises {@link UnsupportedConstructException}
 * naming the construct, so the spike report can count what a real
 * implementation would still owe.
 */
public class LuaMappingGenerator {

    private final RecMapping recMapping;
    private final StringBuilder out = new StringBuilder();
    private int indent;

    public LuaMappingGenerator(RecMapping recMapping) {
        this.recMapping = recMapping;
    }

    // ------------------------------------------------------------ entry points

    /**
     * Reads {@code recdef.xml} + {@code mapping.xml} from a golden case
     * directory (the layout Task 4 established) and generates its Lua module.
     */
    public static String fromCaseDirectory(java.nio.file.Path caseDir) throws Exception {
        RecDef recDef;
        try (InputStream in = Files.newInputStream(caseDir.resolve("recdef.xml"))) {
            recDef = RecDef.read(in);
        }
        RecDefTree recDefTree = RecDefTree.create(recDef);
        RecMapping recMapping;
        try (InputStream in = Files.newInputStream(caseDir.resolve("mapping.xml"))) {
            recMapping = RecMapping.read(in, recDefTree);
        }
        return new LuaMappingGenerator(recMapping).toLua();
    }

    /**
     * CLI: {@code LuaMappingGenerator <caseDir> <outFile>}. Exits 2 with the
     * construct name on a non-T1 mapping, so a shell driver can tally refusals.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: LuaMappingGenerator <caseDir> <outFile>");
            System.exit(1);
            return;
        }
        try {
            String lua = fromCaseDirectory(java.nio.file.Paths.get(args[0]));
            java.nio.file.Path outFile = java.nio.file.Paths.get(args[1]);
            if (outFile.getParent() != null) Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, lua, StandardCharsets.UTF_8);
            System.out.println("GENERATED " + outFile);
        }
        catch (UnsupportedConstructException e) {
            System.out.println("UNSUPPORTED " + e.getConstructName() + " :: " + e.getMessage());
            System.exit(2);
        }
    }

    // ---------------------------------------------------------------- emitter

    private void line(String format, Object... args) {
        for (int walk = 0; walk < indent; walk++) out.append("  ");
        out.append(args.length == 0 ? format : String.format(format, args)).append('\n');
    }

    private void block(String lua) {
        for (String bodyLine : lua.split("\n")) line("%s", bodyLine);
    }

    // ------------------------------------------------------------- generation

    public String toLua() {
        RecDefTree recDefTree = recMapping.getRecDefTree();
        RecDef recDef = recDefTree.getRecDef();

        line("-- Generated by LuaMappingGenerator (Lua mapping-engine feasibility spike, Task 8).");
        line("-- Source mapping prefix: %s, schema version: %s", recMapping.getPrefix(), recMapping.getSchemaVersion());
        line("-- Targets lua-poc/engine/: node.lua (Task 6), stdlib.lua + builder.lua (Task 7).");
        line("-- Do not hand-edit: regenerate with LuaMappingGenerator <caseDir> <outFile>.");
        line("");
        line("local node = require(\"node\")");
        line("local stdlib = require(\"stdlib\")");
        line("local builder = require(\"builder\")");
        line("");
        namespaces(recDef);
        facts();
        helpers();
        line("return function(record_xml)");
        indent++;
        line("local _input = node.parse(record_xml)");
        line("-- CodeGenerator.java:263 boilerplate: _uniqueIdentifier = _input['@id'][0].toString()");
        line("local _uniqueIdentifier = _input:attr(\"id\") or \"\"");
        line("local internalRecordURI = function()");
        line("  return \"urn:\" .. orgId .. \"_\" .. spec .. \"_\" .. stdlib.sanitizeURN(_uniqueIdentifier) .. \"/graph\"");
        line("end");
        line("local internalRecordURN = internalRecordURI");
        line("local b = builder.new(NAMESPACES)");

        if (!recDefTree.getRoot().isPopulated()) {
            throw new UnsupportedConstructException("EmptyMapping", "no populated rec-def root");
        }
        for (NodeMapping nodeMapping : allNodeMappings(recDefTree)) {
            if (nodeMapping.hasDictionary()) {
                throw new UnsupportedConstructException("DictionaryLookup", nodeMapping.outputPath.toString());
            }
        }
        toElementCode(recDefTree.getRoot(), new ArrayDeque<String>());

        line("return b:to_rdfxml()");
        indent--;
        line("end");
        return out.toString();
    }

    private List<NodeMapping> allNodeMappings(RecDefTree tree) {
        List<NodeMapping> nodeMappings = new ArrayList<>();
        tree.getRoot().collectNodeMappings(nodeMappings);
        return nodeMappings;
    }

    private void namespaces(RecDef recDef) {
        line("local NAMESPACES = {");
        indent++;
        if (recDef.namespaces != null) {
            Map<String, String> sorted = new TreeMap<>();
            for (RecDef.Namespace namespace : recDef.namespaces) {
                if (namespace.prefix == null || namespace.uri == null) continue;
                sorted.put(namespace.prefix, namespace.uri);
            }
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                line("[%s] = %s,", luaString(entry.getKey()), luaString(entry.getValue()));
            }
        }
        indent--;
        line("}");
        line("");
    }

    /**
     * Facts become module-level locals, mirroring CodeGenerator's
     * {@code String <name> = '''<value>'''} block. {@code orgId} gets the same
     * {@code "unknown"} default {@code MappingResult} applies (MappingResult.java:228),
     * so {@code internalRecordURI()} is always callable.
     */
    private void facts() {
        Set<String> emitted = new TreeSet<>();
        line("-- Facts (CodeGenerator.java:216-220)");
        line("local FACTS = {");
        indent++;
        for (Map.Entry<String, String> entry : new TreeMap<>(recMapping.getFacts()).entrySet()) {
            line("[%s] = %s,", luaString(entry.getKey()), luaString(entry.getValue() == null ? "" : entry.getValue()));
        }
        indent--;
        line("}");
        line("-- Utils.initFactsNode: every fact key becomes a child node, so a present");
        line("-- but empty fact is a one-element list of \"\", not an absent one.");
        line("local function _flist(name)");
        line("  local v = FACTS[name]");
        line("  if v == nil then return {} end");
        line("  return { v }");
        line("end");
        for (Map.Entry<String, String> entry : recMapping.getFacts().entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || LUA_KEYWORDS.contains(name)) {
                line("-- fact %s skipped: not a usable Lua identifier", luaString(name));
                continue;
            }
            line("local %s = %s", name, luaString(entry.getValue() == null ? "" : entry.getValue()));
            emitted.add(name);
        }
        if (!emitted.contains("orgId")) line("local orgId = \"unknown\"  -- MappingResult.java:228 default");
        if (!emitted.contains("spec")) line("local spec = \"\"");
        line("");
    }

    private static final Set<String> LUA_KEYWORDS = Set.of(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
            "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
            "until", "while");

    /**
     * Runtime helpers the generated code needs that are not part of the Task 6/7
     * engine surface. They live here rather than in {@code stdlib.lua} so the
     * committed engine modules stay exactly as Task 7 verified them.
     */
    private void helpers() {
        line("-- Groovy truth (spec section 4.5): \"\" and an empty list are falsy, unlike Lua.");
        line("local function _truthy(v)");
        line("  if v == nil or v == false or v == \"\" then return false end");
        line("  if type(v) == \"table\" then");
        line("    if v.__gstring then return v.text ~= \"\" end");
        line("    if v.text then return v:text() ~= \"\" end");
        line("    return #v > 0");
        line("  end");
        line("  return true");
        line("end");
        line("");
        line("-- MappingCategory.TupleMap.get: a missing key reads as \"\", never nil.");
        line("local function _tget(m, k)");
        line("  local v = m[k]");
        line("  if v == nil then return \"\" end");
        line("  return v");
        line("end");
        line("");
        line("-- MappingCategory.or (the `|` operator): zips node lists into tuple maps");
        line("-- keyed by GroovyNode.getNodeName(), which is the tagToVariable-normalised");
        line("-- name -- the same key node:name() returns.");
        line("local function _or(a, b)");
        line("  local out = {}");
        line("  local n = #a > #b and #a or #b");
        line("  for i = 1, n do");
        line("    local ma, mb = a[i], b[i]");
        line("    local m");
        line("    if ma ~= nil and type(ma) == \"table\" and ma.__tuple then");
        line("      m = ma");
        line("    else");
        line("      m = { __tuple = true }");
        line("      if ma ~= nil then m[ma:name()] = ma end");
        line("    end");
        line("    if mb ~= nil then m[mb:name()] = mb end");
        line("    out[#out + 1] = m");
        line("  end");
        line("  return out");
        line("end");
        line("");
        line("local function _tuple(...)");
        line("  local lists = {...}");
        line("  local acc = lists[1] or {}");
        line("  for i = 2, #lists do acc = _or(acc, lists[i]) end");
        line("  return acc");
        line("end");
        line("");
        line("-- node['@name'] yields a one-element list, or an empty one (spec section 2.3).");
        line("local function _alist(n, name)");
        line("  local v = n:attr(name)");
        line("  if v == nil then return {} end");
        line("  return { v }");
        line("end");
        line("");
    }

    // ------------------------------------------------- CodeGenerator mirror

    private void toElementCode(RecDefNode recDefNode, Deque<String> params) {
        if (recDefNode.isAttr() || !recDefNode.isPopulated()) return;
        if (recDefNode.getOptBox() != null) {
            throw new UnsupportedConstructException("OptListLookup", recDefNode.getPath().toString());
        }
        if (recDefNode.getNodeMappings().isEmpty()) {
            if (recDefNode.isRootOptNoOptList()) {
                throw new UnsupportedConstructException("OptListLookup", recDefNode.getPath().toString());
            }
            if (!recDefNode.isLeafElem()) {
                toBranchCode(recDefNode, params);
            }
            else if (recDefNode.hasActiveAttributes()) {
                startBuilderCall(recDefNode, params);
                endBuilderCall();
            }
        }
        else {
            for (NodeMapping nodeMapping : recDefNode.getNodeMappings().values()) {
                refuseIfAbsentCode(nodeMapping);
                toNodeMappingLoop(recDefNode, nodeMapping, getLocalPath(nodeMapping), params);
            }
        }
    }

    private void refuseIfAbsentCode(NodeMapping nodeMapping) {
        List<String> groovyCode = nodeMapping.groovyCode;
        if (groovyCode == null) return;
        for (String codeLine : groovyCode) {
            if (StringUtil.IF_ABSENT_PATTERN.matcher(codeLine).matches()) {
                throw new UnsupportedConstructException("IfAbsentFallback", nodeMapping.outputPath.toString());
            }
        }
    }

    private void toNodeMappingLoop(RecDefNode recDefNode, NodeMapping nodeMapping, Path path, Deque<String> params) {
        if (path.isEmpty()) throw new UnsupportedConstructException("EmptyPath");
        if (path.size() == 1) {
            if (recDefNode.isLeafElem()) {
                toLeafCode(recDefNode, nodeMapping, params);
            }
            else {
                toBranchCode(recDefNode, params);
            }
        }
        else if (nodeMapping.hasMap() && path.size() == 2) {
            String mapName = getMapName(nodeMapping);
            if (params.contains(mapName)) {
                toMapNodeMapping(recDefNode, nodeMapping, params);
            }
            else {
                line("for _, %s in ipairs(%s) do", mapName, toMapExpression(nodeMapping, path));
                indent++;
                params.push(mapName);
                toMapNodeMapping(recDefNode, nodeMapping, params);
                params.pop();
                indent--;
                line("end");
            }
        }
        else {
            Operator operator = nodeMapping.getOperator();
            if (path.size() > 2 && operator != Operator.FIRST) operator = Operator.ALL;
            if (operator != Operator.ALL && operator != Operator.FIRST) {
                throw new UnsupportedConstructException("Operator:" + operator.getCodeString());
            }
            String param = toLoopGroovyParam(path);
            if (params.contains(param)) {
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), params);
            }
            else {
                line("for _, %s in ipairs(%s) do", param, toLoopRef(path));
                indent++;
                params.push(param);
                toNodeMappingLoop(recDefNode, nodeMapping, path.withRootRemoved(), params);
                params.pop();
                indent--;
                if (operator == Operator.FIRST) line("break");
                line("end");
            }
        }
    }

    private void toMapNodeMapping(RecDefNode recDefNode, NodeMapping nodeMapping, Deque<String> params) {
        startBuilderCall(recDefNode, params);
        if (recDefNode.isLeafElem()) {
            block(toUserCode(nodeMapping, params));
        }
        else {
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (sub.isAttr()) continue;
                if (sub.isChildOpt()) {
                    throw new UnsupportedConstructException("OptListLookup", sub.getPath().toString());
                }
                toElementCode(sub, params);
            }
        }
        endBuilderCall();
    }

    private void toBranchCode(RecDefNode recDefNode, Deque<String> params) {
        startBuilderCall(recDefNode, params);
        for (RecDefNode sub : recDefNode.getChildren()) {
            if (sub.isAttr()) continue;
            toElementCode(sub, params);
        }
        endBuilderCall();
    }

    private void toLeafCode(RecDefNode recDefNode, NodeMapping nodeMapping, Deque<String> params) {
        if (nodeMapping.hasMap()) throw new UnsupportedConstructException("MapAtLeaf");
        startBuilderCall(recDefNode, params);
        block(toUserCode(nodeMapping, params));
        endBuilderCall();
    }

    /**
     * Mirrors {@code CodeGenerator.startBuilderCall}: resolve this element's
     * mapped attributes, then open the element. In Lua that is a single
     * {@code b:elem(qname, attrs, function(b, el)}; the closing {@code end)}
     * comes from {@link #endBuilderCall()}.
     *
     * <p>Attribute values are emitted as {@code function(b) ... end}, matching
     * builder.lua's calling convention (attribute closures get {@code b} only;
     * only the content closure gets {@code (b, el)}).
     */
    private void startBuilderCall(RecDefNode recDefNode, Deque<String> params) {
        String qname = recDefNode.getTag().toString();
        List<String[]> attributes = new ArrayList<>();
        if (recDefNode == autoRdfAboutTarget()) {
            attributes.add(new String[] { "rdf:about", "return internalRecordURI()" });
        }
        for (RecDefNode sub : recDefNode.getChildren()) {
            if (!sub.isAttr()) continue;
            if (sub.getOptBox() != null) {
                throw new UnsupportedConstructException("OptListLookup", sub.getPath().toString());
            }
            for (NodeMapping nodeMapping : sub.getNodeMappings().values()) {
                refuseIfAbsentCode(nodeMapping);
                attributes.add(new String[] { sub.getTag().toString(), toUserCode(nodeMapping, params) });
            }
        }
        if (attributes.isEmpty()) {
            line("b:elem(%s, nil, function(b, el)", luaString(qname));
        }
        else {
            line("b:elem(%s, {", luaString(qname));
            indent++;
            for (String[] attribute : attributes) {
                line("[%s] = function(b)", luaString(attribute[0]));
                indent++;
                block(attribute[1]);
                indent--;
                line("end,");
            }
            indent--;
            line("}, function(b, el)");
        }
        indent++;
    }

    private void endBuilderCall() {
        indent--;
        line("end)");
    }

    private RecDefNode autoRdfAboutTargetCache;
    private boolean autoRdfAboutResolved;

    /** Mirrors {@code CodeGenerator.findAutoRdfAboutTarget} (CodeGenerator.java:280-303). */
    private RecDefNode autoRdfAboutTarget() {
        if (autoRdfAboutResolved) return autoRdfAboutTargetCache;
        autoRdfAboutResolved = true;
        RecDefNode root = recMapping.getRecDefTree().getRoot();
        if (root == null) return null;
        for (RecDefNode child : root.getChildren()) {
            if (child.isAttr() || !child.isPopulated()) continue;
            RecDefNode aboutAttr = null;
            for (RecDefNode sub : child.getChildren()) {
                if (sub.isAttr() && "rdf:about".equals(sub.getTag().toString())) aboutAttr = sub;
            }
            if (aboutAttr == null) return null;
            if (!aboutAttr.getNodeMappings().isEmpty()) return null;
            autoRdfAboutTargetCache = child;
            return child;
        }
        return null;
    }

    // --------------------------------------------------------- value lowering

    /** Mirrors {@code CodeGenerator.toUserCode}: constant, snippet, or implied value. */
    private String toUserCode(NodeMapping nodeMapping, Deque<String> params) {
        if (nodeMapping.isConstant()) {
            String value = nodeMapping.getConstantValue();
            String trimmed = value == null ? "" : value.trim();
            if ("internalRecordURI".equals(trimmed) || "internalRecordURI()".equals(trimmed)
                    || "internalRecordURN".equals(trimmed) || "internalRecordURN()".equals(trimmed)) {
                return "return internalRecordURI()";
            }
            return "return " + luaString(value == null ? "" : value);
        }
        if (nodeMapping.groovyCode != null) {
            return GroovySnippetToLua.convertBlock(String.join("\n", nodeMapping.groovyCode));
        }
        List<String> loops = new ArrayList<>();
        String value = toInnerLoop(nodeMapping, getLocalPath(nodeMapping), params, loops);
        if (loops.isEmpty()) return "return " + value;
        return guardedValueLoop(nodeMapping, loops, value);
    }

    /**
     * Renders the loops {@code CodeGenerator.toInnerLoop} emits around an
     * implied value ({@code _input.a * { _a -> _a.b * { _b -> "${_b}" } }}).
     *
     * <p><b>Known divergence, made loud rather than silent.</b> Groovy's
     * {@code multiply} collects <em>every</em> iteration's value into a List,
     * which the reference builder then turns into repeated elements (spec
     * section 3.5, {@code calcElementsRequired}). {@code builder.lua}
     * deliberately does not implement element multiplication (see its module
     * header), so this generator can only carry the single-value case. Rather
     * than quietly keep the first value and emit output that is wrong for
     * multi-valued input, the emitted loop <em>raises</em> the moment a second
     * value appears — so a golden case that needs multiplication fails as a Lua
     * error, not as a plausible-looking wrong answer.
     */
    private String guardedValueLoop(NodeMapping nodeMapping, List<String> loops, String value) {
        StringBuilder out = new StringBuilder("local _v = nil\n");
        for (int walk = 0; walk < loops.size(); walk++) {
            out.append("  ".repeat(walk)).append(loops.get(walk)).append('\n');
        }
        String pad = "  ".repeat(loops.size());
        out.append(pad).append("if _v ~= nil then error(\"element multiplication not implemented for ")
                .append(nodeMapping.outputPath.toString()).append("\") end\n");
        out.append(pad).append("_v = ").append(value).append('\n');
        for (int walk = loops.size() - 1; walk >= 0; walk--) {
            out.append("  ".repeat(walk)).append("end\n");
        }
        return out.append("return _v").toString();
    }

    /**
     * Mirrors {@code CodeGenerator.toInnerLoop}, returning the innermost value
     * expression and appending any {@code for} headers it had to open to
     * {@code loops}.
     */
    private String toInnerLoop(NodeMapping nodeMapping, Path path, Deque<String> params, List<String> loops) {
        RecDefNode recDefNode = nodeMapping.recDefNode;
        if (path.isEmpty()) throw new UnsupportedConstructException("EmptyPath");
        if (path.size() == 1) {
            if (recDefNode.hasFunction()) {
                throw new UnsupportedConstructException("MethodCall:" + recDefNode.getFunction());
            }
            if (nodeMapping.hasMap()) return getMapUsage(nodeMapping);
            // CodeGenerator emits "${param}" here: a one-slot GString, so
            // empty-template suppression applies (spec section 3.4).
            String param = toGroovyIdentifier(path.getTag(0));
            return "stdlib.gs({\"\", \"\"}, {stdlib.to_text(" + param + ") or \"null\"})";
        }
        if (recDefNode.isLeafElem()) {
            return toInnerLoop(nodeMapping, path.withRootRemoved(), params, loops);
        }
        String param = toLoopGroovyParam(path);
        if (params.contains(param)) {
            return toInnerLoop(nodeMapping, path.withRootRemoved(), params, loops);
        }
        loops.add("for _, " + param + " in ipairs(" + toLoopRef(path) + ") do");
        params.push(param);
        String value = toInnerLoop(nodeMapping, path.withRootRemoved(), params, loops);
        params.pop();
        return value;
    }

    /** Mirrors {@code CodeGenerator.getMapUsage}: {@code "${_M4['a']} ${_M4['b']}"}. */
    private String getMapUsage(NodeMapping nodeMapping) {
        String mapName = getMapName(nodeMapping);
        StringBuilder parts = new StringBuilder("{\"\"");
        StringBuilder slots = new StringBuilder("{");
        Iterator<Path> walk = nodeMapping.getInputPaths().iterator();
        boolean first = true;
        while (walk.hasNext()) {
            Path path = walk.next();
            if (!first) slots.append(", ");
            slots.append("stdlib.to_text(_tget(").append(mapName).append(", ")
                    .append(luaString(path.peek().toMapKey())).append(")) or \"null\"");
            parts.append(", ").append(walk.hasNext() ? "\" \"" : "\"\"");
            first = false;
        }
        parts.append("}");
        slots.append("}");
        return "stdlib.gs(" + parts + ", " + slots + ")";
    }

    // -------------------------------------------------------- path utilities

    private String getMapName(NodeMapping nodeMapping) {
        return String.format("_M%d", nodeMapping.inputPath.size());
    }

    private String toMapExpression(NodeMapping nodeMapping, Path path) {
        StringBuilder expression = new StringBuilder("_tuple(");
        Iterator<Path> walk = nodeMapping.getInputPaths().iterator();
        while (walk.hasNext()) {
            Path inputPath = walk.next();
            Path loopPath = path.parent().child(inputPath.peek());
            if (loopPath.size() != 2) throw new UnsupportedConstructException("MapPathDepth", loopPath.toString());
            expression.append(toLoopRef(loopPath));
            if (walk.hasNext()) expression.append(", ");
        }
        return expression.append(")").toString();
    }

    private String toLoopGroovyParam(Path path) {
        return toGroovyIdentifier(path.getTag(1));
    }

    /** Mirrors {@code CodeGenerator.toLoopRef}: {@code outer.inner} / {@code outer['@attr']}. */
    private String toLoopRef(Path path) {
        Tag outer = path.getTag(0);
        Tag inner = path.getTag(1);
        if (outer == null || inner == null) throw new UnsupportedConstructException("LoopRef", path.toString());
        if ("facts".equals(outer.toString())) {
            return "_flist(" + luaString(inner.toString()) + ")";
        }
        String outerName = toGroovyIdentifier(outer);
        if (inner.isAttribute()) {
            return "_alist(" + outerName + ", " + luaString(inner.toString()) + ")";
        }
        return outerName + ":get(" + luaString(tagToVariable(inner.toString())) + ")";
    }

    private String toGroovyIdentifier(Tag tag) {
        return StringUtil.toGroovyIdentifier(tag);
    }

    private Path getLocalPath(NodeMapping nodeMapping) {
        NodeMapping ancestor = getAncestorNodeMapping(nodeMapping, nodeMapping.inputPath);
        if (ancestor.inputPath.isAncestorOf(nodeMapping.inputPath)) {
            return nodeMapping.inputPath.extendAncestor(ancestor.inputPath);
        }
        return nodeMapping.inputPath;
    }

    private NodeMapping getAncestorNodeMapping(NodeMapping nodeMapping, Path path) {
        for (RecDefNode ancestor = nodeMapping.recDefNode.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            for (NodeMapping ancestorNodeMapping : ancestor.getNodeMappings().values()) {
                if (ancestorNodeMapping.inputPath.isAncestorOf(path)) return ancestorNodeMapping;
            }
        }
        return new NodeMapping().setInputPath(Path.create("input")).setOutputPath(nodeMapping.outputPath.takeFirst());
    }
}
