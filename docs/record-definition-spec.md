# SIP-Creator `record-definition` Format Specification

Status: descriptive, derived from the Java reference implementation as of commit
`b1dce0b5`.  Audience: developers re-implementing the parser/generator in Go or
JavaScript (browser-side editor).  All file references use repository-relative
paths and Java line numbers so the source of every claim can be verified.

Today the format is defined implicitly by XStream-annotated Java classes plus a
handful of in-the-wild XML files.  The XStream serialization is heavily
attribute-oriented and relies on several non-obvious behaviours (resolution
phases, deep copy of templates, optional outer collection wrappers,
attribute-vs-element ambiguity).  This document makes the wire format and the
resolution semantics explicit.

---

## 1. Overview

### 1.1 What it is

A **record-definition** describes the **shape** of one schema-conformant output
record.  It is the schema-side artefact that drives:

1. The tree displayed in the SIP-Creator GUI on the "target" side.
2. The Groovy builder code that constructs each output record.
3. Documentation panels shown next to elements.
4. Value enumerations (opt-lists) used by the dictionary editor.
5. Validation hooks (uriCheck, assertions) that run after a record is built.

It is **not**:

- The XSD schema (separate file, `{prefix}_{version}_validation.xsd`).
- The SHACL shape (separate file, `{prefix}_{version}_shacl.ttl`).
- The per-dataset mapping (separate file, `mapping_{prefix}.xml`; serialized
  from `RecMapping`; references a record-definition by `SchemaVersion`).

The XSD and SHACL files are *output* validation contracts.  The
record-definition is the *editor model*: it declares which elements/attributes
the user is allowed to map onto, in what nesting, with what semantics.

### 1.2 Role in the pipeline

```
source.xml.gz  ──parse──▶  MetadataRecord
                                │
                                ▼
                    ┌─────── RecMapping ───────┐
                    │  per-dataset NodeMapping  │
                    │  one per output node      │
                    └─────────────┬─────────────┘
                                  │
                                  │ uses
                                  ▼
                    ┌─────── RecDef ───────────┐
                    │  schema-side definition   │
                    │  (this document)          │
                    └─────────────┬─────────────┘
                                  │
                                  ▼
                       CodeGenerator emits Groovy
                                  │
                                  ▼
                       MappingRunner executes
                                  │
                                  ▼
                       DOM, validated against XSD/SHACL
                                  │
                                  ▼
                       RDF/XML output record
```

The `RecDef` is the **target-side scaffolding**.  The `RecMapping` decorates it
with `NodeMapping` instances that say "for *this* output node, take *that*
input path and run *this* Groovy snippet".  Together they generate a Groovy
closure that emits one valid record per input record.

Reference: `sip-core/src/main/java/eu/delving/metadata/RecDef.java:40-61` for
the class-level Javadoc.

### 1.3 How it relates to RecMapping

| Concern                              | `RecDef` (this spec) | `RecMapping`         |
| ------------------------------------ | -------------------- | -------------------- |
| Shape of output                      | yes                  | inherited            |
| Allowed elements/attributes          | yes                  | no                   |
| Documentation                        | yes                  | no                   |
| Opt-lists / dictionaries (allowed)   | yes                  | no                   |
| Reusable functions (Groovy)          | optional (yes)       | also yes             |
| Input path bindings                  | no                   | yes                  |
| Per-element Groovy code              | no (except defaults) | yes                  |
| Dictionary instance (input → output) | no                   | yes                  |
| Facts / dataset metadata             | no                   | yes (via facts file) |

`RecMapping` is XStream-aliased `record-mapping` and lives at the top level of
the sip.zip as `mapping_{prefix}.xml`.  Its `NodeMapping` instances reference
output paths defined by the `RecDef`.

### 1.4 Relationship to XSD/SHACL, and the four generators

The validation XSD (`*_validation.xsd`) and SHACL shape (`*_shacl.ttl`) are
shipped alongside the record-definition for the same schema version (see
`schema-repo/src/main/java/eu/delving/schema/SchemaType.java:26-33`).  They are
the contracts that **must** be satisfied by output records.  The
record-definition should be a (looser, editor-friendly) shape that produces XSD-
and SHACL-valid records when populated.  The Java implementation does **not**
cross-validate the record-definition against the XSD; consistency is a hand-
maintained property of the schema repository at https://schemas.delving.eu.

Since the *recdef-semantics-generators* feature, `*_validation.xsd` and
`*_shacl.ttl` are no longer the only artefacts a record-definition can
produce.  `sip-core/src/main/java/eu/delving/metadata/` ships **four**
generators, all reading the same semantic annotations off `RecDef` via a
shared parsed view, `RecDefSemantics.from(recDef)`
(`sip-core/.../RecDefSemantics.java:59`) — entities keyed by tag (templates
first, root overwrites), each with its properties (direct subelements, plus
-- since the nested-rangenode traversal added in this round of follow-ups --
the direct subelements of any inline rangenode nested anywhere inside the
entity, attributed to the entity matching the *nested* elem's tag; see
`RecDefSemantics.collectNestedProperties`):

| Generator                 | Output                                    | Reads (beyond the base shape)                                          |
| -------------------------- | ------------------------------------------ | ------------------------------------------------------------------------ |
| `XsdGenerator`              | `*_validation.xsd` (lax XSD)               | `required`, `singular`, `xsdDataType`, `xsdPattern`, `xsdMinOccurs`, `xsdMaxOccurs` |
| `RdfsGenerator`             | OWL/RDFS ontology (Turtle or RDF/XML)      | `subclassof`, `equivalentClass`, `subPropertyOf`, `target`, `xsdDataType`, Label/Definition `<para>`s (see §8.4) |
| `ShaclGenerator`            | SHACL shapes (Turtle)                      | `required`/`singular` → `sh:minCount`/`sh:maxCount`, `xsdDataType` → `sh:datatype`, `uriCheck` → `sh:nodeKind sh:IRI`, `target` → `sh:class`, `xsdPattern` → `sh:pattern` |
| `JsonLdContextGenerator`   | JSON-LD `@context`                         | `target`, `xsdDataType`, `uriCheck` (`@type: "@id"`)                    |

None of the four is invoked from the mapping/output pipeline in §1.2 above —
they are read-only views over the record-definition, generated on demand:

- **Narthex** exposes them as on-the-fly artifacts, never stored to disk, so a
  generator improvement is visible immediately without re-uploading anything:
  `GET /narthex/app/rec-defs/:prefix/:hash/artifact/:name` with
  `name` one of `ontology.rdf`, `ontology.ttl`, `shapes.ttl`, `context.jsonld`
  (`narthex/app/controllers/AppController.scala:1294-1320`, wired in
  `narthex/conf/routes:101`). The same file also falls back to
  `XsdGenerator.generate(recDef)` for `*_validation.xsd` when a record-def is
  uploaded without a hand-made XSD (`AppController.scala:1346-1357`).
- **SIP-Creator** applies the same on-the-fly-when-missing pattern to SHACL
  validation: the Expert menu's "Toggle SHACL Validation" checkbox
  (`sip-app/src/main/java/eu/delving/sip/menus/ExpertMenu.java:196-208`) makes
  `FileProcessor` validate every generated record against `shacl.ttl`
  (`sip-app/.../FileProcessor.java:228-231`, `697-720`). `StorageImpl`'s
  shape-loading path (`sip-app/.../StorageImpl.java:692-719`) loads
  `{prefix}_{version}_shacl.ttl` from disk if present; if the file is absent
  it generates shapes on the fly from the dataset's own record-definition via
  `ShaclGenerator.generate(recDef)`, parses the resulting Turtle into a Jena
  graph, and uses that instead. Any failure in that fallback (missing/broken
  recdef, a generator bug, a Turtle parse error) is caught and treated as "no
  shape" — SHACL validation is silently skipped for that run, exactly as
  before the fallback existed; it never breaks record processing. The toggle
  itself still defaults to off.

§5.1 below notes that unknown `<elem>` attributes are ignored by the mapping
engine. `xsdDataType`, `xsdPattern`, `xsdMinOccurs`, `xsdMaxOccurs`,
`subclassof`, `equivalentClass` and `subPropertyOf` are the named exceptions:
still inert for the mapping/output pipeline, but read by one or more of the
four generators above.

---

## 2. Identity & versioning

### 2.1 Schema identifier (`SchemaVersion`)

A schema is identified by the tuple `(prefix, version)`.  The string form is
`{prefix}_{version}` (e.g. `ace_0.2.3`, `edm_5.2.6`).

`schema-repo/src/main/java/eu/delving/schema/SchemaVersion.java:30` enforces:

```
prefix:   /[a-z]{3,6}/      (3..6 lower-case ASCII)
version:  /[0-9][.][0-9][.][0-9]+/  (major.minor.patch, all numeric)
```

Note: the regex is strict.  Prefixes outside 3..6 chars or versions with
non-numeric segments will be rejected by `new SchemaVersion(String)`.  A Go
re-implementation should keep the same validation to remain interoperable with
the schema repository.

### 2.2 File naming

For each `SchemaType` the canonical filename is
`{prefix}_{version}_{fileName}` where `fileName` comes from `SchemaType`
(`schema-repo/.../SchemaType.java:26`):

| `SchemaType` enum    | Suffix                | Notes                                            |
| -------------------- | --------------------- | ------------------------------------------------ |
| `RECORD_DEFINITION`  | `record-definition.xml` | The artefact this spec describes.              |
| `VALIDATION_SCHEMA`  | `validation.xsd`       | Output validation.                              |
| `SHACL_SHAPE`        | `shacl.ttl`            | Output validation.                              |
| `FACT_DEFINITIONS`   | `definition-list.xml`  | Dataset-level facts.                            |
| `VIEW_DEFINITION`    | `view-definition.xml`  | Optional display config.                        |

