# Mapping Language Core

Engine-neutral specification of the SIP-Creator mapping language.

**Status:** descriptive. Every rule below is derived from the current reference
implementation (Groovy on the JVM) and is cited `file:line`. Where the reference
implementation's behaviour is surprising, the surprise is specified, not fixed —
a second engine that "improves" on it would fail the golden-output comparison
(Task 4/5).

**Audience:** Tasks 5 (canonical verifier), 6 (Lua node navigation), 7 (Lua T1
stdlib + builder) and 8 (converter + codegen + Go host) implement against the
section names used here: **Navigation**, **Builder output**, **Stdlib**,
**Null propagation**, **Discard & errors**, **Facts & lookups**,
**URIs & identifiers**, **Lua ecosystem survey**.

**Normative keywords:** MUST / MUST NOT / SHOULD / MAY as in RFC 2119. "The
engine" means any implementation of this spec (Groovy today, Lua under
evaluation).

---

## 0. Scope and coverage claims

Coverage is grounded in the canonical (deduplicated) corpus measured in Task 2
(`lua-poc/analysis/TIER_TABLE.md`, `lua-poc/analysis/corpus-report-canonical.json`):
2,241 canonical mappings, 84,698 Groovy snippets.

| Tier | Canonical count | Canonical % |
|------|----------------:|------------:|
| T1 (trivially portable) | 57,153 | **67.48%** |
| T2 (moderate) | 15,564 | **18.38%** |
| T3 (exotic) | 11,981 | **14.15%** |

Mappings in which *every* snippet is T1: **134 of 2,241 (5.98%)**.

Two consequences that later tasks MUST NOT paper over:

1. **T1 alone is ~67% of snippets, not ~90%.** Older zig-era estimates claiming
   ~90% are superseded; `TIER_TABLE.md` explains why the all-versions run
   (61.65% T1) and the canonical run (67.48% T1) differ and why the canonical
   numbers are the ones to cite.
2. **A T1-only engine converts ~6% of mappings end-to-end.** Per-snippet T1
   coverage is the interesting spike metric; per-mapping coverage is the
   product-relevant one, and it is small. The spike's success criterion is
   "does the Lua engine reproduce byte-identical output for the T1 subset",
   not "does it replace Groovy".

This spec covers the **T1 subset plus the structural machinery every mapping
needs regardless of tier** (Navigation, Builder output, Facts & lookups,
Discard & errors, URIs & identifiers). T2 constructs that appear in the Task 2
top-20 (closures, list literals, `each`/`collect`/`findAll`, regex matching,
spread) are specified for semantics only and marked
**[spike: out of scope, sub-project 2]**.

---

## 1. Value model

The mapping language has five runtime value kinds. An engine MUST provide all
five, distinguishably.

| Kind | Reference implementation | Notes |
|------|--------------------------|-------|
| **Node** | `eu.delving.groovy.GroovyNode` (`sip-core/src/main/java/eu/delving/groovy/GroovyNode.java:43`) | XML element: name, attributes, text, parent, children |
| **NodeList** | `groovy.util.NodeList` (a `java.util.ArrayList` subclass), returned by `GroovyNode.getByName` (`GroovyNode.java:183-189`) | ordered, possibly empty; **`toString()` is `ArrayList`'s — `[a, b]`** (verified: `groovy.util.NodeList` in groovy 4.0.13 declares no `toString`) |
| **String** | `java.lang.String` | |
| **Template string** | `groovy.lang.GString` | carries its interpolated values separately from the literal skeleton; this distinction is load-bearing — see **Builder output** §3.4 |
| **Absent** | Java `null`, plus the empty-string sentinel `""` | see **Null propagation** |

Numbers and booleans exist but are incidental: mappings compute them and
immediately stringify. The engine MUST render integers without a decimal point
(`5`, not `5.0`) — Lua 5.1's single number type makes this an active hazard.

---

## 2. Navigation

### 2.1 The input tree

One record is one `GroovyNode` tree. The tree is built by the streaming parser
(`sip-app/src/main/java/eu/delving/sip/xml/MetadataParser.java:116-175`), not by
the mapping engine, but the engine MUST accept a tree with exactly these
properties:

- **Root node is synthetic and named `input`** (`MetadataParser.java:120`). It is
  the only node with no parent (`GroovyNode.java:132-134`).
- **The root carries exactly one attribute, `id`** (`MetadataParser.java:129-130`),
  whose value has been passed through `StringUtil.sanitizeId`
  (`sip-core/src/main/java/eu/delving/metadata/StringUtil.java:36-38`:
  `replaceAll("[/_ ]", "-").replaceAll("-{2,}", "-")`). Parsing fails if the
  source record root has no `@id` (`MetadataParser.java:126-128`).
- **Every other node is created with (namespaceUri, localName, prefix)**
  (`MetadataParser.java:132`), i.e. prefixes are preserved.
- **Attributes are keyed by qualified name.** Unprefixed → local name;
  prefixed → `prefix + ":" + localName` (`MetadataParser.java:139-143`). The
  attribute map is a `TreeMap` (`GroovyNode.java:68,72`) so **attribute
  iteration order is lexicographic by qualified name**, not document order.
- **Text is accumulated then trimmed.** Character data is appended with control
  characters stripped (`MetadataParser.java:152` →
  `eu.delving.groovy.Utils.stripNonPrinting`,
  `sip-core/src/main/java/eu/delving/groovy/Utils.java:96-100`, which removes
  `[\p{Cc}&&[^\r\n\t]]`). At element close the accumulated value is `trim()`ed
  (`MetadataParser.java:168`) and, if non-empty, stored via `setNodeValue`
  (`GroovyNode.java:122-130`), which trims **again**.
- **CDATA survives as literal markers in the text.** A CDATA section is appended
  as the string `<![CDATA[...]]>` (`MetadataParser.java:156`). It stays a plain
  substring through the whole mapping and is only re-interpreted at output time
  (see **Builder output** §3.5). An engine MUST NOT strip or unescape it in
  transit.
- **`node.text` is never null after construction** — a null node value yields
  `""` (`GroovyNode.java:122-128`).

### 2.2 Node name normalisation

`GroovyNode.getNodeName()` (`GroovyNode.java:93-96`) returns
`StringUtil.tagToVariable(prefix + localPart)`.

`tagToVariable` (`StringUtil.java:195-209`):
- folds accented Latin characters to ASCII via the `UNICODE`→`PLAIN_ASCII`
  tables (`StringUtil.java:172,185`);
- **deletes every character in `-.;:_`** (`StringUtil.java:183`);
- passes everything else through.

So `<lido:lido>` navigates as `lidolido`, `<dc:title>` as `dctitle`, and
`<some-tag>` as `sometag`. The normalised name is cached on first call
(`GroovyNode.java:94`). An engine MUST reproduce `tagToVariable` exactly,
including the accent table, or navigation silently misses nodes.

### 2.3 The four lookup forms

All four go through `GroovyNode.get(String key)` (`GroovyNode.java:150-163`).
Dispatch is on the *shape of the key*, in this order:

| Key shape | Returns | Reference |
|---|---|---|
| starts with `@` | a **list** containing the attribute's value (a `String`), or an **empty list** if the attribute is absent | `GroovyNode.java:151-156` |
| exactly `*` | the node's `children` list **by reference**, in document order | `GroovyNode.java:157` |
| ends with `_` | the **first descendant** (self included, pre-order) whose normalised name equals the key minus the trailing `_` **and whose text is non-empty**; `""` (empty String, *not* null, *not* a list) when there is no match | `GroovyNode.java:158-161`, `findFirstMatch` at `GroovyNode.java:191-199` |
| anything else | a **NodeList of direct children** whose normalised name matches; empty list if none | `GroovyNode.java:162`, `getByName` at `GroovyNode.java:183-189` |

