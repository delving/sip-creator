# Auto-inject `rdf:about` on Root Resource

Date: 2026-05-26
Status: Proposed (remaining scope only — see "Already Landed")
Scope: `sip-core` (CodeGenerator, MappingCategory)

## Already Landed

Two commits on `main` already implement the validator half of this work
and expose the helper function in the UI:

- `01319cba fix(sip-core): require top-level RDF subjects`
  - `MappingResult.collectMissingTopLevelSubjectErrors` walks `rdf:RDF`'s
    element children. Each child must carry a non-empty, non-blank
    `rdf:about`. Otherwise: error
    `"Top-level RDF resource <X> has no rdf:about; use internalRecordURI() for an internal record identifier"`.
  - `CodeGenerator.toConstantCode` recognizes the literal strings
    `internalRecordURI` / `internalRecordURI()` (and `internalRecordURN`)
    when typed as a constant node-mapping value, and rewrites to the
    actual call instead of a string literal.
  - Tests added in `CodeGeneratorTest` + `JenaHelperTest`.

- `93c408dc fix(sip-app): expose internal record URI helper`
  - `StandardMappingFunctions` registers `internalRecordURI` and
    `internalRecordURN` as zero-argument standard functions. They now
    appear in the field-mapping function picker.
  - `MappingFunction.getParameterCount()` added.
  - `FieldMappingFrame` — zero-arg functions insert as `name()` at the
    caret without requiring selected text.

Net effect of landed work:

1. Records without `rdf:about` on top-level subjects now fail validation
   (no more silent omission).
2. The fallback URN closure (`internalRecordURI()`) is discoverable in
   the function picker and easy to drop into a node-mapping.
3. Users must still add the node-mapping themselves; nothing is
   auto-injected.

## Remaining Problem

The validator forces users to add `rdf:about` themselves on every
non-EDM rec-def. For rec-defs like CIDOC-CRM / LRM (e.g.,
`lrm:F3_Manifestation`) where the root is a single obvious subject and
the canonical identifier is already known (the URN that appears in the
trailing comment), this is repetitive boilerplate. EDM works without
this manual step only because its rec-def hard-codes node-mappings on
`@rdf:about`.

Additionally, the URN produced by `internalRecordURI()` may not match
the URN in the trailing comment when `_uniqueIdentifier` contains
characters that `MappingCategory.sanitizeURI()` does not transform
(notably `:`). The trailing-comment URN shows `c-lvd-94` derived from
source `c:lvd:94`; the closure currently calls `sanitizeURI()` which
only percent-encodes space / `[` / `]` / `\`. Either:

(a) `_uniqueIdentifier` is colon-stripped upstream before reaching the
closure, or
(b) the closure currently produces a different URN than the trailing
comment.

To be verified during implementation. If (b), `internalRecordURI()`
needs a stronger sanitizer.

## Goals (remaining)

1. When a rec-def root has exactly one populated top-level child and
   that child has no user-authored `rdf:about` node-mapping, codegen
   automatically inserts `'rdf:about': internalRecordURI()` on it so
   the validator stays green out of the box.
2. User-authored `rdf:about` always wins over the auto-injected one.
3. `internalRecordURI()` produces the same URN that the trailing
   comment uses (modulo the SHA1 hash suffix, which is post-
   serialization).
4. Behavior is reactive to current mapping state: rec-defs with
   multiple candidate roots (e.g. ACE) only get injection on the root
   the user actually starts mapping under.

## Non-Goals

- Multi-root rec-defs where more than one top-level child is populated.
  Auto-inject lands on the first populated child only; others must
  carry explicit mappings (validator will reject otherwise).
- Changing the URN scheme.
- Including content SHA1 in `rdf:about`.
- Editing existing rec-def XML files.

## Problem

Some rec-defs (e.g. CIDOC-CRM / LRM `lrm:F3_Manifestation`, plus most non-EDM
rec-defs) do not hard-code a node-mapping for `@rdf:about` on the root
resource. When the user maps such a rec-def, the produced RDF/XML has no
`rdf:about` on the top-level resource element. Downstream RDF tooling then
either fails to parse, treats the resource as a blank node, or merges it
with other unidentified subjects.

Example output (root has no `rdf:about`):

```xml
<rdf:RDF ...>
    <lrm:F3_Manifestation>
        <crm:P1_is_identified_by> ... </crm:P1_is_identified_by>
        ...
    </lrm:F3_Manifestation>