Examples on disk:
- `ace_0.2.3_record-definition.xml`
- `edm_5.2.6_record-definition.xml`
- `mods_3.4.0_record-definition.xml`

`SchemaVersion.getFullFileName(SchemaType)` builds these names; see
`schema-repo/.../SchemaVersion.java:53`.

### 2.3 Location inside a sip.zip

A sip.zip bundles, at the top level (no directories):

- `mapping_{prefix}.xml` — the `RecMapping`
- `{prefix}_{version}_record-definition.xml` — **this artefact**
- `{prefix}_{version}_validation.xsd` (optional)
- `hints.txt`, `narthex_facts.txt`, `sip.json`, `source.xml.gz`, etc.

See `sip-app/src/main/java/eu/delving/sip/files/StorageImpl.java:585-620` for
the packing code.  A SIP archive may also be unpacked as a directory; the
example file
`/home/kiivihal/PocketMapper/datahub/PocketMapper/work/adlib-loans__2026_05_05_11_25.sip.zip/`
is a directory (not a real zip) containing exactly these files.

### 2.4 Loading

`RecDef.read(InputStream)` is the only canonical entry point
(`sip-core/.../RecDef.java:75-84`).  It:

1. Reads the file as UTF-8.
2. Lets XStream construct the object graph.
3. Calls `recDef.resolve()`, which performs name resolution, prefix defaulting,
   template expansion, opt-list attachment, doc-block attachment, and
   element-group inlining.

After `resolve()` the in-memory `RecDef` is no longer a faithful image of the
on-disk XML.  In particular:

- `Elem.attrs` and `Elem.attrGroups` (the *string* fields) are set to `null`
  because they have been "consumed" into `Elem.attrList`.
- Same for `Elem.elems` and `Elem.elemGroups` versus `Elem.elemList`.
- `Doc` instances are attached to the matching `Elem`/`Attr` they document.
- `OptList` instances are stamped onto the matching `Elem.optList`.

A round-trip writer must **either** serialize before `resolve()` runs, or
re-derive `attrs="…"` and `elems="…"` from the resolved tree.  XStream
serializes the in-memory object as-is, so the latter requires manually clearing
the resolved-only fields.

---

## 3. Top-level structure

Root element: `<record-definition>` (XStream alias of `RecDef`, see
`sip-core/.../RecDef.java:63`).

### 3.1 Root attributes

All XStream `@XStreamAsAttribute` fields on `RecDef`:

| Attribute                       | Type     | Required | Default | Source                            |
| ------------------------------- | -------- | -------- | ------- | --------------------------------- |
| `prefix`                        | xs:NCName-like (see §2.1) | **yes** | —      | `RecDef.java:89-90`               |
| `version`                       | string (see §2.1) | **yes** | —      | `RecDef.java:92-93`               |
| `flat`                          | xs:boolean | no      | `false` | `RecDef.java:95-96`               |
| `elementFormDefaultQualified`   | xs:boolean | no      | `true`  | `RecDef.java:98-99`               |
| `attributeFormDefaultQualified` | xs:boolean | no      | `true`  | `RecDef.java:101-102`             |

`prefix` and `version` are not nullable: `resolve()` throws if either is
missing (`RecDef.java:212`).

`flat` is consulted by GUI code to decide whether to render a flat list versus a
tree.  Most modern record-definitions are `flat="false"` (EDM, ACE, MODS, SMFR).
ICN is the only example in this repo where `flat="true"`.

`elementFormDefaultQualified` and `attributeFormDefaultQualified` mirror their
XSD namesakes.  Only `mods_3.4.0` in the corpus sets non-default values
(`attributeFormDefaultQualified="false"`).

### 3.2 Direct children of `<record-definition>`

Listed in the order they appear in `RecDef.java:104-130`.  All children are
**optional** at the XStream layer; semantic requirements are noted.

| Child element       | Java field                                         | Cardinality | Required? | Purpose                                                                 |
| ------------------- | -------------------------------------------------- | ----------- | --------- | ----------------------------------------------------------------------- |
| `<namespaces>`      | `List<Namespace> namespaces`                       | 0..1        | strongly  | XML namespace bindings (see §4).                                        |
| `<functions>`       | `List<MappingFunction> functions`                  | 0..1        | no        | Reusable Groovy closures available to mappings (see §11).               |
| `<attrs>`           | `List<Attr> attrs`                                 | 0..1        | no        | Reusable attribute prototypes referenced from `<elem attrs="…"/>`.       |
| `<attr-groups>`     | `List<AttrGroup> attrGroups`                       | 0..1        | no        | Named bundles of attribute-prototype refs (see §6).                     |
| `<elems>`           | `List<Elem> elems`                                 | 0..1        | no        | Reusable element prototypes referenced from `<elem elems="…"/>`.         |
| `<elem-groups>`     | `List<ElemGroup> elemGroups`                       | 0..1        | no        | Named bundles of element prototypes referenced from `<elem elemGroups="…"/>`. |
| `<templates>`       | `List<Elem> templates`                             | 0..1        | no        | Reusable element subtrees expanded via `target="…"` (see §5.6).         |
| `<root>`            | `Elem root`                                        | 1           | **yes**   | The root of the output tree (see §5).                                   |
| `<opts>`            | `List<OptList> opts`                               | 0..1        | no        | Value enumerations (see §7).                                            |
| `<assertion-list>`  | `Assertion.AssertionList assertionList`            | 0..1        | no        | Post-build XPath assertions (see §10).                                  |
| `<field-markers>`   | `List<FieldMarker> fieldMarkers`                   | 0..1        | no        | Index/role hints (see §9).                                              |
| `<docs>`            | `List<Doc> docs`                                   | 0..1        | no        | Path/tag-based documentation block (see §8).                            |

**Ordering**: XStream is permissive on read but writes in the field-declaration
order above.  Existing on-disk files mostly follow this order, but several put
`<opts>` after `<field-markers>` (e.g. ACE) — both are accepted.  A Go writer
should emit the canonical Java order to maximise round-trip stability.

**Empty placeholders**: it is idiomatic for files to declare empty
`<field-markers/>`, `<opts/>`, `<docs/>` even when unused — see the ACE example
(lines 235-238).  A parser must accept both their presence and absence.

---

## 4. Namespaces block

```xml
<namespaces>
  <namespace prefix="rdf" uri="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/>
  <namespace prefix="ace" uri="https://data.antwerp.be/ns/cultureel-erfgoed#"/>
  <namespace prefix="dc"  uri="http://purl.org/dc/elements/1.1/"/>
</namespaces>
```

XStream alias `namespace` → `RecDef.Namespace` (`RecDef.java:353-373`).

| Attribute | Type   | Required | Notes                                                    |
| --------- | ------ | -------- | -------------------------------------------------------- |
| `prefix`  | xs:NCName | yes   | XML namespace prefix used in `tag` strings throughout.   |
| `uri`     | xs:anyURI | yes   | The namespace URI.  May be empty (`""`) in legacy files. |
| `schema`  | string    | no    | Optional XSD location hint.                              |

**Built-in namespaces** (always available, not declared on disk):

| Prefix | URI                                          | Defined in            |
| ------ | -------------------------------------------- | --------------------- |
| `xml`  | `http://www.w3.org/XML/1998/namespace`       | `RecDef.java:68`      |
| `xsi`  | `http://www.w3.org/2001/XMLSchema-instance`  | `RecDef.java:69`      |

`getNamespaceMap()` (`RecDef.java:140-148`) merges built-ins with declared
namespaces.

**Quirks**:

- Empty URI strings appear in the SMFR example (`<namespace prefix="xsl" uri=""/>`).
  These are accepted but produce non-conforming XML output if used as element
  namespaces.  A Go validator may warn.
- `schema` is mostly empty in the corpus; only legacy ICN/EDM-pre-5 used it.
- Duplicate prefixes: not validated.  Last-write-wins per `HashMap.put`.

---

## 5. The `<elem>` recursion — the central section

`<elem>` is the workhorse element.  It models both:

1. Output-tree nodes (under `<root>` and inside other `<elem>` children).
2. Reusable prototypes (under `<elems>` and `<templates>`).

XStream alias `elem` → `RecDef.Elem` (`RecDef.java:448-624`).

### 5.1 Attributes (in declared order)

All fields are `@XStreamAsAttribute`.

