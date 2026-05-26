# rdf:about Auto-Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a rec-def's root resource has no user-authored `@rdf:about` mapping, codegen automatically emits `'rdf:about': internalRecordURI()` on that resource, and the URN returned by `internalRecordURI()` matches the URN already written in the trailing record comment (including colon-stripping on the local identifier).

**Architecture:** Two surgical changes inside `sip-core`. First, add a Groovy helper `sanitizeURN` in `MappingCategory.groovy` that maps `:` (and other URN-unfriendly characters) to `-` so the closure URN matches the trailing-comment URN. Switch the generated `internalRecordURI` closure to use `sanitizeURN` instead of `sanitizeURI`. Second, in `CodeGenerator`, compute the auto-injection target (first populated top-level resource child of the rec-def root) once per codegen pass, and have `startBuilderCall` emit an extra `'rdf:about': internalRecordURI()` builder-map entry when that node is being generated and has no user-supplied `rdf:about` mapping.

**Tech Stack:** Java 21, Maven multi-module, Groovy (mapping DSL), JUnit Jupiter 5.

**Spec:** `docs/superpowers/specs/2026-05-26-rdf-about-auto-inject-design.md`

**Prior commits already on `main`** (do not redo these):
- `01319cba fix(sip-core): require top-level RDF subjects` — adds the validator gate in `MappingResult.collectMissingTopLevelSubjectErrors` and the constant-rewrite path `CodeGenerator.toConstantCode` that recognizes `internalRecordURI()` typed as a constant.
- `93c408dc fix(sip-app): expose internal record URI helper` — registers `internalRecordURI` / `internalRecordURN` as standard mapping functions and makes zero-arg functions insert as `name()` in the UI.

---

## File Structure

**Files to modify:**

- `sip-core/src/main/resources/MappingCategory.groovy` — add `sanitizeURN(Object)` static method.
- `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java` — switch closure to `sanitizeURN`; add auto-injection logic.
- `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java` — update existing helper-closure assertion; add auto-inject tests.

**Files to create:**

- `sip-core/src/test/resources/recdef/rdf-root-recdef.xml` — minimal rec-def with a single RDF-style root (`<root tag="RDF">` declaring `rdf:about` in `<attrs>`, one populated top-level child element). Drives the auto-inject test path.
- `sip-core/src/test/java/eu/delving/test/TestMappingCategory.java` — JUnit test that exercises `MappingCategory.sanitizeURN` through a `GroovyShell` so we test the actual Groovy method.

**Responsibilities:**

