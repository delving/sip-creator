# Framed JSON-LD Output Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add "JSONLD FRAMED" output format that produces nested, human-readable JSON-LD using Jena's framing support.

**Architecture:** Modify JenaHelper to accept optional frame parameter, add UI option in OutputFrame, wire up format selection in SipModel and MappingCompileModel.

**Tech Stack:** Java 21, Jena ARQ 3.x, JUnit 5

---

### Task 1: Write Failing Test for Framed JSON-LD

**Files:**
- Create: `sip-core/src/test/java/eu/delving/metadata/JenaHelperTest.java`

**Step 1: Create test file with failing tests**

```java
/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.metadata;

import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JenaHelperTest {

    @Test
    void shouldProduceFramedJsonLd() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                    <rdf:type rdf:resource="http://example.org/Thing"/>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        Map<String, Object> frame = new HashMap<>();
        frame.put("@type", "Thing");
        
        String framed = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_FRAME_PRETTY, frame);
        
        assertNotNull(framed);
        assertTrue(framed.contains("@graph"));
        assertTrue(framed.contains("Test Item"));
    }

    @Test
    void framedDiffersFromCompact() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                    <rdf:type rdf:resource="http://example.org/Thing"/>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        Map<String, Object> frame = new HashMap<>();
        frame.put("@type", "Thing");
        
        String compact = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_COMPACT_PRETTY);
        String framed = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_FRAME_PRETTY, frame);
        
        assertNotEquals(compact, framed);
    }

    @Test
    void shouldReturnCompactJsonLdWhenNoFrame() {
        String rdf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
                <rdf:Description rdf:about="http://example.org/item1">
                    <ex:name>Test Item</ex:name>
                </rdf:Description>
            </rdf:RDF>
            """;
        
        String result = JenaHelper.convertRDF("ex", rdf, RDFFormat.JSONLD_COMPACT_PRETTY);
        
        assertNotNull(result);
        assertTrue(result.contains("Test Item"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd .worktrees/framed-jsonld && mvn test -pl sip-core -Dtest=JenaHelperTest`
Expected: FAIL - methods not defined yet

---

### Task 2: Implement Framed JSON-LD in JenaHelper

**Files:**
- Modify: `sip-core/src/main/java/eu/delving/metadata/JenaHelper.java`

**Step 1: Add imports and new method overloads**

Add these imports:
```java
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.WriterGraphRIOT;
import org.apache.jena.riot.RDFDataMgr;
import java.util.Map;
```

Add new overloaded methods after existing `convertRDF` method:

```java
public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat, Map<String, Object> frame) {
    if (outputFormat == RDFFormat.RDFXML)
        return rdf;

    if (outputFormat == RDFFormat.JSONLD_FRAME_PRETTY || outputFormat == RDFFormat.JSONLD11_FRAME_PRETTY) {
        return convertRDFWithFrame(defaultPrefix, rdf, outputFormat, frame);
    }

    byte[] out = convertRDFTo(defaultPrefix, rdf, outputFormat);
    if (outputFormat == RDFFormat.JSONLD_COMPACT_PRETTY) {
        return formatJSON(out);
    }
    throw new UnsupportedOperationException("Conversion to " + outputFormat + " is not supported");
}

private static String convertRDFWithFrame(String defaultPrefix, String rdf, RDFFormat outputFormat, Map<String, Object> frame) {
    String compliantRDF = MappingResult.toJenaCompliantRDF(defaultPrefix, rdf);
    InputStream in = new ByteArrayInputStream(compliantRDF.getBytes(StandardCharsets.UTF_8));
    Model model = ModelFactory.createDefaultModel().read(in, null, "RDF/XML");
    
    ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
    
    JsonLDWriteContext ctx = new JsonLDWriteContext();
    ctx.setFrame(frame);
    
    WriterGraphRIOT writer = RDFDataMgr.createGraphWriter(outputFormat);
    PrefixMap prefixes = RiotLib.prefixMap(model.getGraph());
    writer.write(out, model.getGraph(), prefixes, null, ctx);
    
    return formatJSON(out.toByteArray());
}
```

Add helper import:
```java
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
```

**Step 2: Run tests to verify they pass**