Four properties of this table are easy to get wrong and MUST be preserved:

1. The `@` form returns a list of **Strings**, never nodes.
2. The `*` form returns the live `children` list; the other forms return fresh
   lists.
3. The `_` form is **recursive** (searches the whole subtree, self first) and
   **skips empty-text matches** — a `<title/>` above a `<title>x</title>` is
   passed over. The other forms are **direct-children-only** and do not filter
   on text.
4. The `_` form's miss value is `""`, a *different type* from the other forms'
   miss value (empty list). This asymmetry is the single largest source of
   accidental behaviour in the corpus. See **Null propagation**.

### 2.4 Property syntax and subscript syntax

`node.foo` is sugar for `node.get("foo")`: the custom metaclass installed on
`GroovyNode` routes **all** property reads through `get`
(`GroovyNode.java:232-239`), and attribute-style reads (`node.@foo`) through
`get("@foo")` (`GroovyNode.java:220-224`). This is why generated code writes
`_input.lidolido` and `_record.pictureurl[0]`.

`node['foo']` and `node['@id']` are the subscript spellings of the same
operation, and the generated preamble depends on it:
`_uniqueIdentifier = _input['@id'][0].toString()`
(`sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java:263`; see the
committed fixture `sip-core/src/test/resources/bulkmapper/generated-groovy/first.groovy:268`).