- `MappingCategory.sanitizeURN`: pure transform — replace `:`, `/`, space, `_`, `[`, `]`, `\` with `-` and collapse runs of `-`. Keep `sanitizeURI` (percent-encoder) untouched.
- `CodeGenerator`: cache auto-injection target during `generate()`. Inside `startBuilderCall`, when the current `recDefNode` equals the cached target AND no child attr node named `rdf:about` carries a user node-mapping, emit one extra builder-map entry.
- Tests: lock down both the new sanitizer and the generated-code shape.

---

## Task 1: `sanitizeURN` helper in `MappingCategory`

**Files:**
- Create: `sip-core/src/test/java/eu/delving/test/TestMappingCategory.java`
- Modify: `sip-core/src/main/resources/MappingCategory.groovy` (append a new static method)

- [ ] **Step 1: Write the failing JUnit test**

Create `sip-core/src/test/java/eu/delving/test/TestMappingCategory.java` with:

```java
/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.test;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMappingCategory {

    @Test
    public void sanitizeURNReplacesColonsAndCollapsesDashes() throws Exception {
        assertEquals("c-lvd-94", evalSanitizeURN("c:lvd:94"));
        assertEquals("a-b", evalSanitizeURN("a::b"));
        assertEquals("path-with-spaces", evalSanitizeURN("path with spaces"));
        assertEquals("under-score", evalSanitizeURN("under_score"));
        assertEquals("a-b-c", evalSanitizeURN("a/b/c"));
        assertEquals("clean-id", evalSanitizeURN("clean-id"));
    }

    @Test
    public void sanitizeURIIsUnchangedByAdditionOfSanitizeURN() throws Exception {
        // Existing percent-encoding behavior must still work after adding sanitizeURN
        assertEquals("a%20b", evalSanitizeURI("a b"));
        assertEquals("c:lvd:94", evalSanitizeURI("c:lvd:94"));
    }

    private String evalSanitizeURN(String input) throws Exception {
        return (String) runUnderCategory("input.sanitizeURN()", input);
    }

    private String evalSanitizeURI(String input) throws Exception {
        return (String) runUnderCategory("input.sanitizeURI()", input);
    }

    private Object runUnderCategory(String expression, String input) throws Exception {
        CompilerConfiguration config = new CompilerConfiguration();
        ImportCustomizer imports = new ImportCustomizer();
        config.addCompilationCustomizers(imports);
        GroovyShell shell = new GroovyShell(config);
        Binding binding = new Binding();
        binding.setVariable("input", input);
        // Compile MappingCategory.groovy alongside, then run expression inside use(MappingCategory) {}
        Reader categoryReader = new InputStreamReader(
            getClass().getResourceAsStream("/MappingCategory.groovy"));
        shell.evaluate(categoryReader, "MappingCategory.groovy");
        shell.setVariable("input", input);
        return shell.evaluate("use(MappingCategory) { " + expression + " }");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sip-core test -Dtest=TestMappingCategory`

Expected: Compilation succeeds. Tests run. The two `sanitizeURN` assertions fail (method not found / MissingMethodException). The `sanitizeURI` assertions pass.

- [ ] **Step 3: Implement `sanitizeURN` in `MappingCategory.groovy`**

Edit `sip-core/src/main/resources/MappingCategory.groovy`. Just before the closing `}` of the `MappingCategory` class (after the existing `sanitizeURI` method, line ~262), add:

```groovy
    static String sanitizeURN(Object object) {
        if (object == null) return ""
        String text = object.toString()
        // Map URN-unfriendly characters to dash, then collapse runs of dashes.
        text = text.replaceAll('[:/_ \\[\\]\\\\]', '-')
        text = text.replaceAll('-{2,}', '-')
        return text
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sip-core test -Dtest=TestMappingCategory`

Expected: all four assertions pass.

- [ ] **Step 5: Commit**

```bash
git add sip-core/src/main/resources/MappingCategory.groovy \
        sip-core/src/test/java/eu/delving/test/TestMappingCategory.java
git commit -m "$(cat <<'EOF'
feat(sip-core): add sanitizeURN for URN-safe local identifiers

Maps colons, slashes, spaces, underscores, brackets, and backslashes
to '-' and collapses consecutive dashes, matching the form the trailing
record-graph URN already uses (e.g. c:lvd:94 -> c-lvd-94). Leaves the
existing percent-encoding sanitizeURI helper untouched.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Switch `internalRecordURI` closure to use `sanitizeURN`

**Files:**
- Modify: `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java:221`
- Modify: `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java:32-34`

- [ ] **Step 1: Update the assertion in `CodeGeneratorTest.generatedCodeExposesInternalRecordUriHelper` to require the new sanitizer**

Edit `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java`. Replace lines 32-34 (the `assertTrue(code.contains(... sanitizeURI() ...))` block) with:

```java
        assertTrue(code.contains(
                "def internalRecordURI = { -> \"urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURN()}/graph\" }"),
            "Generated mapping code should expose a stable internal record URI helper using URN-safe sanitization");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sip-core test -Dtest=CodeGeneratorTest#generatedCodeExposesInternalRecordUriHelper`

Expected: FAIL — closure still references `sanitizeURI()`.

- [ ] **Step 3: Update `CodeGenerator` to emit `sanitizeURN`**

Edit `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java`. At line 221 the current code is:

```java
        codeOut.line("def internalRecordURI = { -> "
                + "\"urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURI()}/graph\" }");
```

Replace with:

```java
        codeOut.line("def internalRecordURI = { -> "
                + "\"urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURN()}/graph\" }");
```

- [ ] **Step 4: Run all sip-core tests**

Run: `mvn -pl sip-core test`

Expected: all tests pass, including the previously failing assertion. If any other test asserted on the old `sanitizeURI()` text inside this closure, update it the same way and document in the commit.

- [ ] **Step 5: Commit**

```bash
git add sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java \
        sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java
git commit -m "$(cat <<'EOF'
fix(sip-core): use sanitizeURN in internalRecordURI closure

So the URN produced by internalRecordURI() matches the URN that
MappingResult writes in the trailing record-graph comment, including
colon-stripping on the local identifier (c:lvd:94 -> c-lvd-94).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Auto-inject `rdf:about` on first populated top-level resource

**Files:**
- Create: `sip-core/src/test/resources/recdef/rdf-root-recdef.xml`
- Modify: `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java`
- Modify: `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java`

### Step 1: Create the test fixture rec-def

- [ ] **Step 1a: Write `rdf-root-recdef.xml`**

Create `sip-core/src/test/resources/recdef/rdf-root-recdef.xml` with:

```xml
<?xml version="1.0"?>
<record-definition prefix="test" version="0.0.0">
    <namespaces>
        <namespace prefix="test" uri="http://test.example.org/" schema="http://test.example.org/test.xsd"/>
        <namespace prefix="rdf" uri="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/>
        <namespace prefix="crm" uri="http://www.cidoc-crm.org/cidoc-crm/"/>
        <namespace prefix="lrm" uri="https://www.iflastandards.info/ns/lrm/lrmoo/"/>
    </namespaces>

    <attrs>
        <attr tag="rdf:about" uriCheck="true"/>
        <attr tag="rdf:resource"/>
        <attr tag="xml:lang"/>
    </attrs>

    <root tag="RDF">
        <!-- Primary subject; declares rdf:about but has no built-in node-mapping for it -->
        <elem tag="lrm:F3_Manifestation" attrs="rdf:about">
            <elem tag="crm:P102_has_title" attrs="xml:lang"/>
        </elem>

        <!-- Sibling top-level resource also declares rdf:about; not auto-injected -->
        <elem tag="lrm:F2_Expression" attrs="rdf:about">
            <elem tag="crm:P102_has_title" attrs="xml:lang"/>
        </elem>
    </root>
</record-definition>
```

### Step 2: Write the failing auto-inject test

- [ ] **Step 2a: Add new test method to `CodeGeneratorTest`**

Append to `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java` (before the closing `}` of the class). The test populates the first top-level element and asserts the generated Groovy includes the synthetic attribute. It also populates the second resource via a leaf-element mapping and asserts that resource does NOT receive auto-injected rdf:about.

```java
    @Test
    void autoInjectsRdfAboutOnFirstPopulatedTopLevelResource() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode title = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/crm:P102_has_title"));
        assertNotNull(title, "Fixture must contain lrm:F3_Manifestation/crm:P102_has_title");
        title.addNodeMapping(NodeMapping.forConstant("Sample title"));

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains("'rdf:about' : {"),
            "Generated code should open a builder entry for rdf:about");
        assertTrue(code.contains("internalRecordURI()"),
            "Auto-injected rdf:about value should be internalRecordURI()");
    }

    @Test
    void doesNotAutoInjectWhenUserSuppliesRdfAbout() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecDefNode title = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/crm:P102_has_title"));
        assertNotNull(title);
        title.addNodeMapping(NodeMapping.forConstant("Sample title"));

        RecDefNode about = tree.getRecDefNode(
            Path.create("/test:RDF/lrm:F3_Manifestation/@rdf:about"));
        assertNotNull(about, "Fixture must declare rdf:about on lrm:F3_Manifestation");
        about.addNodeMapping(NodeMapping.forConstant("http://example.org/user-supplied"));

        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        assertTrue(code.contains("http://example.org/user-supplied"),
            "Generated code should contain the user-authored rdf:about value");

        int helperOccurrencesInAttribute = countSubstring(
            code, "'rdf:about' : {\n                                                                            internalRecordURI()");
        assertTrue(helperOccurrencesInAttribute == 0,
            "Auto-inject must not fire when the user already maps rdf:about");
    }

    @Test
    void doesNotAutoInjectWhenRecDefHasNoMappings() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/recdef/rdf-root-recdef.xml");
        assertNotNull(stream, "Test fixture recdef/rdf-root-recdef.xml must exist");

        RecDefTree tree = RecDefTree.create(RecDef.read(stream));
        RecMapping recMapping = RecMapping.create(tree);
        recMapping.setFact("orgId", "datahub");
        recMapping.setFact("spec", "brocade-cat-lh");

        String code = new CodeGenerator(recMapping).toRecordMappingCode();

        // No populated top-level child means no resource is being emitted at all,
        // so the auto-inject path must stay off.
        assertTrue(!code.contains("'rdf:about' : {"),
            "Auto-inject must not fire when nothing under the root is populated");
    }

    private static int countSubstring(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
```

Note: indentation inside the generated Groovy output is sensitive to `CodeOut`. The third test's negative assertion only checks that no auto-injected entry appears (no `'rdf:about' : {` at all when nothing is populated). The "user supplied" test relies on the user-authored value appearing literally.

### Step 3: Run the new tests to verify they fail

- [ ] **Step 3a: Run tests**

Run: `mvn -pl sip-core test -Dtest=CodeGeneratorTest`

Expected:
- `autoInjectsRdfAboutOnFirstPopulatedTopLevelResource` FAILS (no `'rdf:about' : {` in the output).
- `doesNotAutoInjectWhenUserSuppliesRdfAbout` may PASS already (user value is emitted by the existing pipeline).
- `doesNotAutoInjectWhenRecDefHasNoMappings` PASSES already.
- Previously-passing tests still pass.

### Step 4: Implement auto-inject in `CodeGenerator`

- [ ] **Step 4a: Add a field caching the auto-inject target**

Edit `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java`. After the existing field declarations near the top of the class (around line 30-34), add:

```java
    private RecDefNode autoRdfAboutTarget;
```

- [ ] **Step 4b: Compute the target during `generate()`**

Locate the `generate()` method. Just before `codeOut.line("// DSL Category wraps Builder call:")` (around line 252), insert:

```java
        autoRdfAboutTarget = findAutoRdfAboutTarget(recDefTree);
```

- [ ] **Step 4c: Add the helper method `findAutoRdfAboutTarget`**

Below `generate()` and above `toElementCode` (around line 270), add:

```java
    /**
     * Identify the first populated top-level resource under the rec-def root that
     * declares an rdf:about attribute but has no user-authored mapping for it.
     * That node will receive an auto-injected `'rdf:about': internalRecordURI()` entry
     * inside startBuilderCall. Returns null when no such node exists, which keeps the
     * generator a no-op for rec-defs that do not need this behaviour.
     */
    private RecDefNode findAutoRdfAboutTarget(RecDefTree tree) {
        if (tree == null) return null;
        RecDefNode root = tree.getRoot();
        if (root == null) return null;
        for (RecDefNode child : root.getChildren()) {
            if (child.isAttr()) continue;
            if (!child.isPopulated()) continue;
            RecDefNode aboutAttr = findChildAttr(child, "rdf:about");
            if (aboutAttr == null) return null; // schema does not allow it here; let the validator flag it
            if (!aboutAttr.getNodeMappings().isEmpty()) return null; // user already supplies it
            return child;
        }
        return null;
    }

    private RecDefNode findChildAttr(RecDefNode parent, String attrTag) {
        for (RecDefNode sub : parent.getChildren()) {
            if (!sub.isAttr()) continue;
            if (attrTag.equals(sub.getTag().toString())) {
                return sub;
            }
        }
        return null;
    }
```

- [ ] **Step 4d: Emit the synthetic builder entry inside `startBuilderCall`**

Locate `startBuilderCall` (around line 485). The current shape is:

```java
        if (!recDefNode.hasActiveAttributes()) {
            codeOut.line_("%s%s { %s",
                    prefixFirstBuilder, recDefNode.getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : ""
            );
        }
        else {
            Tag tag = recDefNode.getTag();
            boolean comma = false;
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (!sub.isAttr()) continue;
                // ... existing per-attribute emission ...
            }
            codeOut._line(") { %s", absentFalse ? ABSENT_IS_FALSE : "").in();
        }
```

The auto-injection must work whether or not the node has *other* active attributes. Two adjustments are required:

1. In the *no-active-attributes* branch, if this is the auto-inject target, fall through to the attribute branch instead.
2. In the *attribute branch*, before the loop emits its first entry, if this is the auto-inject target, emit the synthetic `'rdf:about'` entry first.

Replace the entire body of `startBuilderCall` after `trace();` with:

```java
        boolean injectRdfAbout = recDefNode == autoRdfAboutTarget;
        if (!recDefNode.hasActiveAttributes() && !injectRdfAbout) {
            codeOut.line_("%s%s { %s",
                    prefixFirstBuilder, recDefNode.getTag().toBuilderCall(), absentFalse ? ABSENT_IS_FALSE : ""
            );
        }
        else {
            Tag tag = recDefNode.getTag();

            boolean comma = false;
            if (injectRdfAbout) {
                trace();
                codeOut.line("// auto-injected rdf:about (fallback to internalRecordURI())");
                codeOut.line_("%s (", tag.toBuilderCall());
                codeOut.line_("'rdf:about' : {");
                codeOut.line("internalRecordURI()");
                codeOut._line("}");
                comma = true;
            }
            for (RecDefNode sub : recDefNode.getChildren()) {
                if (!sub.isAttr()) continue;
                OptBox subBox = sub.getOptBox();
                if (subBox != null && subBox.role != ROOT && sub.getNodeMappings().isEmpty()) {
                    if (comma) codeOut.line(",");
                    trace();
                    codeOut.line("%s : %s", sub.getTag().toBuilderCall(), sub.getOptBox().getInnerOptReference());
                    comma = true;
                }
                else {
                    for (NodeMapping nodeMapping : sub.getNodeMappings().values()) {
                        if (comma) codeOut.line(",");
                        trace();

                        Boolean isNotEmpty = sub.getTag().toString().equals("xml:lang");

                        if (!injectRdfAbout) {
                            codeOut.line_("%s (", tag.toBuilderCall());
                        }
                        codeOut.line_("%s : {", sub.getTag().toBuilderCall());
                        if (isNotEmpty) {
                            codeOut.line("if (%s && %s != \"\") {", groovyParams.peek(), groovyParams.peek());
                            codeOut.in();
                        }
                        codeOut.start(nodeMapping);
                        toAttributeCode(nodeMapping, groovyParams);
                        codeOut.end(nodeMapping);
                        if (isNotEmpty) {
                            codeOut._line("}");
                        }
                        codeOut._line("}");

                        comma = true;
                    }
                }
            }
            codeOut._line(") { %s", absentFalse ? ABSENT_IS_FALSE : "").in();
        }
        prefixFirstBuilder = ""; // no longer first
```

Note: when `injectRdfAbout` is true, the builder-call opening `tag.toBuilderCall() (` is emitted once by the injection block; subsequent attribute entries must NOT re-open it, hence the `if (!injectRdfAbout)` guard around the `tag.toBuilderCall() (` line in the per-attribute loop. The trailing `)` is unchanged.

### Step 5: Run the auto-inject tests

- [ ] **Step 5a: Run targeted tests**

Run: `mvn -pl sip-core test -Dtest=CodeGeneratorTest`

Expected: all three new tests pass, plus the previously-passing tests.

### Step 6: Run the full sip-core suite

- [ ] **Step 6a: Run all sip-core tests**

Run: `mvn -pl sip-core test`

Expected: all tests green. If `JenaHelperTest` fails because the previously-missing `rdf:about` is now auto-injected, update the affected fixture(s) to reflect the new contract — but generally the validator gate operates on already-built DOMs, not on CodeGenerator output, so this should not regress.

### Step 7: Commit

- [ ] **Step 7a: Commit**

```bash
git add sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java \
        sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java \
        sip-core/src/test/resources/recdef/rdf-root-recdef.xml
git commit -m "$(cat <<'EOF'
feat(sip-core): auto-inject rdf:about on root resource when unmapped

When the first populated top-level child of a rec-def root declares
rdf:about but has no user-authored node-mapping for it, CodeGenerator
now emits 'rdf:about': internalRecordURI() on that element. User-
authored mappings always win; rec-defs that do not declare rdf:about
on that element are left alone (the existing validator gate in
MappingResult will flag them).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Manual integration verification with a real CRM/LRM mapping

This task is a manual check, not a code change. It confirms the chain works end-to-end before declaring the feature done.

**Files:** none modified.

- [ ] **Step 1: Identify a CRM/LRM mapping locally**

Search for a sample CRM/LRM dataset:

```bash
ls ~/DelvingSIPCreator 2>/dev/null
find ~/DelvingSIPCreator -name "mapping_*.xml" 2>/dev/null | head
```

Expected: one or more dataset directories with `mapping_<prefix>.xml`. If none exists for a CRM/LRM rec-def, skip Step 2 and rely on the unit tests as primary verification.

- [ ] **Step 2: Run a small batch through the CLI**

If a CRM/LRM dataset exists, process a small subset using the CLI. Substitute `<dataset-dir>` accordingly.

```bash
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
mvn exec:java -pl sip-app \
  -Dexec.mainClass="eu.delving.sip.cli.SIPCLI" \
  -Dexec.args="process --dataset <dataset-dir> --limit 5"
```

Expected: process completes without `Top-level RDF resource ... has no rdf:about` errors.

- [ ] **Step 3: Inspect the output**

Locate the produced output XML in the dataset directory and check the first record's root resource:

```bash
zcat <dataset-dir>/<output-file>.xml.gz | head -100
```

Expected:
- Root resource element (e.g. `<lrm:F3_Manifestation ...>`) carries `rdf:about="urn:<orgId>_<spec>_<sanitizedLocalId>/graph"`.
- Trailing comment URN matches the rdf:about value (modulo the SHA1 `/graph__<hash>` suffix in the comment).
- For an identifier like `c:lvd:94`, both forms show `c-lvd-94`.

- [ ] **Step 4: Note findings in the design doc**

If verification succeeds, append a short note under the "Open Items" section of `docs/superpowers/specs/2026-05-26-rdf-about-auto-inject-design.md`:

```markdown
- 2026-05-26: Manual integration check passed on <dataset>. Root resource carries auto-injected rdf:about; URN matches trailing-comment URN (hash excluded).
```

Commit the design-doc note:

```bash
git add docs/superpowers/specs/2026-05-26-rdf-about-auto-inject-design.md
git commit -m "$(cat <<'EOF'
docs: confirm rdf:about auto-inject works on real CRM/LRM mapping

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If verification fails, revert the design-doc note step and open a follow-up plan that surfaces the new failure mode.

---

## Self-Review

**Spec coverage:**

- §Goal 1 (auto-inject) — Task 3.
- §Goal 2 (user mapping wins) — Task 3 Step 2a `doesNotAutoInjectWhenUserSuppliesRdfAbout`.
- §Goal 3 (URN matches comment) — Tasks 1 + 2.
- §Goal 4 (reactive to mapping state) — Task 3 Step 2a `doesNotAutoInjectWhenRecDefHasNoMappings` + the `isPopulated()` check inside `findAutoRdfAboutTarget`.
- §Already Landed — explicitly listed; no rework.
- §Non-Goals — multi-root and SHA1 not addressed; explicitly out of scope (only first populated child triggers auto-inject).

**Placeholder scan:** No TBD/TODO. All steps include concrete code or commands. The "Manual integration verification" task is the only ambiguous one but each step has a concrete command and an explicit fallback when no CRM/LRM dataset is available locally.

**Type / signature consistency:**

- `sanitizeURN(Object)` defined in Task 1; called as `_uniqueIdentifier.sanitizeURN()` in Task 2 (Groovy-method-call-on-Object — consistent).
- `findAutoRdfAboutTarget(RecDefTree)` and `findChildAttr(RecDefNode, String)` defined in Task 3 Step 4c; called from Step 4b and Step 4c respectively — names match.
- `autoRdfAboutTarget` field referenced in `startBuilderCall` (Step 4d) and assigned in `generate()` (Step 4b) — names match.
- `NodeMapping.forConstant(String)` used in tests and in commit 01319cba — verified to exist.

**Notes on indentation-sensitive assertions:**

The `doesNotAutoInjectWhenUserSuppliesRdfAbout` test uses a substring with a specific run of spaces that the implementer should treat as illustrative — if `CodeOut` indentation differs, simplify the assertion to a less brittle pattern such as: there is no `internalRecordURI()` call inside the generated builder section for `lrm:F3_Manifestation`. The intent of the assertion (no auto-inject when user maps it) is what matters, not the exact spacing.