Run: `cd .worktrees/framed-jsonld && mvn test -pl sip-core -Dtest=JenaHelperTest`
Expected: PASS

**Step 3: Commit**

```bash
cd .worktrees/framed-jsonld
git add sip-core/src/main/java/eu/delving/metadata/JenaHelper.java
git add sip-core/src/test/java/eu/delving/metadata/JenaHelperTest.java
git commit -m "feat(sip-core): add framed JSON-LD support in JenaHelper"
```

---

### Task 3: Add UI Option in OutputFrame

**Files:**
- Modify: `sip-app/src/main/java/eu/delving/sip/frames/OutputFrame.java:76-82`

**Step 1: Add FRAMED option to dropdown**

In `createOutputPanel()` method, add after line 79:
```java
outputTypes.addElement("JSONLD,FRAMED");
```

**Step 2: Handle FRAMED selection in action listener**

In the action listener (around line 104), add after the JSONLD check:
```java
} else if (selection.contains("FRAMED")) {
    document = mappingModel.setOutputDocument(SyntaxConstants.SYNTAX_STYLE_JSON, outputArea,
            RDFFormat.JSONLD_FRAME_PRETTY);
```

**Step 3: Commit**

```bash
cd .worktrees/framed-jsonld
git add sip-app/src/main/java/eu/delving/sip/frames/OutputFrame.java
git commit -m "feat(sip-app): add JSONLD FRAMED option to output format dropdown"
```

---

### Task 4: Wire Up Format in SipModel

**Files:**
- Modify: `sip-app/src/main/java/eu/delving/sip/model/SipModel.java:484-502`

**Step 1: Add JSONLD_FRAMED case**

Add after line 490:
```java
if ("JSONLD_FRAMED".equals(rdfFormat)) {
    return RDFFormat.JSONLD_FRAME_PRETTY;
}
```

**Step 2: Commit**

```bash
cd .worktrees/framed-jsonld
git add sip-app/src/main/java/eu/delving/sip/model/SipModel.java
git commit -m "feat(sip-app): map JSONLD_FRAMED format property to RDFFormat"
```

---

### Task 5: Pass Frame in MappingCompileModel

**Files:**
- Modify: `sip-app/src/main/java/eu/delving/sip/model/MappingCompileModel.java:480`

**Step 1: Pass default frame when using FRAMED format**

Need to determine how to pass frame. Since we want a default frame, we can either:
1. Create a default frame in JenaHelper when format is FRAMED
2. Pass null and handle in JenaHelper

Option 1 is cleaner - modify the convertRDF call in MappingCompileModel to detect FRAMED format and pass a default frame.

First, let's check the current call:
```java
output = JenaHelper.convertRDF(recMapping.getDefaultPrefix(), output, rdfFormat);
```

Add a helper method in JenaHelper:
```java
public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat) {
    if (outputFormat == RDFFormat.JSONLD_FRAME_PRETTY || outputFormat == RDFFormat.JSONLD11_FRAME_PRETTY) {
        Map<String, Object> defaultFrame = new HashMap<>();
        defaultFrame.put("@type", "Thing");
        return convertRDF(defaultPrefix, rdf, outputFormat, defaultFrame);
    }
    // existing code
}
```

**Step 2: Commit**

```bash
cd .worktrees/framed-jsonld
git add sip-app/src/main/java/eu/delving/sip/model/MappingCompileModel.java
git add sip-core/src/main/java/eu/delving/metadata/JenaHelper.java
git commit -m "feat(sip-app): wire up framed JSON-LD output in MappingCompileModel"
```

---

### Task 6: Verify All Tests Pass

**Step 1: Run all tests**

Run: `cd .worktrees/framed-jsonld && mvn test -pl sip-core,sip-app`
Expected: All tests pass

**Step 2: Commit**

```bash
cd .worktrees/framed-jsonld
git commit --allow-empty -m "chore: verify all tests pass"
```

---

### Task 7: Final Review and Merge

**Step 1: Show diff for review**

Run: `cd .worktrees/framed-jsonld && git diff main...framed-jsonld --stat`

**Step 2: Merge to main**

Run: `cd .worktrees/framed-jsonld && git checkout main && git merge framed-jsonld`