| Attribute      | Type      | Required | Default | Semantics                                                                                          |
| -------------- | --------- | -------- | ------- | -------------------------------------------------------------------------------------------------- |
| `label`        | string    | no       | —       | Human-readable label shown in GUI tree.                                                            |
| `tag`          | `Tag`     | **yes**  | —       | QName (`prefix:localName`) of the output element.  Parsed via `Tag.Converter` (`Tag.java:268`).    |
| `attr-groups`  | string    | no       | —       | Comma/space-delimited list of attribute-group names (see §6).  Note hyphenated XML name; field is `attrGroups` (`RecDef.java:457-459`). |
| `attrs`        | string    | no       | —       | Comma/space-delimited list of attribute *local-names* to pull from the top-level `<attrs>` block. |
| `elem-groups`  | string    | no       | —       | Comma/space-delimited list of element-group names.  XML name hyphenated; field is `elemGroups`.    |
| `elems`        | string    | no       | —       | Comma/space-delimited list of element *local-names* to pull from the top-level `<elems>` block.    |
| `required`     | xs:boolean | no      | `false` | Marks the element as mandatory (drives "requires mapping" badge in GUI).                          |
| `simple`       | xs:boolean | no      | `false` | Hints to the GUI that this is a leaf with text content only.                                       |
| `singular`     | xs:boolean | no      | `false` | Hints that only one instance is allowed (suppresses "duplicate" UI affordance — see `RecDefNode.isDuplicatePossible`). |
| `uriCheck`     | xs:boolean | no      | `false` | At runtime, validate the produced value is a syntactically valid URI (see `RecDefTree.resolve`).   |
| `hidden`       | xs:boolean | no      | `false` | Hide from default GUI view.                                                                       |
| `unmappable`   | xs:boolean | no      | `false` | Forbid the user from adding a NodeMapping here (used for refs that are wholly constant).          |
| `function`     | string    | no       | —       | Name of a `<mapping-function>` to apply as default code (see §11).                                  |
| `operator`     | `Operator`| no       | `ALL`   | Default cardinality operator (`ALL`/`FIRST`/`COMMA_DELIM`/`SEMI_DELIM`/`SPACE_DELIM`/`PIPE_DELIM`/`AS_ARRAY` — see `Operator.java`). |
| `initialValue` | string    | no       | —       | Convenience: equivalent to attaching a `<node-mapping inputPath="/constant">` with `groovyCode = "'<initialValue>'"`. Applied in `RecDefNode.java:110-117`. |
| `target`       | string    | no       | —       | Comma-delimited list of template tag-strings to deep-copy into this elem's children (see §5.6).    |
| `xsdDataType`  | string (curie) | no  | —       | XSD type curie (e.g. `xsd:date`) for the generated validation XSD's element type, and — since the recdef-semantics-generators feature — the datatype-property `rdfs:range`/`sh:datatype` emitted by `RdfsGenerator`/`ShaclGenerator`. Inert for the mapping engine (`RecDef.java:489-491`). |
| `xsdPattern`   | string (regex) | no  | —       | Regex constraining the element's text content in the generated XSD and in `ShaclGenerator`'s `sh:pattern`. Inert for the mapping engine (`RecDef.java:493-495`). |
| `xsdMinOccurs` | string (int) | no    | `"1"` if `required`, else `"0"` | Overrides the XSD/SHACL minimum cardinality otherwise derived from `required`. Inert for the mapping engine (`RecDef.java:497-499`). |
| `xsdMaxOccurs` | string (int or `"unbounded"`) | no | `"1"` if `singular`, else unbounded | Overrides the XSD/SHACL maximum cardinality otherwise derived from `singular`. Inert for the mapping engine (`RecDef.java:501-503`). |
| `subclassof`   | string (comma-delimited; curie **or** entity label) | no | — | Parent class(es) for the entity this elem declares, emitted as `rdfs:subClassOf` by `RdfsGenerator`. Uniquely among the generator-facing attributes in this table, each token follows the record-definition's own established convention of naming the parent by its `label=` attribute (e.g. `subclassof="PhysicalHumanMadeThing"`) rather than by curie; a token containing a `:` is still accepted and resolved as a plain curie (see `RecDefSemantics.uriForSubClassOf`). Inert for the mapping engine (`RecDef.java:508-510`). |
| `equivalentClass` | string (curie) | no | — | `owl:equivalentClass` target for the entity this elem declares, emitted by `RdfsGenerator`. Inert for the mapping engine (`RecDef.java:512-514`). |
| `subPropertyOf` | string (curie) | no  | —       | `rdfs:subPropertyOf` target for the property this elem declares, emitted by `RdfsGenerator`. Inert for the mapping engine (`RecDef.java:516-518`). |

**Unknown attributes are silently ignored** (XStream is in `ignoreUnknownElements` mode — `XStreamFactory.java:54`).  This is how `nodeid`, `rangenode_id`, `xsd_type`, `path`, `fieldType` (seen in ACE/SMFR/ICN files) survive — they are domain-specific annotations that do **not** map to any `RecDef.Elem` field today.  A Go re-implementation should **preserve unknown attributes** when round-tripping (e.g. into a generic `extra: map[string]string`).

**Named exceptions.**  `xsdDataType`, `xsdPattern`, `xsdMinOccurs`, `xsdMaxOccurs`, `subclassof`, `equivalentClass` and `subPropertyOf` (rows above) are *not* unknown attributes and are *not* ignored outright — the mapping/output pipeline (§1.2) still never reads them, but the four generators described in §1.4 do. Treat them as inert-but-load-bearing: safe to drop for a mapping-only re-implementation, but required for XSD/RDFS/SHACL/JSON-LD generation parity with the Java reference.

### 5.2 Nested children

Order matters for output emission, **not** for the model.  XStream collects:

| Child element  | Java field                              | Cardinality | Purpose                                       |
| -------------- | --------------------------------------- | ----------- | --------------------------------------------- |
| `<doc>`        | `Doc doc`                               | 0..1        | Inline documentation (see §8).               |
| `<attr>`       | `List<Attr> subattrs` (`@XStreamImplicit`) | 0..n   | Inline-defined attributes attached to this elem. |
| `<elem>`       | `List<Elem> subelements` (`@XStreamImplicit`) | 0..n | Recursive — child elements.                   |
| `<node-mapping>` | `NodeMapping nodeMapping`             | 0..1        | A default NodeMapping baked into the RecDef (rare). |
| `<assertion>`* | `Assertion assertion`                   | 0..1        | An inline assertion (rare; not actually serialized in any file in the corpus). |

\* The `assertion` field exists on `Elem` (`RecDef.java:498`) but I found no
on-disk example.  May be vestigial.

### 5.3 Worked example: the ACE template recursion

From `/home/kiivihal/PocketMapper/datahub/PocketMapper/work/adlib-loans__2026_05_05_11_25.sip.zip/ace_0.2.3_record-definition.xml`:

```xml
<templates>
  <elem attrs="rdf:about" label="Document" nodeid="" tag="crm:E31_Document">
    <elem attrs="rdf:resource" label="is identified by" path=""
          tag="crm:P1_is_identified_by" xsd_type="@id" target="crm:E42_Identifier"/>
    <elem attrs="rdf:resource" label="has title" path=""
          tag="crm:P102_has_title" xsd_type="@id" target="crm:E35_Title"/>
    <elem attrs="rdf:resource" label="has type" path=""
          tag="crm:P2_has_type" xsd_type="@id" target="crm:E55_Type"/>
   </elem>

  <elem attrs="rdf:about" label="Identifier" tag="crm:E42_Identifier">
    <elem attrs="rdf:datatype, xml:lang" label="has symbolic content" path=""
          tag="crm:P190_has_symbolic_content" xsd_type="string"/>
    <elem attrs="rdf:resource" label="has type" path="" tag="crm:P2_has_type" xsd_type="@id">
      <elem attrs="rdf:about" label="Type" rangenode_id="" tag="crm:E55_Type">
        <elem attrs="rdf:datatype, xml:lang" label="label" path=""
              tag="rdfs:label" xsd_type="string"/>
        <elem attrs="rdf:resource" label="has_type" path="" tag="rdf:type" xsd_type="@id"/>
      </elem>
    </elem>
    <elem attrs="rdf:datatype, xml:lang" label="has note" path=""
          tag="crm:P3_has_note" xsd_type="string"/>
  </elem>
  ...
</templates>

<root tag="rdf:RDF">
  <elem attrs="rdf:about" label="HumanMadeObject" nodeid="" tag="crm:E22_Human-Made_Object">
    <elem attrs="rdf:resource" label="is identified by" path=""
          tag="crm:P1_is_identified_by" xsd_type="@id" target="crm:E42_Identifier"/>
    ...
  </elem>
</root>
```

**Resolution walk** (per `Elem.resolve(...)`, `RecDef.java:557-609`):

1. The root `<elem tag="rdf:RDF">` is resolved at path `/`.
2. Its child `<elem tag="crm:E22_Human-Made_Object" attrs="rdf:about">` is
   visited:
   - `attrs="rdf:about"` is split, `rdf:about` is looked up in
     `recDef.attrs`, deep-copied, and appended to `attrList`.  The string
     `attrs="…"` is nulled out.
   - Inline child `<elem tag="crm:P1_is_identified_by" attrs="rdf:resource"
     target="crm:E42_Identifier"/>` is added to `elemList`.
3. That child is recursively resolved.  Because it has
   `target="crm:E42_Identifier"`, the **deep-copy** of the
   `crm:E42_Identifier` template is appended to its `elemList`
   (`RecDef.java:594-606`).
4. The deep-copy itself is then recursively resolved — meaning the nested
   `<elem tag="crm:E55_Type">` it contains is fully expanded with its own
   `rdfs:label` and `rdf:type` children.
5. `target` accepts a comma-delimited list; each name is looked up in the
   templates-by-tag map built at the start of `resolve()`
   (`RecDef.java:185-208`).

**Critically**, each `target=` reference produces an **independent deep copy**,
not a reference.  Modifying one expansion does not affect others.

**Tree diagram** for the `HumanMadeObject` subtree after resolution (only the
first few branches shown):

```
rdf:RDF
└── crm:E22_Human-Made_Object   [@rdf:about]
    ├── crm:P1_is_identified_by  [@rdf:resource]
    │   └── crm:E42_Identifier   [@rdf:about]              ← from template
    │       ├── crm:P190_has_symbolic_content  [@rdf:datatype, @xml:lang]
    │       ├── crm:P2_has_type   [@rdf:resource]
    │       │   └── crm:E55_Type  [@rdf:about]
    │       │       ├── rdfs:label   [@rdf:datatype, @xml:lang]
    │       │       └── rdf:type     [@rdf:resource]
    │       └── crm:P3_has_note   [@rdf:datatype, @xml:lang]
    ├── crm:P102_has_title       [@rdf:resource]
    │   └── crm:E35_Title …                                ← from template
    └── …
```