</rdf:RDF>
<!--<urn:datahub_brocade-cat-lh_c-lvd-94/graph__6b2dba118deccaf74f368ac24e949e824910df27>-->
```

`MappingResult.toByteArrayOutputStream` already computes a URN per record
and writes it as a trailing comment. The same URN (minus content hash) is
exposed inside generated Groovy as the closure `internalRecordURI()`
(`CodeGenerator.java:221-222`). Nothing currently wires that value into
the output DOM as `rdf:about`, and nothing errors when it is missing.

EDM rec-def works because `_data/edm_5.2.6_record-definition.xml:354,390`
hard-codes a node-mapping that emits `${baseUrl}/resource/document/...`
into `@rdf:about` on `ore:Aggregation` and `edm:ProvidedCHO`. Other
rec-defs have no equivalent.

## Goals

1. Every produced record carries `rdf:about` on the first populated
   top-level resource element of `rdf:RDF`.
2. Fallback identifier matches the URN that already appears in the
   trailing comment: `urn:{orgId}_{spec}_{sanitizedLocalId}/graph`.
3. User-authored node-mappings for `rdf:about` always win over the
   fallback.
4. Hard error when no `rdf:about` ends up on the root resource at run
   time (covers misconfigured rec-def or explicit empty override).
5. Behavior is reactive to current mapping state: rec-defs with
   multiple candidate roots (e.g. ACE) only inject on whichever root
   the user actually starts mapping.

## Non-Goals

- Auto-applying `rdf:about` to every top-level resource (only the first
  populated one).
- Changing the URN scheme itself.
- Including the SHA1 content hash in `rdf:about` (it remains comment-only,
  since it depends on post-serialization bytes).
- Editing existing rec-def XML files.

## Architecture (remaining work)

Single-point change in `CodeGenerator` plus a sanitization tweak inside
the `internalRecordURI` closure. Validator is already in place.

```
User edits mapping → MappingModel → CodeGenerator runs
  ↓ For each direct child of recDefTree.getRoot():
  ↓   if child.isPopulated() && !child.isAttr() → candidate
  ↓ Take first candidate (if any)
  ↓ If candidate has no rdf:about node-mapping → inject fallback
  ↓ Generated Groovy contains synthetic 'rdf:about': internalRecordURI()
Groovy compiles & runs per record → DOM
  ↓ MappingResult.getRDFErrors() validates (already landed)
FileProcessor accepts/rejects per allowInvalid
```

## Components

### `CodeGenerator` — auto-inject

Location: `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java`.

Behavior:

- During `generate()` (or a new helper invoked from `toElementCode`),
  iterate `recDefTree.getRoot().getChildren()`. Collect children where
  `isPopulated() && !isAttr()`.
- If list empty: no injection (rec-def not yet mapped — nothing to do).
- Take first element of the list as the "root resource node".
- Detect whether that node already supplies `rdf:about`. Check two
  things:
  - Any `NodeMapping` whose output path ends in `@rdf:about` directly
    on that node, or
  - Any child `RecDefNode` with `isAttr()` and tag `rdf:about` that is
    populated.
- If neither: in `startBuilderCall` for the root resource node, emit
  one extra attribute entry before/after existing attributes:
  - A `// auto-injected rdf:about (fallback to internalRecordURI())`
    comment line, then
  - `'rdf:about': internalRecordURI()` as a builder map entry.
- If rec-def's `<attrs>` does not include `rdf:about`, skip injection
  silently (validator will still raise the error downstream — surfaces
  the schema-level gap without making codegen brittle).

### `internalRecordURI` and local-id sanitization

Closure is already emitted at `CodeGenerator.java:221-222`:

```groovy
def internalRecordURI = { -> "urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURI()}/graph" }
def internalRecordURN = internalRecordURI
```