> **Open question (flag for Task 4's golden harness).**
> `MappingCategory.getAt(GroovyNode, Object)`
> (`sip-core/src/main/resources/MappingCategory.groovy:91-93`) unconditionally
> throws `UnsupportedOperationException`, yet subscript navigation demonstrably
> works in production. The history supports the reading that this category
> method is *not* on the dispatch path for `node['x']` (commit `bde3c27f`
> "Improve performance" replaced a working body — `node.toString()[what]` —
> with the throw, and nothing broke): the subscript resolves through the
> metaclass `getProperty` override at `GroovyNode.java:232-239` instead.
> An engine MUST implement `node['x']` as `node.get('x')`. The golden harness
> SHOULD assert this rather than trust the reading.

### 2.5 Recursive value collection

`getValueNodes(String name)` (`GroovyNode.java:202-216`) walks self-then-children
pre-order and collects **every** node whose normalised name matches **and whose
text is non-empty**. It returns a `NodeList`, possibly empty. It is the
non-first-only sibling of the `_` form.

`getValueNodes` appears 1,693 times in the Task 2 top-20 table, where it is
attributed to T3 purely because the analyzer's fallback rule tags any
unrecognised method call as T3 (`lua-poc/analysis/analyze_corpus.py:105-118`).
The same is true of `other_method:get` (3,560). Both are core Navigation
primitives specified here; the published T1 percentage is therefore a
*conservative* floor, and this spec does not restate it upward.

### 2.6 Other node members an engine MUST provide

| Member | Behaviour | Reference |
|---|---|---|
| `node.text` / `node.text()` | trimmed text, `""` when empty | `GroovyNode.java:52,140-142` |
| `node.toString()` | identical to `text` | `GroovyNode.java:179-181` |
| `node.parent()` | parent node or `null` at the root | `GroovyNode.java:132-134` |
| `node.children` / `node.getChildren()` | ordered child list | `GroovyNode.java:53,136-138` |
| `node.attributes()` | the `TreeMap` of qualified name → value | `GroovyNode.java:85-87` |
| `node.qName()` | the raw `QName` | `GroovyNode.java:89-91` |
| `node.equals(other)` | **compares `text` against `other.toString()`**, gated on an equal hash of the text — nodes are value-equal to their own text, so `node == "abc"` is true | `GroovyNode.java:165-177` |
| `node.hashCode()` | hash of the text at construction time; **`""` hashes to 0 and is never refreshed after `setNodeValue`'s empty branch** | `GroovyNode.java:122-130,174-177` |

`node.equals` is deliberately asymmetric with `String.equals` and MUST be
reproduced: mappings rely on `if (it.get("type_") == "image")` working
(`CodeGenerator.java:121`).

---

## 3. Builder output

### 3.1 Shape of a generated script

`CodeGenerator.generate()` (`CodeGenerator.java:62-274`) emits one script per
mapping, in this fixed order:

1. a long comment block documenting the DSL for humans and AI assistants
   (`CodeGenerator.java:63-206`) — informational, MUST NOT be parsed;
2. discard helpers (`CodeGenerator.java:208-212`) — see **Discard & errors**;
3. `_facts` and `_optLookup` bindings (`CodeGenerator.java:213-214`);
4. one `String <factName> = '''<value>'''` per fact, with `'` escaped
   (`CodeGenerator.java:215-221`);
5. `String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'` placeholder
   (`CodeGenerator.java:222`);
6. `internalRecordURI` / `internalRecordURN` closures
   (`CodeGenerator.java:223-225`) — see **URIs & identifiers**;
7. standard functions from `/functions.groovy`
   (`CodeGenerator.java:237`, `sip-core/src/main/java/eu/delving/groovy/StandardMappingFunctions.java:75-79`);
8. mapping-level then rec-def-level user functions, first definition wins on
   name collision (`CodeGenerator.java:239-254`);
9. dictionaries and `lookupX` closures (`CodeGenerator.java:255-256`) — see
   **Facts & lookups**;
10. the builder body (`CodeGenerator.java:258-272`):

```groovy
boolean _absent_ = true
def outputNode
use (MappingCategory) {
  WORLD.input * { _input ->
    _uniqueIdentifier = _input['@id'][0].toString()
    ...builder calls...
  }
  outputNode
}
```

`WORLD.input` is a **singleton list holding the record root**
(`Utils.java:66`), so the outer `*` runs the body exactly once per record. The
script's value is `outputNode` (`CodeGenerator.java:271`).

### 3.2 Builder calls

Output elements are produced by method calls on a builder object
(`Utils.java:65` binds `WORLD.output` to `DOMBuilder.createFor(recDef)`). Two
call shapes exist:

- `'ns:tag' { ...body... }` — element with content only
  (`CodeGenerator.java:527-531`);
- `'ns:tag' ( 'ns:attr' : { ...value... }, ... ) { ...body... }` — element with
  attributes, each attribute value being a **closure**
  (`CodeGenerator.java:532-582`).

Tag spelling: prefixed tags are emitted single-quoted, unprefixed bare
(`sip-core/src/main/java/eu/delving/metadata/Tag.java:220-227`).

The first builder call in a script is prefixed `outputNode = WORLD.output.`
(`CodeGenerator.java:33`, cleared at `CodeGenerator.java:583`); every subsequent
call is a bare method call resolved against the builder delegate.

### 3.3 Closure-valued content is the core mechanism

`DOMBuilder.ElementFactory.resolveValue`
(`sip-core/src/main/java/eu/delving/groovy/DOMBuilder.java:145-160`):

- a **Closure** value is delegated to the builder and **called**, and its result
  passed through `suppressIfAllVariablesEmpty`;
- a **List** value is resolved element-wise;
- anything else passes through.

This is what makes attribute values able to contain loops and conditionals.

### 3.4 Empty-template suppression (T1-critical)

`suppressIfAllVariablesEmpty` (`DOMBuilder.java:176-186`): if the produced value
is a **GString** with at least one interpolated slot, and **every** slot's
`toString().trim()` is empty, the whole value becomes `null`.

Plain Strings are never suppressed — only template strings. This is why
`"https://example.com/${slug}"` emits nothing when `slug` is empty, while the
literal `"https://example.com/"` always emits.

An engine MUST therefore track, for every interpolated string it builds, the
list of interpolated values separately from the rendered text. A Lua engine that
concatenates eagerly loses this and will emit stub URIs. This is the single
most important structural requirement in this section.

### 3.5 Element multiplication and content rules

`ElementFactory` (`DOMBuilder.java:120-317`):

- `calcElementsRequired` (`DOMBuilder.java:188-214`): the element is emitted
  `max(1, longest attribute list, count of non-Node content items)` times.
- `extractValue(value, i)` (`DOMBuilder.java:251-260`): index `i` of a list; an
  **out-of-range index falls back to index 0** (`DOMBuilder.java:256`), a null
  or empty list yields `null`; scalars are reused for every `i`.
- Attribute values that resolve to `null` are skipped (`DOMBuilder.java:270`).
- Only `String`/`GString` content becomes text (`DOMBuilder.java:290-294`);
  Node content is attached structurally instead.
- Text is written by `toTextNodes` (`DOMBuilder.java:371-390`), which scans for
  the literal `<![CDATA[` / `]]>` markers and converts those spans into real
  CDATA sections. An unterminated `<![CDATA[` throws.
- `xmlns` and `xmlns:*` attributes are rejected outright
  (`DOMBuilder.java:216-219`).
- `xml:lang` is special-cased twice: its value is validated against a BCP 47
  pattern (`DOMBuilder.java:53-55,227-249`, throwing `LanguageTagException` on
  empty or malformed), and it is **only actually set when the element has
  non-empty text content** (`DOMBuilder.java:296-310`).
- Nesting is reconstructed after the fact from a recorded `depth` user-data key
  once the outermost call returns (`DOMBuilder.java:338-369`), with children
  inserted **before** existing first children (`DOMBuilder.java:359`) because
  `allNodes` is reversed first (`DOMBuilder.java:351`).
- Namespace resolution: a prefix with no declaration in the rec-def throws
  `"No namespace for <prefix>"` (`DOMBuilder.java:398-414`); the root element
  gets an `xsi:schemaLocation` built from all declared namespaces that have a
  schema (`DOMBuilder.java:97-106`).

### 3.6 Post-processing

After the script returns, the runner calls `Utils.stripEmptyElements`
(`Utils.java:43-47`, recursion at `Utils.java:70-94`;
callers `sip-core/src/main/java/eu/delving/groovy/AppMappingRunner.java:98` and
`sip-core/src/main/java/eu/delving/groovy/BulkMappingRunner.java:107`). It
removes, depth-first: whitespace-only text/CDATA nodes, and elements that end up
with neither children nor attributes. An unknown DOM node type throws
(`Utils.java:88-90`).

Empty-element stripping is part of the output contract, not a tidy-up: golden
comparison happens after it.

### 3.7 The `_absent_` protocol

`_absent_` is a script-level boolean, reset to `true` before each node mapping
(`CodeGenerator.java:348,356`) and set to `false` inside the builder call that a
node mapping produces (`CodeGenerator.ABSENT_IS_FALSE`, `CodeGenerator.java:29`,
emitted at `CodeGenerator.java:529,581`). User code may append an
`if (_absent_) { ... }` block, which the generator extracts by brace counting
(`getIfAbsentCode`, `CodeGenerator.java:828-845`, matched by
`StringUtil.IF_ABSENT_PATTERN`, `StringUtil.java:40`) and re-emits as a fallback
builder call (`addIfAbsentCode`, `CodeGenerator.java:365-381`).

An engine that generates its own code MUST reproduce this fallback, because the
`if (_absent_)` text lives in user-authored mapping snippets.

### 3.8 Loop operators in generated code

Generated loops use `Operator.getCodeString()`
(`sip-core/src/main/java/eu/delving/metadata/Operator.java:26-33`):
`ALL` → `*`, `FIRST` → `**`, `AS_ARRAY` → `>>`, and the delimiter variants
`* ', ' *`, `* '; ' *`, `* ' ' *`, `* '|' * `. Loops deeper than two path
segments are forced to `ALL` unless the operator is `FIRST`
(`CodeGenerator.java:420`). Semantics of each operator: see **Stdlib** §4.4.

---

## 4. Stdlib

The stdlib is the union of three sources:

- **A.** `MappingCategory` — the DSL category (`MappingCategory.groovy`),
  applied only inside `use (MappingCategory) { ... }`;
- **B.** `GroovyNode`'s own convenience methods (`GroovyNode.java:98-116`),
  which shadow A for node receivers;
- **C.** plain Groovy/JDK `String` methods that mappings use unmodified.

### 4.1 MappingCategory: every static method

`MappingCategory` declares **23** static methods (20 reachable from mapping code,
3 private helpers). A raw `grep -c 'static'` over the file returns 27 because it
also counts the two static `Pattern` fields (`MappingCategory.groovy:34-35`) and
the two static nested classes (`:53`, `:62`); the 23-method enumeration below is
the authoritative one.

| # | Signature | Line | Behaviour (one sentence) | Null-input behaviour (derived) |
|---|-----------|------|--------------------------|-------------------------------|
| 1 | `String replaceAll(String s, String regex, String replacement)` | :37 | Replaces every `java.util.regex` match in `s`. | `s == null` → the category is never reached (method call on null throws `NullPointerException` at the call site); `regex == null` → `Pattern.compile(null)` NPE inside `PatternCache.getPattern` (`PatternCache.java:29-36`); `replacement == null` → `Matcher.replaceAll(null)` NPE. |
| 2 | `String[] split(String s, String regex)` | :41 | Splits on a regex, JDK trailing-empty-removal semantics. | as #1. |
| 3 | `String[] split(String s, String regex, int limit)` | :45 | Splits with an explicit limit. | as #1. |
| 4 | `boolean matches(String s, String regex)` | :49 | Whole-string regex match. | as #1. |
| 5 | `List flatten(List input, List flattenedList)` *(private)* | :69 | Appends `input`'s leaves to `flattenedList`, recursing into nested lists. | `input == null` → NPE on iteration; null *elements* are appended as-is. |
| 6 | `List flatten(List input)` *(private)* | :79 | One-shot flatten into a fresh `NodeList`. | `input == null` → NPE at `input.size()`. |
| 7 | `boolean asBoolean(List list)` | :85 | Truth of a list: empty → `false`; a single element that is itself a list → that list's truth; otherwise `true`. | `list == null` → never reached (Groovy treats a null receiver as false before dispatch). Note `[[]]` is **false** and `[null]` is **true**. |
| 8 | `String getAt(GroovyNode node, Object what)` | :91 | Throws `UnsupportedOperationException` unconditionally. | n/a — see the open question in **Navigation** §2.4. |
| 9 | `int indexOf(GroovyNode node, String string)` | :95 | `node.text.indexOf(string)`. | `node == null` → NPE at call site; `string == null` → `String.indexOf(null)` NPE. `node.text` is never null. |
| 10 | `String substring(GroovyNode node, int from)` | :99 | `node.text.substring(from)`. | out-of-range `from` → `StringIndexOutOfBoundsException`. |
| 11 | `String substring(GroovyNode node, int from, int to)` | :103 | `node.text.substring(from, to)`. | as #10. |
| 12 | `Object plus(List a, List b)` | :108 | Operator `+`: flattens both and concatenates into a new `NodeList`. | either null → NPE inside `flatten`. |
| 13 | `Object or(List a, List b)` | :116 | Operator `\|`: zips two flattened lists into a `TupleList` of `TupleMap`s keyed by each node's `getNodeName()`; the longer list's tail is wrapped in single-entry maps. | either null → NPE inside `flatten`; a null *element* on the `b` side → NPE at `mb.getNodeName()` (`:127`); on the `a` side a null non-Map element → NPE at `:146`. |
| 14 | `List multiply(List a, Closure closure)` | :163 | Operator `*`: calls the closure once per flattened element and accumulates truthy results — arrays and lists are spread, Strings appended, `org.w3c.dom.Node` results **dropped**, everything else `toString()`ed. | `a == null` → NPE; a null closure result is falsy and skipped (`:169`). |
| 15 | `List multiply(List a, String delimiter)` | :188 | Operator `*` with a String: splits each node's text on the literal delimiter into synthetic sibling nodes. | `a == null` → NPE; **null elements and null texts are skipped** (`:198`) — the only member of this family that tolerates nulls; `delimiter == null` → NPE at `Pattern.quote(null)`. |
| 16 | `List<GroovyNode> splitNodes(List<GroovyNode>, String)` *(private)* | :194 | Implementation of #15; nodes whose text lacks the delimiter pass through unchanged, and blank segments are dropped (`:201`). | see #15. |
| 17 | `Object power(List a, Closure closure)` | :214 | Operator `**`: calls the closure on the **first** flattened element only; **always returns `null`**. | `a == null` → NPE; empty list → closure never called, returns `null`. |
| 18 | `Object rightShift(List a, Closure closure)` | :224 | Operator `>>`: calls the closure **once** with the whole flattened list; **always returns `null`**. | `a == null` → NPE; empty list → closure still called with `[]`. |
| 19 | `String sanitize(GroovyNode node)` | :230 | `sanitize(node.toString())`. | `node == null` → NPE at call site. |
| 20 | `String sanitize(List list)` | :234 | `sanitize(list.toString())` — note this stringifies the **whole list**, brackets included (`[a, b]`). | `list == null` → NPE at call site. |
| 21 | `String sanitize(String text)` | :238 | Collapses newlines to spaces then runs of spaces to one space. Does **not** trim, and does **not** strip apostrophes (unlike `CodeGenerator.sanitizeGroovy`, `CodeGenerator.java:822-826`). | `text == null` → NPE at the regex-find. |
| 22 | `String sanitizeURI(Object object)` | :244 | Percent-encodes **only** space→`%20`, `[`→`%5B`, `]`→`%5D`, `\`→`%5C`; every other character passes through unencoded. | **`object == null` → NPE at `object.toString()`** (`:246`). Contrast #23. |
| 23 | `String sanitizeURN(Object object)` | :267 | **Returns `""` for null**, else replaces each of `: / _ space [ ] \` with `-` and collapses `-` runs. | **null-safe by explicit guard** (`:268`). |

Nested helpers:

- `TupleMap extends TreeMap` (`:53-60`) — **`get` returns `""` instead of `null`
  for a missing key** (`:56-58`). This is what makes `"${_M2['title']}"`
  render as empty rather than `null` for absent tuple members.
- `TupleList extends ArrayList` (`:62-67`) — `toString()` is `"TUPLE"` +
  the ArrayList rendering.

All regexes go through `PatternCache` (`PatternCache.java:25-36`), a
`Collections.synchronizedMap`-wrapped `HashMap` keyed on the pattern string.
The cache is unbounded and never evicts; an engine MAY cache differently but
MUST NOT change match semantics.

### 4.2 GroovyNode's shadowing methods

For a **node** receiver, these are found before any category method
(`GroovyNode.java:98-116`). They all read `node.text`, which is never null.

| Signature | Line | Behaviour | Null-input |
|---|---|---|---|
| `int size()` | :98 | **`text.length()`** — the character count, *not* a child count | n/a |
| `boolean contains(String s)` | :102 | substring test on the text | `s == null` → NPE |
| `String[] split(String s)` | :106 | regex split of the text | `s == null` → NPE in `PatternCache` |
| `boolean endsWith(String s)` | :110 | suffix test on the text | `s == null` → NPE |
| `String replaceAll(String from, String to)` | :114 | regex replace on the text | either null → NPE |

`size()` is a trap worth restating: `node.size()` is a string length. Only
`node.children.size()` is a child count.

### 4.3 Plain String methods used by T1 mappings

These have no SIP-Creator implementation; they are JDK/Groovy semantics and an
engine MUST match them exactly. They are listed because Task 2's
`T1_STRING_METHODS` (`lua-poc/analysis/analyze_corpus.py:27-32`) includes them
and Task 7 must implement all of them.

| Method | Semantics to match | Null-input |
|---|---|---|
| `replace(a, b)` | **literal** (non-regex) replacement — distinct from `replaceAll` | NPE on null arg |
| `capitalize()` | Groovy GDK: upper-cases the first character only, leaves the rest alone; `""` → `""` | NPE on null receiver |
| `trim()` | strips ASCII ≤ U+0020 from both ends | NPE |
| `toLowerCase()` / `toUpperCase()` | **default-locale** case mapping (a Turkish-locale JVM changes `I`/`i`) — an engine SHOULD pin to root locale and record the deviation | NPE |
| `toString()` | identity for Strings; text for nodes (`GroovyNode.java:179`); `[a, b]` for NodeLists (§1) | NPE |
| `toInteger()` | Groovy GDK `Integer.valueOf` after trimming; throws `NumberFormatException` on junk | NPE |
| `indexOf(s)` | `-1` when absent | NPE on null arg |
| `contains(s)`, `startsWith(s)`, `endsWith(s)` | substring/prefix/suffix tests | NPE on null arg |
| `isEmpty()` | length zero (does not trim) | NPE |
| `size()` | on a String, length; on a List, element count; on a node, **text length** (§4.2) | NPE |
| `join(sep)` | Groovy GDK on collections; stringifies each element | NPE |

**Regex dialect.** `replaceAll`, `split` and `matches` take
`java.util.regex` patterns, not glob and not Lua patterns. See the
**Lua ecosystem survey** §9.4 for the measured impact of this.

### 4.4 List operators

| Operator | Method | Meaning | Result |
|---|---|---|---|
| `a + b` | `plus` (:108) | concatenate (flattened) | new `NodeList` |
| `a \| b` | `or` (:116) | zip into tuples | `TupleList` of `TupleMap` |
| `list * { c }` | `multiply` (:163) | run closure per element, collect truthy results | `List` **[spike: out of scope, sub-project 2 — closure_literal is T2]** |
| `list * 'delim'` | `multiply` (:188) | split node texts on a literal delimiter | `List<GroovyNode>` |
| `list ** { c }` | `power` (:214) | run closure on first element only | **always `null`** **[spike: out of scope, sub-project 2]** |
| `list >> { c }` | `rightShift` (:224) | run closure once with the whole list | **always `null`** **[spike: out of scope, sub-project 2]** |

`power` and `rightShift` returning `null` is deliberate: they are used for their
side effect (builder calls inside the closure), never for their value.

`flatten` (:69-83) is applied by **every** operator before it does anything, so
nesting depth is never observable across an operator boundary.

### 4.5 Truth and comparison

- **List truth**: `asBoolean` (§4.1 #7) — empty is false, `[[]]` is false,
  `[null]` is true.
- **String truth**: Groovy's rule — `""` is false, any other String is true.
  This matters because the `_` navigation form returns `""` on a miss (§2.3),
  so `if (it.get("title_"))` correctly reads as "there is a title".
- **Node truth**: a non-null node is true regardless of its text (there is no
  `asBoolean` override on `GroovyNode`). `if (node)` and `if (node.text)`
  therefore differ.
- **Equality**: `node == string` compares text (§2.6). `equality` is the 6th
  most common construct in the corpus (23,055 occurrences).

### 4.6 String interpolation

`"${expr}"` produces a **GString**, not a String (§1), and the difference is
observable at the builder boundary (§3.4). Interpolating a node renders its
text; interpolating a NodeList renders `[a, b]`; interpolating `null` renders
`null` (the four-character word) unless empty-template suppression fires first.

`gstring_interpolation` is the 2nd most common construct in the corpus (85,867
occurrences), so an engine's GString analogue is on the hot path.

### 4.7 Standard mapping functions

`/functions.groovy` (`sip-core/src/main/resources/functions.groovy`) is appended
verbatim to every script (`StandardMappingFunctions.java:75-79`). It currently
declares two functions, both marked with a trailing `// #def` comment that the
loader parses to build the GUI's function list
(`StandardMappingFunctions.java:56-73`):

- `calculateAge(birthDate, deathDate, automaticDateReordering = false, ignoreErrors = false)`
  (`functions.groovy:19-71`) — parses both dates as `yyyy-MM-dd`; returns `""`
  for null/empty/`"null"` inputs (`:21-28`) and for computed ages > 130
  (`:67-69`); throws `IllegalArgumentException` on unparseable dates or reversed
  ranges unless the corresponding flag is set.
- `calculateAgeRange(...)` (`functions.groovy:74-91`) — buckets the age into
  decades using an en-dash separator (`" – "`), with `"0 – 10"` and
  `"100 – 130"` edge buckets.

An engine MUST NOT hard-code these: the list is data, read at runtime from the
resource. `internalRecordURI` and `internalRecordURN` are injected into the same
list programmatically (`StandardMappingFunctions.java:32-35,58-59`).

### 4.8 Coverage of the Task 2 top-20 constructs

Self-check required by the task brief. Every row is either covered by a section
above or explicitly deferred.

| # | Construct | Occurrences | Covered by |
|---|-----------|------------:|------------|
| 1 | `string_literal` | 110,089 | §1 Value model |
| 2 | `gstring_interpolation` | 85,867 | §4.6, §3.4 (suppression) |
| 3 | `property_access` | 64,863 | §2.4 |
| 4 | `method:replaceAll` | 31,835 | §4.1 #1, §4.2, §9.4 (regex dialect) |
| 5 | `closure_literal` | 30,003 | §4.4, §3.3 — **T2, spike out of scope** |
| 6 | `equality` | 23,055 | §4.5 |
| 7 | `def_keyword` | 15,572 | T3 — local variable declaration; out of scope |
| 8 | `method:sanitizeURI` | 15,092 | §4.1 #22, §8 |
| 9 | `list_literal` | 9,846 | **T2, spike out of scope** |
| 10 | `other_method:first` | 9,169 | T3 (Groovy GDK `List.first()`); out of scope |
| 11 | `method:split` | 8,820 | §4.1 #2/#3, §4.2 |
| 12 | `method:size` | 5,701 | §4.2 (node = text length!), §4.3 |
| 13 | `each_collect_findall` | 4,890 | **T2, spike out of scope** |
| 14 | `method:toString` | 4,581 | §4.3, §2.6 |
| 15 | `method:capitalize` | 4,235 | §4.3 |
| 16 | `other_method:get` | 3,560 | §2.3 — **core Navigation**, T3 only by analyzer fallback (§2.5) |
| 17 | `method:indexOf` | 2,353 | §4.1 #9, §4.3 |
| 18 | `method:contains` | 1,831 | §4.2, §4.3 |
| 19 | `regex_match` | 1,826 | §4.1 #4 — **T2, spike out of scope** |
| 20 | `other_method:getValueNodes` | 1,693 | §2.5 — **core Navigation**, T3 only by analyzer fallback |

No T1 construct in the top-20 is uncovered.

---

## 5. Null propagation

Groovy `null` and Lua `nil` are not interchangeable, and the mapping language
leans on three *different* absent values. An engine MUST keep them distinct.

### 5.1 The three absent values

| Value | Produced by | Truthy? | Stringifies to |
|---|---|---|---|
| empty list | `get("name")` miss, `get("@attr")` miss (§2.3) | false (`asBoolean`, §4.1 #7) | `[]` |
| `""` | `get("name_")` miss (`GroovyNode.java:160`), `sanitizeURN(null)` (`:268`), `TupleMap` miss (`:56-58`), empty node text (`GroovyNode.java:127`) | false | `""` |
| `null` | closures returning nothing, `power`/`rightShift` (§4.4), suppressed GStrings (§3.4), absent attributes in `extractValue` (`DOMBuilder.java:254-255`) | false | `"null"` if interpolated |

A Lua engine MUST NOT map "empty list" to `nil`: `#t == 0` on a real table is
distinguishable from `nil` and the distinction is observable
(`node.get("x").size()` is `0`, not an error).

### 5.2 Per-function null table

Input `null` → output, for every stdlib entry. "NPE (call site)" means Groovy
never dispatches the method because the receiver is null — the failure surfaces
as a mapping exception, not as a null result.

| Function | null receiver / 1st arg | null later arg |
|---|---|---|
| `replaceAll(s, re, rep)` | NPE (call site) | NPE (`PatternCache.java:32` / `Matcher.replaceAll`) |
| `split(s, re[, n])` | NPE (call site) | NPE (`PatternCache.java:32`) |
| `matches(s, re)` | NPE (call site) | NPE (`PatternCache.java:32`) |
| `asBoolean(list)` | not dispatched — null is falsy | — |
| `getAt(node, x)` | `UnsupportedOperationException` always (`:92`) | same |
| `indexOf(node, s)` | NPE (call site) | NPE (`String.indexOf`) |
| `substring(node, …)` | NPE (call site) | — |
| `plus(a, b)` | NPE in `flatten` (`:80`) | NPE in `flatten` |
| `or(a, b)` | NPE in `flatten` | NPE at `mb.getNodeName()` (`:127`) |
| `multiply(a, closure)` | NPE in `flatten` | closure returning null → element skipped (`:169`) |
| `multiply(a, delim)` | NPE in `flatten` | NPE at `Pattern.quote` (`:195`) |
| `power(a, c)` / `rightShift(a, c)` | NPE in `flatten` | — (return value is always `null` anyway) |
| `sanitize(node/list/text)` | NPE (call site) | — |
| **`sanitizeURI(o)`** | **NPE at `o.toString()` (`:246`)** | — |
| **`sanitizeURN(o)`** | **`""` — explicit null guard (`:268`)** | — |
| `GroovyNode.size/contains/split/endsWith/replaceAll` | NPE (call site); `text` itself is never null (`GroovyNode.java:122-128`) | NPE on null arg |
| `calculateAge` / `calculateAgeRange` | **`""`** for null, empty, or the literal string `"null"` (`functions.groovy:21-28`) | — |
| `lookupX(value)` | **`null`** — explicit falsy guard (`CodeGenerator.java:771,780`) | — |

The asymmetry between `sanitizeURI` (throws) and `sanitizeURN` (returns `""`) is
real and MUST be preserved; it is the difference between a discarded record and
a silently empty URI.

### 5.3 Groovy-null vs Lua-nil hazards

For Task 7. Each row is a place where a naive Lua port diverges.

| Hazard | Groovy | Lua trap | Requirement |
|---|---|---|---|
| absent list vs nil | `[]` (truthy-false, size 0) | `nil` has no `#` | represent absent lists as empty tables |
| `""` vs nil | `""` is a value; `if ("")` is **false** | `if ""` in Lua is **true** | the engine's truth test MUST special-case `""` |
| `0` truth | Groovy `0` is false | Lua `0` is **true** | truth test MUST special-case `0` |
| nil in tables | Groovy lists hold `null` (`[null]` is truthy, §4.1 #7) | `nil` truncates a Lua array | use a sentinel, or forbid nils in node lists |
| GString vs string | GString retains its slots (§3.4) | Lua concatenation is eager | template values MUST be a table `{parts, values}`, not a string |
| null → text | `"${null}"` → `"null"` | `tostring(nil)` → `"nil"` | must emit `null` |
| number rendering | `5` prints `5` | Lua 5.1 prints `5` but `5/1` prints `5` and `5.0` prints `5`; `10/2` → `5` | force integer formatting on output |
| method on null | throws | `nil:foo()` throws a *different* message | error text is not part of the contract, but *throwing* is |

---

## 6. Discard & errors

### 6.1 Discard

Every generated script opens with three closures
(`CodeGenerator.java:208-212`, reproduced verbatim in the committed fixture
`sip-core/src/test/resources/bulkmapper/generated-groovy/first.groovy:21-25`):

```groovy
import eu.delving.groovy.DiscardRecordException
def discard      = { reason        -> throw new DiscardRecordException(reason.toString()) }
def discardIf    = { thing, reason -> if (thing)  throw new DiscardRecordException(reason.toString()) }
def discardIfNot = { thing, reason -> if (!thing) throw new DiscardRecordException(reason.toString()) }
```

`DiscardRecordException` is an unchecked `RuntimeException` carrying only a
message (`sip-core/src/main/java/eu/delving/groovy/DiscardRecordException.java:26-29`).

Semantics an engine MUST reproduce:

- discarding **aborts the whole record**, unwinding out of every builder call —
  partially built output is thrown away, not emitted;
- the truth test in `discardIf`/`discardIfNot` is the language's truth test
  (§4.5), so `discardIfNot(it.title_, 'no title')` discards on `""`;
- `reason.toString()` is called, so a node or list reason is stringified per §1.

### 6.2 Discard handling by the host

| Host | Behaviour | Reference |
|---|---|---|
| `BulkMappingRunner` | unwraps `ScriptException` and rethrows the cause | `BulkMappingRunner.java:109-112` |
| `AppMappingRunner` | catches and rethrows unchanged | `AppMappingRunner.java:100-102` |
| `ValidatingMappingRunner` | re-throws as-is, explicitly not converted to a mapping error | `ValidatingMappingRunner.java:110-111` |
| `MappingExecutor` | records `withDiscarded(true, message)` on the result | `MappingExecutor.java:101-102` |
| `FileProcessor` (GUI/CLI) | counts the record as discarded rather than failed | `sip-app/src/main/java/eu/delving/sip/xml/FileProcessor.java:345,675` |

A discard is therefore **not an error**. An engine MUST expose it as a distinct
outcome so the harness can score it separately.

### 6.3 Error taxonomy

Non-discard failures are categorised by `MappingException.getErrorType()` and
mapped by `MappingExecutor` (`MappingExecutor.java:104-126`) to:
`VALIDATION`, `STRUCTURE`, `CONTENT`, `RDF`, `COMPILATION`, `EXECUTION`
(the last two folded into a generic schema-validation error). Anything else
becomes `"Unexpected error: …"` (`MappingExecutor.java:127-132`).

Distinct exception types that a mapping can raise from inside the builder:

- `LanguageTagException` — invalid or empty `xml:lang` (`DOMBuilder.java:227-249`);
- `RuntimeException("No namespace for <prefix>")` (`DOMBuilder.java:404`);
- `RuntimeException("Can't handle xmlns attribute" / "…xmlns:*")` (`DOMBuilder.java:217-218`);
- `RuntimeException("No CDATA terminator")` (`DOMBuilder.java:384`);
- `RuntimeException("Node type not implemented: …")` from empty-stripping (`Utils.java:89`).

An engine SHOULD raise at the same points; error *messages* are not part of the
golden contract, error *occurrence* is.

---

## 7. Facts & lookups

### 7.1 Facts

Facts are a `Map<String, String>` of dataset-level metadata (`orgId`, `spec`,
`provider`, `baseUrl`, `rights`, `language`, …). They reach mapping code twice:

1. **As typed locals.** One `String <key> = '''<value>'''` per fact, with `'`
   escaped as `\'` (`CodeGenerator.java:215-221`). Triple-quoting means embedded
   newlines survive.
2. **As a node tree.** `_facts` is bound to
   `Utils.initFactsNode` (`Utils.java:49-55`): a synthetic root node named
   `facts` with one child per fact, wrapped in a singleton list
   (`Utils.java:54`, bound at `Utils.java:63` and re-exposed as
   `Object _facts = WORLD._facts`, `CodeGenerator.java:213`).

The node form is what makes `_facts.dataProvider * { _dataProvider -> … }` work
(see `first.groovy:286`) — it is an ordinary navigation over an ordinary node
tree, so **Navigation** applies unchanged, including `tagToVariable`
normalisation of fact names.

An engine MUST provide both forms; mappings in the corpus use both, sometimes in
the same snippet.

### 7.2 Option lists (`_optLookup`)

`_optLookup` is bound to a `Map<String, Map<String, OptList.Opt>>`
(`Utils.java:58,64`), i.e. dictionary name → key → option. An `Opt`
(`sip-core/src/main/java/eu/delving/metadata/OptList.java:107-127`) has fields
`key`, `value`, `schema`, `schemaUri`, and **`toString()` returns `value`, or
`key` when `value` is null** (`OptList.java:124-126`).

### 7.3 Generated dictionary and lookup code

`CodeGenerator.toLookupCode` (`CodeGenerator.java:754-784`) emits, per node
mapping:

**With a dictionary** (`CodeGenerator.java:757-776`):

```groovy
def DictionaryX = [ '''key''':'''value''', … ]
def lookupX = { value ->
   if (!value) return null
   String optKey = DictionaryX[value.sanitize()]
   if (!optKey) optKey = value
   _optLookup['X'][optKey]
}
```

Note the sequence: falsy input → `null`; the dictionary is keyed on the
**sanitised** (§4.1 #21) input; a dictionary miss falls back to the raw value;
the result is the `Opt` object, not a String. Keys and values are escaped by
`CodeGenerator.sanitizeGroovy` (`CodeGenerator.java:822-826`), which escapes `'`
**and** collapses newlines and space runs — a stricter transform than
`MappingCategory.sanitize`.

**Without a dictionary** (`CodeGenerator.java:777-783`), only when the option
list has no `value` element:

```groovy
def lookupX = { value ->
   if (!value) return null
   _optLookup['X'][value.toString()]
}
```

### 7.4 Lookup call sites

`toLookupStatement` (`CodeGenerator.java:439-476`) emits an assignment to
`OptList.Opt _found<Dictionary>` (`OptBox.getOuterOptReference`,
`sip-core/src/main/java/eu/delving/metadata/OptBox.java:71-73`) followed by
`if (_found<Dictionary>) {`, so the whole builder subtree is skipped on a miss.
Inside, `_found<Dictionary>.<field>` selects the role's field
(`OptBox.getInnerOptReference`, `OptBox.java:75-80`), defaulting the ROOT role
to the `VALUE` field.

An engine MUST reproduce the guard (skip-on-miss), the sanitised dictionary key,
the raw-value fallback, and the `Opt.toString()` rule.

---

## 8. URIs & identifiers

### 8.1 `_uniqueIdentifier`

Declared as the literal placeholder `'UNIQUE_IDENTIFIER'`
(`CodeGenerator.java:222`) and reassigned per record from the input root's `@id`
(`CodeGenerator.java:263`):

```groovy
_uniqueIdentifier = _input['@id'][0].toString()
```

The value has already been through `StringUtil.sanitizeId` at parse time
(`MetadataParser.java:130`; `StringUtil.java:36-38` maps `/`, `_` and space to
`-`, then collapses `-` runs). An engine MUST NOT re-sanitise here.

### 8.2 `internalRecordURI` / `internalRecordURN`

Emitted verbatim (`CodeGenerator.java:223-225`):

```groovy
def internalRecordURI = { -> "urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURN()}/graph" }
def internalRecordURN = internalRecordURI
```

`CodeGeneratorTest` pins this string exactly
(`sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java:32-36`):
the generated code must contain that closure definition **character for
character**, and must contain `def internalRecordURN = internalRecordURI`.
`internalRecordURI` must also appear in the standard-function list as a
zero-argument function whose `toString()` is `"internalRecordURI()"`
(`CodeGeneratorTest.java:39-49`, backed by
`StandardMappingFunctions.java:32-35`).

Both names are pre-seeded into the deduplication set
(`CodeGenerator.java:229-230`), so a user function of either name is silently
dropped rather than overriding the helper.

**Constant mappings referencing the helper are unquoted.** A constant whose
trimmed text is `internalRecordURI`, `internalRecordURI()`, `internalRecordURN`
or `internalRecordURN()` is emitted as a **call**, not as a string literal
(`toInternalRecordHelperCall`, `CodeGenerator.java:633-643`, used by
`toConstantCode`, `CodeGenerator.java:623-631`). `CodeGeneratorTest.java:51-71`
asserts both that `internalRecordURI()` appears and that
`'internalRecordURI()'` (quoted) does not.

### 8.3 `rdf:about` auto-injection

`findAutoRdfAboutTarget` (`CodeGenerator.java:288-301`) picks the **first
populated non-attribute child of the rec-def root**. It returns that node only
if the node declares an `rdf:about` attribute in the schema and the user has
**not** mapped it; otherwise it returns `null` and the feature is a no-op. It
deliberately stops at the first populated child rather than scanning siblings
(`CodeGenerator.java:276-287` documents the rationale).

When a target exists, `startBuilderCall` (`CodeGenerator.java:524-584`) prepends:

```groovy
// auto-injected rdf:about (fallback to internalRecordURI())
'ns:Tag' (
  'rdf:about' : {
     internalRecordURI()
  }
  , …other attributes…
) { …
```

Pinned by `CodeGeneratorTest.java:73-94` (fires on the first populated top-level
resource), `:96-129` (does **not** fire when the user supplied `rdf:about` —
asserted by counting `internalRecordURI()` occurrences, which must be exactly 1,
the closure definition), and `:131-147` (does not fire when nothing is
populated).

### 8.4 Output-side URI validation

`MappingResult` (`sip-core/src/main/java/eu/delving/metadata/MappingResult.java`)
checks the produced DOM:

- `getUriErrors` (`:92-110`) evaluates the rec-def's URI-check XPaths and reports
  `"At <path>: not a URI: [<content>]"` for any value that is not an **absolute**
  `java.net.URI` (`:83-90`).
- `collectMissingTopLevelSubjectErrors` (`:127-149`) — only when the output root
  is an `RDF` element (`:151-156`): if **no** top-level child carries a non-blank,
  non-`_:` `rdf:about`, an error is reported, worded differently for
  "no top-level resources" vs "no resource with a non-blank rdf:about". One
  conforming sibling is enough (`:137-139`).
- `collectRelativeUriErrors` (`:158-187`) — recursively, any `rdf:about` or
  `rdf:resource` that is non-empty, not a `_:` blank node, and not absolute is
  reported as a relative-URI error; an unparseable value is reported as invalid.

These run outside the mapping engine, but they define what "valid output" means
and Task 5's verifier SHOULD apply the same checks to Lua-produced DOM.

### 8.5 URI-shaping functions

`sanitizeURI` (§4.1 #22) is the 8th most common construct in the corpus (15,092
occurrences) and is **not** a general percent-encoder: only space, `[`, `]` and
`\` are encoded. `sanitizeURN` (§4.1 #23) is a URN-slug transform and is the
only null-safe function in the category. The generated comment block documents
both for users (`CodeGenerator.java:98-99`).

---

## 9. Lua ecosystem survey

Evaluated against the requirements this spec creates. Two are **hard**:
**pure Lua** (no C extension: the host is gopher-lua, which cannot load
`.so` modules — `package.loadlib` is unsupported) and **Lua 5.1 compatible**
(gopher-lua implements Lua 5.1 plus 5.2's `goto`).

### 9.1 XML parsing — the Navigation contract as an acceptance test

A parser is acceptable only if the tree it produces can satisfy §2:

| Requirement (from §2) | Why it bites |
|---|---|
| R1. children in **document order**, as a list | `get("*")` returns `children` by reference (`GroovyNode.java:157`) |
| R2. **repeated siblings preserved individually** | `getByName` collects every match (`GroovyNode.java:183-189`); dc:subject ×2 must stay 2 nodes |
| R3. **element prefix available** | `getNodeName()` needs `prefix + localPart` (`GroovyNode.java:94`) |
| R4. **attributes with qualified names** | `rdf:about`, `xml:lang` (`MetadataParser.java:139-143`) |
| R5. **CDATA distinguishable from text** | must be re-wrapped as `<![CDATA[…]]>` (`MetadataParser.java:156`) |
| R6. **parent links** | `node.parent()` (`GroovyNode.java:132`) |
| R7. **mixed content not silently reordered** | text and elements interleave in `children` |

**SLAXML** (`Phrogz/SLAXML`, MIT, 259-line `slaxml.lua` + `slaxdom.lua`,
last pushed 2024-07-12, rockspec `slaxml-0.8-1` declaring
`lua >= 5.1, <= 5.4`):

- pure Lua, no C dependency; verified 5.1-safe (`local unpack = unpack or table.unpack`
  at `slaxml.lua:49` is the only version-sensitive line);
- `slaxdom.lua` builds nodes as
  `{type, name, kids, el, attr, nsURI, nsPrefix, parent}` — `kids` is
  document-ordered and includes text/comment/PI nodes, `el` is the
  element-only view, `attr` is both an ordered array and a name-keyed map;
- SAX layer additionally reports namespaces resolved to URIs, CDATA via a
  `cdata` flag on text nodes, comments and PIs.
- **Verdict: satisfies R1–R7.** `kids` gives R1/R7, repeated siblings are
  separate table entries (R2), `nsPrefix` gives R3, `attr` entries carry
  `nsPrefix`/`nsURI` (R4), the `cdata` flag gives R5, `parent` gives R6.
- Known limitation (stated in its own rockspec): it accepts some
  non-well-formed XML without error. For our use the input is already
  SIP-Creator-normalised source XML, so laxness costs nothing.

**xml2lua** (`manoelcampos/xml2lua`, MIT, 329 stars, last pushed 2025-06-12,
rockspec `xml2lua-1.6-2` declaring `lua >= 5.1, <= 5.4`), pure Lua, no C
dependency. It ships three handlers:

- `xmlhandler/tree.lua` — **rejected.** Its own header documents the
  disqualifying behaviour: *"Mixed-Content behaves unpredictably — the
  relationship between text elements and embedded tags is lost"*, and a single
  child is collapsed to a named key while multiple children become a vector.
  That breaks R1, R2 (shape varies with cardinality) and R7.
- `xmlhandler/dom.lua` — **viable but weaker.** Nodes are
  `{_name, _type, _text, _attr, _parent, _children}` with `_children` in
  document order, and the header claims it can represent any valid XML: R1,
  R2, R6, R7 hold, R4 holds via raw qualified names in `_attr`. R3 holds only
  by string-splitting `_name` on `:` (no namespace resolution at all), and R5
  requires re-deriving CDATA from the handler's node type.
- `xmlhandler/print.lua` — not applicable.

**Recommendation:** **SLAXML + `slaxdom`**, with xml2lua's `dom` handler as the
fallback if SLAXML's laxness ever becomes a problem. SLAXML wins on namespace
resolution (R3/R4 without string surgery), explicit CDATA flagging (R5), and a
smaller surface (2 files, 259 + ~200 lines) to audit and vendor. Neither library
is a drop-in `GroovyNode`: Task 6 MUST build a `GroovyNode`-shaped adapter over
the chosen DOM implementing §2.2 (`tagToVariable`) and §2.3 (the four lookup
forms), because both parsers preserve raw names and neither implements the
`_`-suffix / `@` / `*` key protocol.

### 9.2 RDF: build vs reuse

**Verdict: build, do not reuse.**

Named candidates, and why each is rejected:

| Candidate | Status | Why rejected |
|---|---|---|
| **volksdata** (`scossu/volksdata`, the only rock LuaRocks returns for "rdf", v1.0.0beta-12) | active | Declares `lua >= 5.4, < 6` and depends on `penlight` — **fails the 5.1 hard requirement** on its own metadata, before considering that its scope (terms, triples, graphs, a persistent store, Turtle/N3 codecs) is an order of magnitude beyond what is needed. |
| **lua-rdf** | **does not exist** — LuaRocks search returns no modules | n/a |
| Bindings to `raptor` / `redland` / `librdf` | C libraries | gopher-lua cannot load C modules (`package.loadlib` unsupported); fails the pure-Lua hard requirement. |
| A Lua SPARQL/triplestore client | wrong layer | We never query; we serialise. |

The actual requirement is narrow: the mapping engine does **not** build an RDF
graph. It builds a **DOM** (§3) that is then serialised as RDF/XML by the
existing Java side, with `MappingResult` doing RDF-level validation (§8.4).
What Lua needs is therefore an **element builder**, not an RDF library:
namespace-prefixed element creation, attribute maps, ordered children, CDATA
spans, empty-element stripping (§3.6) — roughly the 429 lines of
`DOMBuilder.java` plus the 50 lines of `Utils.stripEmpty`, with no triple
model, no graph, no Turtle writer.

Reusing an RDF library would mean modelling triples we do not have and then
losing the XML-shape fidelity that golden comparison depends on. Build a small
`lua-rdf` module scoped to *serialising the mapping DOM*, and keep the graph
semantics on the Java side where they already are.

### 9.3 gopher-lua constraints (Lua 5.1 target)

Confirmed against `yuin/gopher-lua` upstream:

1. **Language level is Lua 5.1**, plus Lua 5.2's `goto` / `::label::` (which
   also makes `goto` a reserved word — it cannot be used as an identifier).
2. **No C modules.** `package.loadlib` is unsupported, so every dependency must
   be pure Lua source. This is the constraint that drives §9.1 and §9.2.
3. **Unsupported stdlib:** `string.dump`, `os.setlocale`,
   `lua_Debug.namewhat`, `package.loadlib`, and debug hooks.
4. **No 5.2+ stdlib.** No `bit32`; no 5.3+ `string.pack`/`string.unpack`,
   integer division `//`, bitwise operators, or the `utf8` library. Strings are
   byte strings; there is no built-in Unicode support — relevant to
   `tagToVariable`'s accent folding (§2.2), which MUST be implemented as an
   explicit byte-sequence table, not via any case/locale function.
5. **Lua patterns, not regexes** — gopher-lua implements Lua's own pattern
   matcher natively in Go (`pm/pm.go`, a hand-written matcher with captures,
   `%b`, position captures). It does **not** delegate to Go's `regexp`; some
   third-party fork documentation claims otherwise and is wrong for upstream.
   This means `string.find`/`gsub`/`match` behave like reference Lua, and it
   also means there is **no** regex engine available in-VM. See §9.4.
6. `collectgarbage` ignores its arguments and collects for the whole Go process.
7. `file:setvbuf` has no line-buffering mode; DST is not modelled in date
   handling; `os.setenv` is a non-standard extension.
8. Coroutines are fully supported (including cancellation via Go context), and
   gopher-lua adds a `channel` type — neither is needed by this spec, and an
   engine SHOULD NOT depend on them, to keep the Lua sources runnable under
   stock PUC Lua 5.1 for testing.

### 9.4 The regex gap (measured)

`replaceAll`, `split` and `matches` take `java.util.regex` patterns (§4.3), and
per §9.3.5 the host VM offers only Lua patterns. To size the gap, the 2,241
canonical mappings were scanned for string-literal first arguments to those
three methods: **506 distinct patterns, 37,520 occurrences**. Classifying each
by the regex features it actually uses (after undoing Groovy string escaping,
with a scanner that tracks character-class state):

| Class | Occurrences | Share | Example |
|---|------------:|------:|---------|
| literal (no metacharacters) | 17,211 | 45.87% | `split('-')`, `replaceAll('ark:/')` |
| expressible as a Lua pattern | 15,663 | 41.75% | `^http:`, `[0-9]*`, `.jpg` |
| **alternation `\|`** | 3,927 | 10.47% | `'\[\|\]'`, `'WORKSHOP\|workshop'` |
| **counted repetition `{n,m}`** | 392 | 1.04% | `'[ ]{2,15}'` |
| **inline flags / groups `(?…)`** | 276 | 0.74% | `'(?i)\.jpg\|\.jpeg\|…'` |
| **lazy/possessive quantifiers** | 41 | 0.11% | `'thumb/.*?/'` |
| extraction artifacts | 10 | 0.03% | a literal truncated at an escaped quote (`"[\"…`) — not a real pattern |

So **≈87.6% of regex uses are reachable with Lua patterns alone**, and
**≈12.4% are not** — dominated by alternation. Two viable strategies for Task 7,
in preference order:

1. **Host callback.** Expose Go's `regexp` (RE2) to the Lua VM as a
   `regex.replace_all` / `regex.split` / `regex.matches` triple. RE2 covers
   alternation, counted repetition and inline flags; it lacks backreferences and
   lookaround, which the corpus scan shows are effectively unused (0 measured
   backreferences; 6 occurrences of a bare `(?i)` and no lookaround). Lazy
   quantifiers are supported by RE2. This closes ~100% of the measured gap at
   the cost of making the Lua module non-standalone.
2. **Pure-Lua translation layer.** Translate the common java.util.regex subset
   to Lua patterns and fall back to an error on the rest — closes 87.6%, keeps
   the rock dependency-free, and makes the residual 12.4% a visible, countable
   failure rather than a silent wrong answer.

Caveats on the measurement: only string-literal first arguments were counted
(slashy `/…/` patterns, `~/…/` literals and pattern variables are not included);
the extractor does not handle escaped quotes inside a literal, which produced the
10 truncated "artifact" occurrences above; and the classifier is conservative — a pattern is credited as Lua-expressible
only if it uses no feature outside Lua's pattern grammar.

### 9.5 Rock names reserved

Neither name is taken; LuaRocks search returns **no modules** for either:

- **`lua-mapping-engine`** — the Navigation + Stdlib + Builder core (§2, §4, §3).
- **`lua-rdf`** — the DOM/serialisation layer scoped per §9.2.

Both SHOULD declare `lua >= 5.1` and depend on nothing outside a vendored
SLAXML.

---

## 10. Open questions for later tasks

1. **Subscript dispatch** (§2.4). `MappingCategory.getAt` throws yet
   `node['@id']` works. Task 4's golden harness SHOULD assert the observed
   behaviour directly rather than rely on this spec's reading.
2. **Locale sensitivity** (§4.3). `toLowerCase`/`toUpperCase` use the default
   locale in the reference implementation. Pinning to root locale is almost
   certainly correct but is a behaviour change; the golden corpus should be
   checked for Turkish-`I` style inputs before pinning.
3. **`NodeList.toString()`** (§1). `[a, b]` rendering is inherited, not chosen.
   The golden harness SHOULD confirm a mapping that interpolates a bare
   NodeList, since this is the kind of accident that shows up in real output.
4. **`GroovyNode.hashCode` staleness** (§2.6). `hashCode` is only assigned in
   the non-null branch of `setNodeValue` (`GroovyNode.java:122-130`), so a node
   whose value was set to null keeps hash 0 while `text` is `""`. Since
   `equals` short-circuits on hash inequality (`GroovyNode.java:169-170`),
   node-to-node equality can be false for two equal-text nodes. Worth a
   targeted golden test.
5. **Regex strategy** (§9.4). Host callback vs pure-Lua translation is a
   product decision, not a technical one; Task 8 should decide on the basis of
   whether the rock must stand alone.