The same `crm:E42_Identifier` template is expanded under `P1_is_identified_by`
for HumanMadeObject, CuratedHolding, Collection, Work, ProductType,
Manifestation, Expression, VisualItem, Document, RecordSet and Record — each
gets its own copy.

### 5.4 Default prefix handling

Bare local names in `tag` attributes implicitly take `RecDef.prefix` as their
namespace prefix.  E.g. in MODS (`prefix="mods"`), `<elem tag="titleInfo">`
becomes `mods:titleInfo` after `Tag.defaultPrefix(...)` runs
(`Tag.java:120-127`).

`RecDef.prefix` flows through `Elem.resolve`, `Attr` lookup, `Path.withDefaultPrefix`,
and `OptList.resolve`.

### 5.5 The `attrs="…"` / `elems="…"` shorthand

Both fields take a comma- or space-delimited list (`DELIM = "[ ,]+"`, see
`RecDef.java:67`).  Each token is a local-name (or `prefix:localName`).  The
parser looks up the named `<attr>`/`<elem>` in the top-level `<attrs>`/`<elems>`
block, **deep-copies** it, and appends it to this element's resolved
attribute/element list.

Example: `<elem tag="titleInfo" attrs="type, supplied"
attr-groups="base-four, authority, top-level-a, top-level-b">`
(`test/mods/record_def_3.4.0.xml:88`).

After resolution:
- 2 attributes added by `attrs` (type, supplied)
- 4 attributes from `base-four` group + 3 from `authority` + 3 from
  `top-level-a` + 3 from `top-level-b` = 13 group-derived attributes

### 5.6 The `target="…"` template expansion

`target` is the **template** mechanism.  Distinct from `attr-groups`/`elem-groups`,
templates can themselves contain `target=` references.  The resolver checks
*up front* that every referenced name is present in the templates-by-tag map
(`RecDef.java:185-208`) — that check verifies existence only, not acyclicity.

Templates are themselves `<elem>` definitions under `<templates>`.  They are
keyed by `tag.toString()` (so `crm:E42_Identifier`, not just `E42_Identifier`).

A template reference performs `template.deepCopy()` and appends the copy as a
child of the referrer.  The deep-copy is then recursively `resolve()`d, so
chained templates compose correctly.

**Cyclic references — expand once per branch.**  Template cycles are a
legitimate authoring pattern: a shared elem-group can give every entity a
`has-type -> crm:E55_Type` link, including the `crm:E55_Type` template itself.
Blind expansion would recurse forever, so `Elem.resolve` skips a `target`
whose template tag is already an **ancestor on the current path**: each entity
expands once per branch, and deeper self-nesting adds nothing a mapping could
target.  Both direct (`A -> A`) and indirect (`A -> B -> A`) cycles therefore
terminate silently after one round.  Note the ancestor check compares
*pre-prefix-defaulting* tag strings, matching how the path is built during
resolution.

**Depth backstop.**  Independently of the ancestor skip, `Elem.resolve` fails
with a catchable `IllegalStateException` (naming the offending path) once
element depth exceeds 100.  This converts any remaining runaway recursion —
e.g. an elem-group whose member re-injects the same group, which the
ancestor skip does not cover — into a normal error instead of a JVM-killing
`StackOverflowError`.  Legitimate record definitions nest well below this
limit.  Covered by `RecDefTemplateCycleTest`.

### 5.7 What is a "leaf"

`RecDefNode.isLeafElem()` (`RecDefNode.java:219-221`) returns true iff the
element has zero child elements after resolution.  Attribute children do not
count.  The GUI treats leaves specially for code editing (only leaves and
attributes are user-code-editable, per `NodeMapping.isUserCodeEditable`).

---

## 6. `<attr>` definitions

XStream alias `attr` → `RecDef.Attr` (`RecDef.java:375-415`).

### 6.1 Top-level `<attrs>` block (reusable prototypes)

```xml
<attrs>
  <attr tag="rdf:about" uriCheck="true"/>
  <attr tag="rdf:resource"/>
  <attr tag="rdf:datatype"/>
  <attr tag="xml:lang"/>
</attrs>
```

These are **prototypes**; they are not attached to any element until a
particular `<elem attrs="…"/>` or `<attr-group>` references them by tag local
name.  At reference time, a deep copy is appended (`RecDef.attr`,
`RecDef.java:150-156`).

### 6.2 `<attr>` attributes

All `@XStreamAsAttribute`:

| Attribute      | Type      | Required | Default | Notes                                                                            |
| -------------- | --------- | -------- | ------- | -------------------------------------------------------------------------------- |
| `tag`          | `Tag`     | **yes**  | —       | Parsed via `Tag.AttributeConverter` (`RecDef.java:378-380`, `Tag.java:281`).      |
| `required`     | xs:boolean | no      | `false` | Marks attribute as mandatory.                                                    |
| `simple`       | xs:boolean | no      | `false` | (Mirrors `Elem.simple`.)                                                         |
| `uriCheck`     | xs:boolean | no      | `false` | Runtime URI syntax check on the produced value.                                  |
| `hidden`       | xs:boolean | no      | `false` | Hide from GUI.                                                                   |
| `initialValue` | string    | no       | —       | Becomes a constant `node-mapping` (cf. §5.1).                                    |

### 6.3 Nested children of `<attr>`

| Child            | Java field                | Cardinality | Notes                                       |
| ---------------- | ------------------------- | ----------- | ------------------------------------------- |
| `<node-mapping>` | `NodeMapping nodeMapping` | 0..1        | Default NodeMapping (e.g. a constant URI builder). |

Example from `edm_5.2.6_record-definition.xml:368-374`:

```xml
<attr tag="rdf:about" uriCheck="true">
  <node-mapping inputPath="/input">
    <groovy-code>
      <string>"${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}"</string>
    </groovy-code>
  </node-mapping>
</attr>
```

### 6.4 Inline `<attr>` on an `<elem>`

`<attr>` may appear as a direct child of `<elem>` — see SMFR
(`<attr uriCheck="True" tag="rdf:about"/>` inside several elements at
lines 43, 64, 98, …).  This collects into `Elem.subattrs` and is appended to
`Elem.attrList` at resolve time.

### 6.5 `<attr-groups>` / `<attr-group>`

XStream alias `attr-groups` → `List<AttrGroup>`, `attr-group` → `AttrGroup`
(`RecDef.java:110-111`, `417-446`).

```xml
<attr-groups>
  <attr-group name="base-four">
    <string>lang</string>
    <string>xml:lang</string>
    <string>script</string>
    <string>transliteration</string>
  </attr-group>
  ...
</attr-groups>
```

Note: members are bare `<string>` elements (`@XStreamImplicit` on
`List<String> tags`, `RecDef.java:422-423`).  Each string is parsed as an
attribute tag with the record-definition's default prefix.

A `<attr-group>` is resolved (`AttrGroup.resolve`, `RecDef.java:428-433`) by
looking each name up in `recDef.attrs` and storing the resolved `Attr` list on
`attrs` (the field, not the XML attribute).  When an `<elem attr-groups="…"/>`
references the group, the resolved list is deep-copied in.

| Attribute | Type     | Required | Notes              |
| --------- | -------- | -------- | ------------------ |
| `name`    | string   | **yes**  | Group identifier.  |

### 6.6 `<elem-groups>` / `<elem-group>`

Same shape as `<attr-groups>` but for elements
(`RecDef.java:115-116`, `626-645`).

```xml
<elem-groups>
  <elem-group name="labels">
    <elem tag="skos:prefLabel" attrs="xml:lang"/>
    <elem tag="skos:altLabel" attrs="xml:lang"/>
  </elem-group>
  ...
</elem-groups>
```

Members are full `<elem>` definitions (not just name strings).  Referenced via
`<elem elem-groups="labels"/>`.  At resolve time, each member is `deepCopy()`d
and appended.

| Attribute | Type     | Required | Notes              |
| --------- | -------- | -------- | ------------------ |
| `name`    | string   | **yes**  | Group identifier.  |

Members may carry their own inline `<doc>` blocks, which travel with the deep
copy (see `test/resources/recdef/inline-docs-recdef.xml`).

---

## 7. `<opt>` / `<opt-list>` / `<opt-role>`

XStream alias `opt-list` → `OptList` (`OptList.java:37`).  Containing wrapper:
`<opts>` → `List<OptList>` field on `RecDef` (`RecDef.java:122`).

### 7.1 `<opt-list>` shape

```xml
<opt-list dictionary="LEGAL"
          path="/RDF/nave:RijksCollection/nave:legalStatus"
          displayName="Legal Status">
  <opt value="Eigenaar: Rijks"/>
  <opt value="Eigenaar: Anders dan Rijk"/>
  <opt value="Aan de zorg van het Rijk toevertrouwd"/>
  ...
</opt-list>
```