`MappingCategory.sanitizeURI()` currently percent-encodes `space`,
`[`, `]`, `\`. It does not touch `:`. Trailing-comment URN in real
output (`c-lvd-94` from source `c:lvd:94`) shows that colon-to-dash
conversion happens upstream during `localId` derivation — confirm
during implementation. If `_uniqueIdentifier` arrives with colons,
`internalRecordURI()` would yield `urn:..._c:lvd:94/graph`, which is
syntactically a URN but ambiguous to parsers and breaks the contract
of matching the trailing comment.

Decision: add a sibling helper `sanitizeURN(String)` (or extend the
existing `MappingCategory.sanitizeURI`) so it ALSO maps `:` → `-` and
collapses repeated `-`. Use it in the `internalRecordURI` closure:

```groovy
def internalRecordURI = { -> "urn:${orgId}_${spec}_${_uniqueIdentifier.sanitizeURN()}/graph" }
```

Existing `sanitizeURI` keeps its current behavior so EDM's
HTTP-URL-building call site is unchanged.

### `MappingResult` — validator gate (already landed)

`collectMissingTopLevelSubjectErrors` is in place. No further changes
needed here. Auto-injected `rdf:about` flows through the existing
validator path; if `internalRecordURI()` returns empty (e.g.,
`_uniqueIdentifier` is empty), the existing validator catches it.

## Behavior on Multi-Root Rec-Defs (ACE)

- Rec-def loaded, no mappings yet: no top-level child is `isPopulated()`.
  Codegen runs as today. No injection. Validator never runs (no
  `MappingResult`).
- User maps one element under top-level child A: A becomes populated.
  Next codegen pass picks A as root resource node and injects
  `rdf:about` there.
- User reroutes mapping under top-level child B: A drops back to
  unpopulated; B becomes populated. Codegen now injects on B. A's
  generated output stops emitting auto-injected `rdf:about`.
- Both A and B populated simultaneously (rare): injection lands on A
  (document order). B must either supply its own `rdf:about` mapping
  or be modeled as nested. Validator will reject record if B is
  considered a top-level resource — but our validator only inspects
  the first child, so B without `rdf:about` would slip through. This
  is acceptable: multi-resource records are out of scope for v1, and
  EDM-style records have explicit rdf:about node-mappings on every
  resource via the rec-def.

## Error Handling

- Injection itself never errors (silent fallback by design).
- `MappingResult.getRDFErrors()` raises hard error in two cases:
  - Root resource ends up without any `rdf:about` (rec-def has no
    `rdf:about` in `<attrs>`, or explicit override produced empty
    string).
  - Existing relative-URI / Jena-parse failures (unchanged).
- Errors propagate via the existing `FileProcessor` events list and
  `allowInvalid` flag.

## Testing

- `TestCodeGeneration` (`sip-core/src/test/resources/codegen/`):
  add a fixture whose rec-def root has no `rdf:about` node-mapping
  and one top-level child mapped. Assert generated Groovy contains
  `'rdf:about': internalRecordURI()` exactly once on that child.
- Add a fixture where user explicitly maps `@rdf:about` on the root
  resource. Assert generated Groovy contains user's expression and
  NO auto-injected one.
- `TestStringUtil` / new `MappingCategory` test: assert
  `sanitizeURN` maps `c:lvd:94` → `c-lvd-94` and collapses repeats.
- `JenaHelperTest`-style: DOM with root lacking `rdf:about` →
  `MappingResult.getRDFErrors()` contains new error. DOM with valid
  `rdf:about` → error absent.
- Integration: take a real CRM/LRM mapping. Generate output. Confirm
  `<lrm:F3_Manifestation rdf:about="urn:..._c-lvd-94/graph">` present
  and trailing comment still produced with hash suffix.

## Open Items

- Verify exact mechanics of `_uniqueIdentifier` derivation and whether
  colon stripping is already applied elsewhere before reaching the
  closure. Confirm during implementation; adjust `sanitizeURN`
  accordingly.
- Decide whether the auto-injected attribute should be placed first or
  last in the generated builder map. Probably first, so user-eye
  scanning of generated code sees the record identity immediately.
- 2026-05-27: No CRM/LRM-style mapping was available in `~/DelvingSIPCreator` during plan execution. Verification rests on the unit tests in `CodeGeneratorTest` (auto-inject fires, user override wins, no-op when nothing is populated) and the validator coverage in `MappingResult` from prior commits 01319cba and 93c408dc. To be re-verified manually when a CRM/LRM dataset is loaded.

## Files Touched (remaining work)

- `sip-core/src/main/java/eu/delving/metadata/CodeGenerator.java` —
  auto-inject + closure switches to `sanitizeURN`.
- `sip-core/src/main/resources/MappingCategory.groovy` — add
  `sanitizeURN(Object)`.
- `sip-core/src/test/java/eu/delving/metadata/CodeGeneratorTest.java` —
  assert auto-injection on populated root, no auto-injection when
  user supplies `rdf:about`, no auto-injection on unpopulated rec-def.
- `sip-core/src/test/java/eu/delving/test/TestMappingCategory.java`
  (new) or extend `TestStringUtil` — assert `sanitizeURN` colon-to-dash
  and repeat-collapse.
- Optional integration fixture: a CRM/LRM rec-def stub under
  `sip-core/src/test/resources/codegen/` driving the auto-inject path.