| Attribute      | Type      | Required | Notes                                                                                        |
| -------------- | --------- | -------- | -------------------------------------------------------------------------------------------- |
| `dictionary`   | string    | **yes**  | Unique identifier; `OptList.resolve` throws if missing (`OptList.java:67`).                 |
| `path`         | `Path`    | **yes**  | XPath-like absolute path to the *root* element of the opt's anchor (no attribute allowed; see `OptList.java:68`). |
| `value`        | `Tag`     | no       | If absent, defaults to `path.peek()` (the root anchor itself becomes the value-bearing node). If present, must be a descendant tag. Marks `valuePresent=true`. |
| `key`          | `Path`    | no       | Path to a key node descended from `path`.                                                    |
| `schema`       | `Path`    | no       | Path to a schema-name slot descended from `path`.                                            |
| `schemaUri`    | `Path`    | no       | Path to a schema-URI slot descended from `path`.                                             |
| `displayName`  | string    | no       | Human-readable label (not a model field — it's silently retained as XStream attribute).      |

`displayName` is not declared in `OptList.java`; it survives because XStream
ignores unknown attributes.  Treat it as a UI hint.

### 7.2 `<opt>` shape

`@XStreamImplicit List<Opt> opts` (`OptList.java:58`).  Each `<opt>`:

| Attribute    | Type   | Required | Notes                                |
| ------------ | ------ | -------- | ------------------------------------ |
| `value`      | string | usually  | The displayed/emitted value.         |
| `key`        | string | optional | Internal key (alternative to value). |
| `schema`     | string | optional | Schema name for this option.         |
| `schemaUri`  | string | optional | Schema URI for this option.          |

`Opt.toString()` returns `value` if non-null, otherwise `key`
(`OptList.java:124-126`).  A `hidden` attribute is sometimes written
(`test/mods/record_def_3.4.0.xml:325`); it is **silently ignored** by
`OptList.Opt` (no field).  Round-trippers should preserve it.

### 7.3 Resolution semantics

`OptList.resolve(RecDef)` (`OptList.java:64-85`):

1. Sets `opt.parent = this` for each `Opt`.
2. Verifies `path` and `dictionary` are present.
3. Forbids `path` ending in an attribute.
4. Prefixes `path` with the default prefix.
5. If `value` is absent, defaults it to the last segment of `path`; otherwise
   sets `valuePresent = true`.
6. Resolves `key`, `schema`, `schemaUri` (each relative to `path`) and verifies
   they point to either an element or an attribute that exists in the tree.
7. Finds the target `Elem` (`recDef.findElem(path)`) and stores `elem.optList = this`.
8. Builds `recDef.valueOptLookup[dictionary]` = `{value → Opt}` for fast
   runtime lookup during code generation.

### 7.4 `OptRole`

`OptRole.java:28-50` defines six roles:

```
ROOT, CHILD, KEY, VALUE, SCHEMA, SCHEMA_URI
```

The roles are *runtime* concepts assigned to `OptBox` instances as the
`RecDefTree` walks the resolved tree.  They are not serialized.  The role
controls how generated Groovy code references opt values (cf.
`OptBox.getInnerOptReference`, `OptBox.java:75-81`).

### 7.5 `DynOpt`

`DynOpt.java:27`:

```xml
<dyn-opt path="/some/path" value="someValue"/>
```

`DynOpt` is **not** part of the record-definition.  It is stored inside a
`RecMapping` (per-dataset) and extends the static `OptList` set with
user-added entries at runtime.  Listed here because the spec mentioned it: a
Go re-implementation of the *record-definition* parser can ignore `DynOpt`.

---

## 8. `<doc>` blocks

XStream alias `doc` → `RecDef.Doc` (`RecDef.java:292-342`).

`Doc` instances appear in **two** places:

1. **Top-level under `<docs>`** — most common; allows reference by path or tag.
2. **Inline under an `<elem>` or `<attr>`** — see
   `recdef/inline-docs-recdef.xml`.

### 8.1 Attributes

| Attribute | Type   | Required | Notes                                                              |
| --------- | ------ | -------- | ------------------------------------------------------------------ |
| `tag`     | `Tag`  | one of   | Tag-based lookup; matches every `Elem` (or `Attr`, if `tag.isAttribute()`) with this tag, anywhere in the tree. |
| `path`    | `Path` | one of   | Path-based lookup; resolves a single specific node.                |

`Doc.resolve(RecDef)` (`RecDef.java:309-327`) requires **exactly one** of
`path` or `tag` to be present; throws otherwise.  Path-based docs trump
tag-based for specificity, and either type **overrides** any inline `<doc>` on
the matched element (verified by `recdef/inline-docs-recdef.xml`).

### 8.2 Body

Two mutually-exclusive content models:

#### 8.2.1 `<para>` paragraphs (current, recommended)

```xml
<doc path="/RDF/ore:Aggregation">
  <para name="Label">Aggregation</para>
  <para name="Definition">The set of resources related to a single cultural heritage object…</para>
  <para name="Obligation and Occurrence">…</para>
  <para name="Example">…</para>
</doc>
```

`@XStreamImplicit(itemFieldName="para") List<DocParagraph> paraList`
(`RecDef.java:304-305`).  `DocParagraph` (`RecDef.java:344-354`) uses
`ToAttributedValueConverter` so the paragraph **text content** becomes the
`content` field and the `name="…"` becomes the `name` attribute.

`<para>` also carries an optional `lang="…"` attribute (`DocParagraph.lang`,
`RecDef.java:350-351`), an IETF language tag (`en`, `nl`, …) for the
paragraph's content — same convention as `xml:lang` elsewhere in the format,
but as a plain (non-namespaced) XStream attribute on `<para>` itself rather
than an inherited XML `xml:lang`. `lang` is optional for free-form paragraphs
(`Obligation and Occurrence`, `Example`, `Note`, …), which are display-only
and untyped by language as far as the Java implementation is concerned. It
is, however, **load-bearing** for the two names the recdef-semantics-
generators feature reads: `RecDefSemantics.labelsOf`/`definitionsOf`
(`sip-core/.../RecDefSemantics.java:172-191`) collect only `<para
name="Label" lang="…">`/`<para name="Definition" lang="…">` entries **that
have a `lang`** into a `{lang → text}` map, keyed by tag on whichever `<elem>`
the `<doc>` resolved onto. `RdfsGenerator` emits one `rdfs:label`/
`rdfs:comment` triple per language present (`RdfsGenerator.java:85-90`,
`135-140`) — for both entity classes and properties. A `Label`/`Definition`
paragraph with **no** `lang` attribute is silently invisible to the
generators (parsed as a doc paragraph like any other, but never reaches the
ontology) — this is a common source of "why didn't my label show up in
ontology.ttl" confusion when porting older record-definitions that predate
this convention.

```xml
<para name="Note"><![CDATA[CDATA-wrapped HTML or special chars allowed here]]></para>
```

Paragraphs commonly carry CDATA-wrapped content with embedded HTML entities,
URLs, and code snippets (see EDM lines 1047-1064 for examples with embedded
HTML and special chars).

`@XStreamImplicit` means the `<para>` elements are direct children of `<doc>`
with no enclosing list wrapper.

There is a **second** field `public List<DocParagraph> paras` (`RecDef.java:307`,
no `@XStreamImplicit`) which expects a wrapping `<paras>` element.  See
`test/mods/record_def_3.4.0.xml:371-385` for an example using `<paras>…</paras>`.
**Both wire forms are accepted on read but only one will be populated** based
on which XML wrapper is present.  This is a known XStream quirk; a Go writer
should pick one form (the implicit form is more common in the corpus).

#### 8.2.2 `<string>` lines (legacy)

```xml
<doc tag="foo">
  <string>One line</string>
  <string>Another line</string>
</doc>
```

`@XStreamImplicit(itemFieldName="string") List<String> lines`
(`RecDef.java:301-302`).  Used by older record-definitions; the corpus shows
this in MODS docs as a legacy form.

### 8.3 Embedded HTML / Markdown

`<para>` content is raw text; if it contains HTML, that HTML is preserved
verbatim and rendered by the GUI's HTML pane.  No sanitisation.  When
serializing to a non-XML format (JSON, Protobuf), preserve byte-for-byte to
avoid lossy round-trips.

### 8.4 Documentation `<para>` name conventions

Para `name` values are free-form strings, but EDM/SMFR have settled on the
following palette (cf. EDM lines 1008-1024):

```
URI, Label, Definition, Super-property of, Subproperty of,
Subclass of, Equivalent Class, Domain, Range, Equivalent property,
Obligation and Occurrence, Example, Rationale, Comment, Note,
Value type, Cardinality
```

Treat as convention, not schema — with one exception: `Label` and
`Definition` (each with a `lang` attribute, see §8.2.1) are the two names the
`RdfsGenerator` generator actually reads to emit `rdfs:label`/`rdfs:comment`.
The rest of the palette (`Obligation and Occurrence`, `Example`, `Rationale`,
…) is display-only in the Java implementation today.

---

## 9. `<field-markers>` / `<field-marker>`

XStream alias `field-markers` → `List<FieldMarker>` (`RecDef.java:127-128`)
and `field-marker` → `RecDef.FieldMarker` (`RecDef.java:259-290`).

```xml
<field-markers>
  <field-marker name="CREATOR" path="/record/dc:creator"/>
  <field-marker name="THUMBNAIL" path="/record/europeana:object" check="THUMBNAIL"/>
  <field-marker name="LANDING_PAGE" path="/record/europeana:isShownAt" check="LANDING_PAGE"/>
  <field-marker type="search" name="SNIPPET" path="/record/delving:fullText"/>
  <field-marker type="fact" name="spec" path="/record/europeana:collectionName"/>
</field-markers>
```

### 9.1 Attributes

| Attribute | Type   | Required | Notes                                                          |
| --------- | ------ | -------- | -------------------------------------------------------------- |
| `name`    | string | no       | Identifier used by indexing/display layers.                    |
| `type`    | string | no       | Free-form; observed values `"search"`, `"fact"`. (Default ≡ "system".) |
| `path`    | `Path` | no       | Output path this marker applies to.                            |
| `xpath`   | string | no       | Alternative XPath expression (used by `getXPath()`).            |
| `check`   | `Check`| no       | Enum: `LANDING_PAGE`, `DIGITAL_OBJECT`, `THUMBNAIL`, `DEEP_ZOOM`, `THESAURUS_REFERENCE`, `LOD_REFERENCE`, `GEO_COORDINATE`, `DATE` (`RecDef.java:240-257`). |

`Check` carries `(fetch, captureSize)` booleans that drive runtime behaviour
(should the SIP Creator fetch this URL? should it capture image dimensions?).

### 9.2 Purpose

Field markers tell downstream services (Hub3, Narthex, indexer):
- "this path is the THUMBNAIL field" — used for image-fetch
- "this path is a fact-level field" — promoted to dataset metadata
- "this path is a snippet field" — included in search highlighting

They have **no effect** inside the SIP-Creator itself; they are passed through
to the consuming platform.

A `field-marker` with no `path`/`xpath` and only a `name` is still meaningful
(e.g. ICN's `<field-marker name="SPEC"/>` at line 167) — it indicates that the
named role *should* exist but is to be resolved elsewhere.

---

## 10. `<assertion-list>` / `<assert>`

XStream aliases (`Assertion.java:29`, `72`):
- `<assertion-list>` → `Assertion.AssertionList`
- `<assert>` → `Assertion`

```xml
<assertion-list>
  <assert xpath="//mods:namePart/text()">
    <condition>it =~ /Florida/</condition>
    <on-fail>No florida found in the string $it</on-fail>
  </assert>
  <assert xpath="//mods:topic/text()">
    <allowed>
      <string>Text</string>
      <string>Geology</string>
    </allowed>
    <on-fail>Improper value: $it</on-fail>
  </assert>
  <assert xpath="/mods:mods/mods:subject/@authority">
    <condition>it == 'divine'</condition>
    <on-fail>Authority '$it' is not divine!</on-fail>
  </assert>
  <assert xpath="/mods:mods/mods:typeOfResource/text()">
    <on-fail>Type of resource is empty!</on-fail>
  </assert>
  <assert xpath="//some/path">
    <code>
      if (!(it =~ /Florida/)) {
        "No florida found in $it"
      }
    </code>
  </assert>
</assertion-list>
```

Source: `sip-app/src/test/resources/assertion/assertion-list.xml`.

### 10.1 Attributes of `<assert>`

| Attribute | Type   | Required | Notes                                                       |
| --------- | ------ | -------- | ----------------------------------------------------------- |
| `xpath`   | string | **yes**  | XPath against the output DOM, evaluated per record.         |

### 10.2 Children of `<assert>` (mutually exclusive content models)

| Child         | Java field             | Notes                                                              |
| ------------- | ---------------------- | ------------------------------------------------------------------ |
| `<condition>` | `String condition`     | Groovy boolean expression; `it` is the XPath result. On false → fail. |
| `<allowed>`   | `List<String> allowed` | Whitelist of allowed string values (`<string>…</string>` children). |
| `<code>`      | `String code`          | Raw Groovy block; should return failure-message string or empty.    |
| `<on-fail>`   | `String onFail`        | Failure-message template (`$it` interpolates).                      |

`Assertion.getScript()` (`Assertion.java:47-69`) compiles the assertion into
the canonical Groovy form:

- `code` wins if present → used verbatim.
- Otherwise `condition` → `if (!(<condition>)) { "<onFail>" }`.
- Otherwise `allowed` → `if (!(it.toString() in [<allowed>])) { "<onFail>" }`.
- Otherwise → empty string.

If all three (`code`, `condition`, `allowed`) are absent, the assertion can
still fail when `xpath` evaluates empty (the runtime treats an empty match as a
failure with the `onFail` message — see the last example above).

### 10.3 Severity

There is **no severity field**.  All assertion failures are equal.  Downstream
treatment is decided by the runner.

---

## 11. `<functions>` / `<mapping-function>`

XStream alias `mapping-function` → `MappingFunction` (`MappingFunction.java:39`).

`@XStreamImplicit` is not used; the top-level wrapper is `<functions>` (matching
the field name `List<MappingFunction> functions`, `RecDef.java:106`).

### 11.1 Shape

```xml
<functions>
  <mapping-function name="cleanAdlibImageReference">
    <sample-input>
      <string>..\..\..\Images PH\OKS 1989-001 [01].JPG</string>
    </sample-input>
    <groovy-code>
      <string>it.replaceAll(...)...replaceAll(...)</string>
    </groovy-code>
  </mapping-function>
  <mapping-function name="createOreAggregationUri">
    <documentation>
      <string>Builds the canonical aggregation URI for an item.</string>
    </documentation>
    <sample-input>
      <string>00001</string>
    </sample-input>
    <groovy-code>
      <string>"${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}"</string>
    </groovy-code>
  </mapping-function>
</functions>
```

### 11.2 Attributes

| Attribute | Type   | Required | Notes                                |
| --------- | ------ | -------- | ------------------------------------ |
| `name`    | string | **yes**  | Function identifier; used in `<elem function="…"/>`. |

### 11.3 Children

| Child             | Java field                          | Notes                                                                  |
| ----------------- | ----------------------------------- | ---------------------------------------------------------------------- |
| `<sample-input>`  | `List<String> sampleInput`          | Each `<string>` child is one sample line.                              |
| `<documentation>` | `List<String> documentation`        | One line per `<string>` child.                                         |
| `<groovy-code>`   | `List<String> groovyCode`           | Function body; one line per `<string>` child. If the lines form a full closure assignment (`def name = { … }`) it is used verbatim; otherwise it is wrapped as `def <name> = { it -> <body> }` (see `MappingFunction.toCode`, `MappingFunction.java:118-129`). |

### 11.4 Lines vs. strings

Multi-line code is encoded as **one `<string>` per source line**
(`StringUtil.stringToLines` does the split, `StringUtil.linesToString` the
join).  Newlines inside a single `<string>` are not preserved.  This is one of
the most XStream-specific artefacts of the format and must be reproduced
faithfully.

### 11.5 Standalone function-list file

`MappingFunction.FunctionList` (`MappingFunction.java:176-180`) is a separate
top-level type aliased to `<mapping-function-list>` for user-private function
files (not part of the record-definition).  Listed here for completeness; not
embedded in a record-definition.

---

## 12. Complete grammar (EBNF-style)

```
record-definition ::= "<record-definition"
                        prefix-attr
                        version-attr
                        [ flat-attr ]
                        [ element-form-attr ]
                        [ attribute-form-attr ]
                      ">"
                        [ namespaces ]
                        [ functions ]
                        [ attrs-block ]
                        [ attr-groups ]
                        [ elems-block ]
                        [ elem-groups ]
                        [ templates ]
                        root
                        [ opts ]
                        [ assertion-list ]
                        [ field-markers ]
                        [ docs ]
                      "</record-definition>"

namespaces       ::= "<namespaces>" namespace* "</namespaces>"
namespace        ::= "<namespace" prefix-attr uri-attr [schema-attr] "/>"

functions        ::= "<functions>" mapping-function* "</functions>"
mapping-function ::= "<mapping-function" name-attr ">"
                       [ sample-input ]
                       [ documentation ]
                       [ groovy-code ]
                     "</mapping-function>"
sample-input     ::= "<sample-input>" line* "</sample-input>"
documentation    ::= "<documentation>" line* "</documentation>"
groovy-code      ::= "<groovy-code>" line* "</groovy-code>"
line             ::= "<string>" text "</string>"

attrs-block      ::= "<attrs>" attr-def* "</attrs>"
attr-def         ::= "<attr" attr-tag-attr
                            [ required-attr ]
                            [ simple-attr ]
                            [ uricheck-attr ]
                            [ hidden-attr ]
                            [ initial-value-attr ]
                     (">" [ node-mapping ] "</attr>" | "/>")

attr-groups      ::= "<attr-groups>" attr-group* "</attr-groups>"
attr-group       ::= "<attr-group" name-attr ">" string-list "</attr-group>"
string-list      ::= "<string>" tag-string "</string>" +

elems-block      ::= "<elems>" elem-def* "</elems>"
elem-def         ::= elem-template       (* identical to the recursive <elem> below *)

elem-groups      ::= "<elem-groups>" elem-group* "</elem-groups>"
elem-group       ::= "<elem-group" name-attr ">" elem-def+ "</elem-group>"

templates        ::= "<templates>" elem-template+ "</templates>"

root             ::= "<root" tag-attr [ elem-shorthand-attrs ] ">"
                       elem-child*
                     "</root>"

elem-template    ::= "<elem"
                       [ label-attr ]
                       tag-attr
                       [ attr-groups-attr ]
                       [ attrs-attr ]
                       [ elem-groups-attr ]
                       [ elems-attr ]
                       [ required-attr ]
                       [ simple-attr ]
                       [ singular-attr ]
                       [ uricheck-attr ]
                       [ hidden-attr ]
                       [ unmappable-attr ]
                       [ function-attr ]
                       [ operator-attr ]
                       [ initial-value-attr ]
                       [ target-attr ]
                       [ extension-attrs ]   (* see note *)
                     (
                       "/>"
                       |
                       ">" elem-child* "</elem>"
                     )

elem-child       ::= doc | attr-def | elem-template | node-mapping | assertion

doc              ::= "<doc" ( path-attr | tag-attr ) ">"
                       (para+ | "<paras>" para+ "</paras>" | string-line+)
                     "</doc>"
para             ::= "<para" name-attr ">" text-or-cdata "</para>"
string-line      ::= "<string>" text "</string>"

node-mapping     ::= "<node-mapping"
                       input-path-attr
                       [ output-path-attr ]
                       [ operator-attr ]
                     ">"
                       [ siblings ]
                       [ dictionary ]
                       [ groovy-code ]
                       [ documentation ]
                     "</node-mapping>"

opts             ::= "<opts>" opt-list* "</opts>"
opt-list         ::= "<opt-list"
                       dictionary-attr
                       path-attr
                       [ value-attr ]
                       [ key-attr ]
                       [ schema-attr ]
                       [ schema-uri-attr ]
                       [ display-name-attr ]
                     ">"
                       opt+
                     "</opt-list>"
opt              ::= "<opt"
                       [ key-attr ]
                       [ value-attr ]
                       [ schema-attr ]
                       [ schema-uri-attr ]
                     "/>"

assertion-list   ::= "<assertion-list>" assertion* "</assertion-list>"
assertion        ::= "<assert" xpath-attr ">"
                       ( "<condition>" groovy-expr "</condition>"
                       | "<allowed>" string-line+ "</allowed>"
                       | "<code>" groovy-block "</code>"
                       | )            (* all optional *)
                       [ "<on-fail>" text "</on-fail>" ]
                     "</assert>"

field-markers    ::= "<field-markers>" field-marker* "</field-markers>"
field-marker     ::= "<field-marker"
                       [ name-attr ]
                       [ type-attr ]
                       [ path-attr ]
                       [ xpath-attr ]
                       [ check-attr ]
                     "/>"

docs             ::= "<docs>" doc* "</docs>"
```

**Notes**:
- `extension-attrs` covers `nodeid`, `rangenode_id`, `xsd_type`, `path`,
  `fieldType` and any other unknown `<elem>`-level attributes seen in the
  corpus.  XStream ignores them; a faithful parser must preserve them.
- `check-attr` value is one of the `RecDef.Check` enum names.
- `operator-attr` value is one of the `Operator` enum names.

---

## 13. Serialization recommendations

### 13.1 Direct XML port (preserve current format)

**Pros**
- Lossless on the wire.
- Existing record-definition files at https://schemas.delving.eu work unchanged.
- Trivial interop with the Java SIP-Creator and existing SIP archives.

**Cons**
- XStream-isms are fiddly (`@XStreamImplicit`, `ToAttributedValueConverter`
  for `<para>`, attribute-vs-element ambiguity, line-per-`<string>` encoding).
- Two parallel encodings for the same data (`<para>` directly vs. wrapped in
  `<paras>`; `attrs="…"` shorthand vs. nested `<attr/>`) — must support both
  on read.
- Order-sensitive serialization of unknown attributes is hard in most XML
  libraries; Go's `encoding/xml` will reorder.
- A browser editor would need to ship an XSLT/Go-XML re-emitter.

**Recommendation**: write an **XSD 1.1** schema for `<record-definition>`
describing the canonical form (the more restrictive of the two encodings for
each ambiguous case).  Use that as the source of truth.  Parser
implementations validate against it.

### 13.2 JSON mirror (lossless 1:1)

A JSON object representation that mirrors the XML 1:1 would look like:

```json
{
  "prefix": "ace",
  "version": "0.2.3",
  "flat": false,
  "namespaces": [
    { "prefix": "rdf", "uri": "http://www.w3.org/1999/02/22-rdf-syntax-ns#" },
    { "prefix": "ace", "uri": "https://data.antwerp.be/ns/cultureel-erfgoed#" }
  ],
  "attrs": [
    { "tag": "rdf:about", "uriCheck": true },
    { "tag": "rdf:resource" }
  ],
  "templates": [
    {
      "tag": "crm:E31_Document",
      "label": "Document",
      "attrs": "rdf:about",
      "elems": [
        {
          "tag": "crm:P1_is_identified_by",
          "label": "is identified by",
          "attrs": "rdf:resource",
          "target": "crm:E42_Identifier",
          "extra": { "path": "", "xsd_type": "@id" }
        }
      ]
    }
  ],
  "root": {
    "tag": "rdf:RDF",
    "elems": [
      {
        "tag": "crm:E22_Human-Made_Object",
        "label": "HumanMadeObject",
        "attrs": "rdf:about",
        "elems": [ … ]
      }
    ]
  },
  "opts": [
    {
      "dictionary": "LEGAL",
      "path": "/RDF/nave:RijksCollection/nave:legalStatus",
      "displayName": "Legal Status",
      "opts": [
        { "value": "Eigenaar: Rijks" },
        { "value": "Onbekend" }
      ]
    }
  ],
  "docs": [
    {
      "path": "/RDF/ore:Aggregation",
      "paras": [
        { "name": "Label", "content": "Aggregation" },
        { "name": "Definition", "content": "The set of resources …" }
      ]
    }
  ],
  "fieldMarkers": [
    { "name": "CREATOR", "path": "/record/dc:creator" }
  ],
  "assertions": [
    {
      "xpath": "//mods:namePart/text()",
      "condition": "it =~ /Florida/",
      "onFail": "No florida found in $it"
    }
  ],
  "functions": [
    {
      "name": "cleanAdlibImageReference",
      "groovyCode": "it.replaceAll('; ', '_')\n  .replaceAll('JPG', 'jpg')"
    }
  ]
}
```

**Key choices**:
- Use a single `elems` child array for nested elements (drop the
  shorthand-string form `attrs="a, b"` in favour of always-expanding arrays of
  prototypes).  But: **keep the string form for `attrs`/`elems`/`attrGroups`/`elemGroups`** on `<elem>` because that is the *interesting* compact form
  the Java code uses to reduce repetition.  Decompose it only at resolve-time.
- `groovyCode` and `documentation` and `sampleInput` become single strings
  (multi-line, newline-joined) instead of arrays of one-line `<string>`s.
- Move all unknown XML attributes to a per-node `extra: {string: string}` map
  to retain round-trip fidelity.
- Replace `<para>` mixed-content with `{ name, content }` objects.

**Pros**: native to JS, easy to fetch in the browser, no XStream quirks.

**Cons**: requires a one-shot conversion of every record-definition on disk;
existing Java code would need a JSON loader (or a JSON→XML adaptor).

### 13.3 Protobuf

The natural mapping is straightforward for most of the format, but several
fields are awkward:

- `<para>` mixed content with embedded HTML/CDATA — Protobuf has no native
  mixed-content; either use a `string content` (and pre-render to HTML) or a
  repeated structured `Run` message.  Lossy if the HTML changes shape.
- Order-dependent unknown attributes — Protobuf `map<string, string>` doesn't
  preserve order.  Use `repeated KeyValue` if order matters.
- Attribute-vs-element ambiguity — easy in Protobuf (everything is a field)
  but requires choosing one encoding per attribute, breaking round-trip with
  the XML.

Sample proto (illustrative, not exhaustive):

```proto
syntax = "proto3";
package delving.recdef.v1;

message RecordDefinition {
  string prefix = 1;
  string version = 2;
  bool flat = 3;
  bool element_form_default_qualified = 4;
  bool attribute_form_default_qualified = 5;
  repeated Namespace namespaces = 6;
  repeated MappingFunction functions = 7;
  repeated Attr attrs = 8;
  repeated AttrGroup attr_groups = 9;
  repeated Elem elems = 10;
  repeated ElemGroup elem_groups = 11;
  repeated Elem templates = 12;
  Elem root = 13;
  repeated OptList opts = 14;
  repeated Assertion assertions = 15;
  repeated FieldMarker field_markers = 16;
  repeated Doc docs = 17;
}

message Namespace { string prefix = 1; string uri = 2; string schema = 3; }

message Attr {
  string tag = 1;
  bool required = 2;
  bool simple = 3;
  bool uri_check = 4;
  bool hidden = 5;
  string initial_value = 6;
  NodeMapping node_mapping = 7;
}

message Elem {
  string tag = 1;
  string label = 2;
  // shorthand refs (resolved to attrs/elems below at runtime)
  string attrs_ref = 3;
  string elems_ref = 4;
  string attr_groups_ref = 5;
  string elem_groups_ref = 6;
  bool required = 7;
  bool simple = 8;
  bool singular = 9;
  bool uri_check = 10;
  bool hidden = 11;
  bool unmappable = 12;
  string function = 13;
  Operator operator = 14;
  string initial_value = 15;
  string target = 16;
  repeated Attr inline_attrs = 17;
  repeated Elem inline_elems = 18;
  NodeMapping default_node_mapping = 19;
  Doc inline_doc = 20;
  map<string, string> extra = 21;  // for unknown attrs
}

enum Operator {
  OPERATOR_UNSPECIFIED = 0;
  OPERATOR_ALL = 1;
  OPERATOR_FIRST = 2;
  OPERATOR_COMMA_DELIM = 3;
  OPERATOR_SEMI_DELIM = 4;
  OPERATOR_SPACE_DELIM = 5;
  OPERATOR_PIPE_DELIM = 6;
  OPERATOR_AS_ARRAY = 7;
}

message Doc {
  oneof anchor { string path = 1; string tag = 2; }
  repeated Para paras = 3;
  repeated string lines = 4;  // legacy <string> form
}
message Para { string name = 1; string content = 2; }
// … and so on
```

**Pros**: deterministic binary form, efficient on the wire, strong codegen.

**Cons**: poor fit for an editor surface (browser would need
protobuf-js + a viewer); harder to author by hand; lossy for `<para>` HTML and
unknown attributes.

### 13.4 Recommendation

**Go with JSON as the in-memory and editor format, with a lossless XML
import/export adapter.**  Reasoning:

1. The browser-side editor is the long-term front-end.  JSON is native to JS,
   trivially diff/merge-able, and parsable by Go's `encoding/json`.
2. The corpus of existing `_record-definition.xml` files at
   schemas.delving.eu must keep working, so a deterministic two-way XML ↔ JSON
   converter is mandatory anyway.
3. Treat JSON as the source of truth for new authoring; keep the XML form as
   the canonical interchange with legacy SIP-Creator (write XML from JSON when
   building a sip.zip, parse XML to JSON when loading one).
4. Protobuf is overkill for an artefact that is read once per dataset and
   ranges from 10 KB (ACE) to 90 KB (EDM) on disk.

Concretely, the Go re-implementation should ship:

- `recdef.LoadXML(io.Reader) (*RecDef, error)` — XML parser using
  `encoding/xml` with custom unmarshalers for the awkward bits (Tag, Path,
  `@XStreamImplicit` collections, `attrs`/`elems` shorthand expansion, Doc
  `<para>` vs. `<paras>` ambiguity).
- `recdef.LoadJSON(io.Reader) (*RecDef, error)` — JSON parser.
- `recdef.WriteXML(io.Writer, *RecDef) error` — round-trip XML emitter using
  Java field order; **also** emits an XSD comment header so external tools
  recognise the canonical form.
- `recdef.WriteJSON(io.Writer, *RecDef) error` — JSON emitter.
- `recdef.Resolve(*RecDef) error` — performs §2.4's resolution
  (template expansion, `attrs`/`elems` shorthand expansion, doc attachment,
  opt-list attachment, default-prefix application).

Browser editor: JSON over the wire, JSON in IndexedDB, XML only on export to
sip.zip.

---

## 14. Open questions & quirks

### 14.1 Dead/legacy-looking fields

- **`RecDef.sourceTree`** (`RecDef.java:73`) — public, non-static, not
  `@XStreamOmitField`.  It's typed as `RecDefTree.SourceTree`, an interface.
  Likely dead in this class (the source tree belongs to a sip.zip's facts/source,
  not the schema).  XStream will try to serialize it if non-null; it always
  appears to be null in the corpus.  **Recommend: treat as transient; don't
  serialize.**
- **`Elem.assertion`** (`RecDef.java:498`) — inline `<assertion>` field on
  `Elem`.  No on-disk example uses it.  Likely vestigial; assertions are
  globally collected under `<assertion-list>`.
- **`Doc.paras` vs `Doc.paraList`** — two fields for the same content
  (`RecDef.java:304-307`).  Both are populated by XStream depending on whether
  the XML has `<paras>` wrapper or not.  Pick one form for a canonical
  re-emitter (recommend: `paraList`, implicit, no wrapper, used in EDM/ACE).
- **`Doc.lines`** (`RecDef.java:301-302`) — legacy line-based encoding;
  superseded by `<para>`.

### 14.2 Behavioural subtleties

- **Resolution mutates the tree**: as noted in §2.4, after `RecDef.read()` the
  shorthand string fields (`Elem.attrs`, `Elem.elems`, `Elem.attrGroups`,
  `Elem.elemGroups`) are nulled.  A re-emitter that hasn't tracked this will
  emit a tree that is structurally equivalent but textually divergent (no more
  shorthand).  This is intentional in Java but surprising for tooling.
- **Implicit lists**: `@XStreamImplicit` means there is **no XML wrapper
  element**.  E.g. `Attr.subattrs` inside an `<elem>` is just bare `<attr>`
  children directly under `<elem>`, not `<subattrs><attr/></subattrs>`.
  Confused parsers will emit the latter and break round-trip.
- **Default `Tag` prefix vs. fully-qualified**: tag strings without a colon
  are reinterpreted at resolve-time with `RecDef.prefix`.  The on-disk form
  may be either `tag="record"` or `tag="icn:record"`; both round-trip the
  same.  A normaliser should pick one.
- **`Tag.descendency`**: the `Tag` class has a `descendency` counter
  (`Tag.java:36`) used internally to disambiguate two same-named elements at
  different depths within a single path (e.g. `crm:E22_Human-Made_Object`
  inside another `crm:E22_Human-Made_Object`).  Not serialized; recomputed.
- **`Path` is opaque** but `toString()` is a `/`-separated XPath-like string,
  with attributes prefixed `@` (e.g. `/RDF/ore:Aggregation/@rdf:about`).
  Re-implementations should preserve this exact form (see `Path.toString()`,
  `Path.java:232-236`).
- **`OptList.value` defaulting** (`OptList.java:69-76`) — if `value` is
  absent on `<opt-list>`, it defaults to the *last* segment of `path` and the
  flag `valuePresent` is left false.  Different runtime code paths key off
  `valuePresent` (e.g. `NodeMapping.valueHasDictionary`,
  `NodeMapping.java:160-178`).  A re-emitter must preserve the
  presence/absence distinction.
- **Mixed-prefix `attrs`/`elems` lists**: tokens in the shorthand string can
  be either bare (default prefix) or `prefix:localName`.  Examples: `attrs="rdf:datatype, xml:lang"` (ACE templates).  The split is purely by `[ ,]+`.
- **Empty `target=""` tokens** are silently skipped (`RecDef.java:597-599`).
- **`AttrGroup` resolution** is called for **every** `attr-group` even if
  unused, and throws on missing tags (`RecDef.java:214`).  An invalid `<attr-group>` aborts loading the whole record-definition.
- **`@XStreamConverter(ToAttributedValueConverter.class, strings = {"content"})`**
  on `DocParagraph` (`RecDef.java:345`) is what makes
  `<para name="X">text</para>` work — `content` becomes the element text,
  `name` becomes the attribute.  Custom logic required in Go.

### 14.3 Unverified / undocumented

- **`displayName` on `<opt-list>`** is in every example file but not in
  `OptList.java`.  Confirm with the schema-repo maintainer whether it should be
  promoted to a real field or considered presentational metadata.
- **`type="search"` and `type="fact"`** on `<field-marker>` are free-form
  strings in Java; downstream consumers (Narthex, Hub3) interpret them.  Worth
  enumerating the actually-used values across the schema repo before locking
  the JSON schema.
- **`hidden="true"` on `<opt>`** is preserved by XStream as an unknown attr
  but has no field.  Determine whether `hide-from-GUI` is the intent and add
  the field if so.
- **`elementFormDefaultQualified` / `attributeFormDefaultQualified`** are
  set but not visibly consumed in `RecDef`/`RecDefTree`.  Trace whether
  generator code actually honours them (suspected: vestigial).
- **`Elem.fieldType` and `Elem.path=""`** attributes seen in ICN/ACE are
  domain extensions with no field; no code consumes them in the current
  RecDef.  They may be needed by Narthex but are invisible in the SIP
  Creator.
- **`<assertion>` as inline child of `<elem>`** — Java field exists; no
  in-the-wild example.  Confirm whether to spec it as supported or
  deprecated.

### 14.4 Blocking unknowns

None.  Every observed XML construct in the four canonical examples maps to a
clear (if sometimes legacy) Java field.  The only outstanding decisions are
*authoring* choices (pick the JSON canonical form for ambiguous Doc encodings;
decide whether to surface or hide `displayName`/`hidden`/`fieldType`) — not
parsing blockers.

---

## Appendix A: Source file index

Java sources cited:

- `sip-core/src/main/java/eu/delving/metadata/RecDef.java`
- `sip-core/src/main/java/eu/delving/metadata/RecDefNode.java`
- `sip-core/src/main/java/eu/delving/metadata/RecDefTree.java`
- `sip-core/src/main/java/eu/delving/metadata/Tag.java`
- `sip-core/src/main/java/eu/delving/metadata/Path.java`
- `sip-core/src/main/java/eu/delving/metadata/OptList.java`
- `sip-core/src/main/java/eu/delving/metadata/OptRole.java`
- `sip-core/src/main/java/eu/delving/metadata/OptBox.java`
- `sip-core/src/main/java/eu/delving/metadata/DynOpt.java`
- `sip-core/src/main/java/eu/delving/metadata/Assertion.java`
- `sip-core/src/main/java/eu/delving/metadata/AssertionException.java`
- `sip-core/src/main/java/eu/delving/metadata/MappingFunction.java`
- `sip-core/src/main/java/eu/delving/metadata/NodeMapping.java`
- `sip-core/src/main/java/eu/delving/metadata/Operator.java`
- `sip-core/src/main/java/eu/delving/XStreamFactory.java`
- `schema-repo/src/main/java/eu/delving/schema/SchemaVersion.java`
- `schema-repo/src/main/java/eu/delving/schema/SchemaType.java`
- `schema-repo/src/main/java/eu/delving/schema/SchemaRepository.java`
- `schema-repo/src/main/java/eu/delving/schema/SchemaResponse.java`
- `sip-app/src/main/java/eu/delving/sip/files/StorageImpl.java`

Example XML files studied in full:

- `/home/kiivihal/PocketMapper/datahub/PocketMapper/work/adlib-loans__2026_05_05_11_25.sip.zip/ace_0.2.3_record-definition.xml` — primary nested-elem / templates / target= example.
- `/home/kiivihal/data_no_backup/_scratch/downloads/edm_5.2.6_record-definition.xml` — primary opt-lists / docs / constants / `node-mapping` example.
- `/home/kiivihal/_para/01_projects/semafora_final/smfr_1.0.1_record-definition.xml` — secondary example, mostly flat under root, lots of inline `<attr>`.
- `/home/kiivihal/data_no_backup/_scratch/downloads/smfr_1.0.1_record-definition.xml` — identical to the above; confirmed via diff.
- `sip-app/src/test/resources/test/mods/record_def_3.4.0.xml` — only corpus example using `<attr-groups>` and `attr-form-default-qualified="false"`.
- `sip-app/src/test/resources/test/icn/icn_1.0.3_record.xml` — only corpus example using `flat="true"`, `<field-marker type="fact"/>`, `<field-marker type="search"/>`, and the `check` enum exhaustively.
- `sip-core/src/test/resources/recdef/elem-groups-recdef.xml` — minimal `<elem-groups>` example.
- `sip-core/src/test/resources/recdef/inline-docs-recdef.xml` — minimal inline `<doc>` and inline-vs-path override semantics.
- `sip-app/src/test/resources/assertion/assertion-list.xml` — assertion examples (all forms: `condition`, `allowed`, `code`, bare).
