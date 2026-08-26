# Lua mapping engine — spike results and go/no-go

Sub-project 1 (feasibility spike) of the Lua mapping-engine deep-dive.
Semantic contract: [`docs/specs/mapping-language-core.md`](../docs/specs/mapping-language-core.md)
— the one planning artifact that *is* under version control.

> **Note on sources.** The plan and design documents for this sub-project live
> under `docs/superpowers/`, which is git-ignored on the machine this work was
> done on, so they are not in the repository and cannot be linked. Everything
> this document depends on them for — the four success criteria, and the
> next-phase DSL evaluation — is therefore reproduced in full below (§0 and §7)
> rather than cited. This document is intended to be decision-grade on its own.

## 0. Success criteria (reproduced verbatim from the design document)

1. Tier table with percentage coverage per tier over the full corpus.
2. Mapping Language Core spec reviewed and committed.
3. ≥7/10 goldens green from the Go host; all pure-T1 mappings green.
4. Written recommendation: proceed to sub-project 2 (full converter) or stop —
   with T2/T3 cost estimated from corpus numbers, and the Lua-ecosystem verdict
   (reuse vs build for XML/RDF libs).

Status: **1 met** (§1), **2 met** (`docs/specs/mapping-language-core.md`,
committed), **3 NOT met** (6/10, §2), **4 met** (§3, §4).

**Recommendation: GO — but only for a sub-project 2 that includes T2 from
day one, and with eyes open about where T2 stops.** A T1-only engine is not a
product: it covers 5.49% of mappings. T1+T2 complete reaches **34.1%** — a real
migration, but still not engine retirement, which needs the T3 long tail. The
spike proves the architecture works end to end; it also proves the T1 boundary
is far too narrow to ship behind. See §4.3 for the increments and §5 for the
framing.

---

## 1. Corpus coverage (success criterion 1)

Canonical corpus: 2,241 mappings, 84,698 Groovy snippets, deduplicated one
representative per logical mapping. Full method and the all-versions
comparison in [`analysis/TIER_TABLE.md`](analysis/TIER_TABLE.md); raw output in
[`analysis/corpus-report-canonical.json`](analysis/corpus-report-canonical.json).

| Tier | Snippets | Share |
|---|---:|---:|
| T1 (trivially portable) | 57,153 | 67.48% |
| T2 (moderate: spread/closures/regex/list literals) | 15,564 | 18.38% |
| T3 (exotic / bespoke) | 11,981 | 14.15% |

**T1-only mappings — the number that actually matters: 134 of 2,241 (5.98%).**
A mapping is only convertible by a T1-only engine if *every* snippet in it is
T1. Snippet-level T1 share is 67%; mapping-level it is 6%.

### 1.1 The regex gap: 12.36%

`replaceAll`/`split`/`matches` take `java.util.regex` patterns; gopher-lua
offers only Lua patterns (spec §9.3.5). Over 506 distinct literal patterns /
37,520 occurrences (`analysis/regex_gap.py`, `analysis/regex-gap-report.json`):

| Class | Occurrences | Share |
|---|---:|---:|
| Lua-expressible + literal | 32,874 | 87.62% |
| alternation `\|` | 3,927 | 10.47% |
| counted repetition `{n,m}` | 392 | 1.04% |
| inline flags / groups `(?…)` | 276 | 0.74% |
| lazy/possessive quantifiers | 41 | 0.11% |
| extraction artifact (truncated literal, unclassifiable) | 10 | 0.03% |

Zero backreferences and zero lookaround were measured, which is why the
host-callback strategy (§4.3) closes essentially the whole gap.

### 1.2 New finding: the tier table over-counts T1 by ~16 points

`analyze_corpus.py`'s "arbitrary method call → T3" fallback matches
`\.(\w+)\s*\(` — a call *with a receiver*. A **bare** call to a
mapping-defined or rec-def-defined Groovy function (`reverseNames("…")`,
`remove_whitespace("…")`, `checkLang("…")`) has no receiver, so no rule fires
and the snippet stays T1. It is not T1: the callee's own Groovy body must be
ported too, and those bodies are routinely T2/T3 — `reverseNames`, defined in
the EDM 5.2.6 rec-def, uses assignment, array indexing and `.length`.

Measured by [`analysis/bare_call_gap.py`](analysis/bare_call_gap.py)
(same population, same dedup rule; raw output
[`analysis/bare-call-gap-report.json`](analysis/bare-call-gap-report.json)):

```
python3 lua-poc/analysis/bare_call_gap.py \
    ~/PocketMapper sip-app/src/test/resources sip-core/src/test/resources \
    -o lua-poc/analysis/bare-call-gap-report.json
```

- **13,315 of 57,153 T1 snippets (23.30%)** contain a bare user-function call.
- **11 of the 134 T1-only mappings** contain one — including `coll-schraven`,
  which is exactly why it is the T1 golden this spike could not convert (§2).

Corrected picture — **snippet-level, and an upper bound on the correction,
not a measurement**:

| Tier | Snippets | Share | |
|---|---:|---:|---|
| T1 | 43,838 | 51.76% | lower bound |
| T2 | 15,564 | 18.38% | unchanged |
| T3 | 25,296 | 29.87% | **upper bound** |

**Read this table as a ceiling.** `bare_call_gap.py` detects that a snippet
*contains* a bare user-function call; it does not parse the callee's body and
classify it. Some callees are genuinely T1 — the single most frequent name is
`remove_whitespace` (2,259 occurrences), which is plausibly a one-line
`replaceAll`. Reclassifying all 13,315 affected snippets as T3 is therefore the
worst case: real T1 is somewhere between 51.76% and 67.48%, and real T3 between
14.15% and 29.87%. Pinning it down requires resolving each call to its
definition (mapping `<functions>` or rec-def `<mapping-function>`) and
classifying that body recursively — worth doing in sub-project 2, out of scope
here.

**The mapping-level figure is not a ceiling — it is measured.**
`coll-schraven` demonstrates the mechanism directly: a mapping was selected as
T1-only, its bare call resolved to a real rec-def function, and that function's
body is unambiguously T2/T3. Whether *every* one of the 11 affected mappings
resolves that way is unverified, so:

**Corrected T1-only mappings: 123 of 2,241 = 5.49%** (between 5.49% and 5.98%;
at least one — `coll-schraven` — is confirmed by inspection, and the
recommendation rests on this mapping-level number, not on the snippet table
above).

This does not change the recommendation — it sharpens it. Every headline in
this document uses the mapping-level figure where coverage is at stake, and
labels the snippet-level number as bounded where it is quoted.

---

## 2. Golden results (success criterion 3)

Ten real corpus mappings captured against the production Groovy engine
(Task 4), verified by Jena graph isomorphism (Task 5). Reproduce with:

```
lua-poc/spike-go/run-goldens.sh
make -C lua-poc/golden verify ACTUAL_DIR=$PWD/lua-poc/spike-go/out
```

### Scorecard: 6/10 green — all six SEMANTIC, none IDENTICAL

| Case | Tier (as selected) | Result | Note |
|---|---|---|---|
| nationaal-gevangenismuseum | T1 | **SEMANTIC** | |
| synagoge-groningen | T1 | **SEMANTIC** | if/else + tuple-map subscripts |
| huizer-museum | T1 | **SEMANTIC** | |
| maritieme-archeologische-rijkscollectie-artikelen | T1 | **SEMANTIC** | 7-way tuple merge |
| universiteitsmuseum-groningen | T1 | **SEMANTIC** | if/else + tuple-map subscripts |
| provinciaal-depot-bodemvondsten-noord-brabant-1 | T1 | **SEMANTIC** | |
| coll-schraven | T1 | **refused** | `MethodCall:reverseNames` |
| collectie-schraven | T2 | **refused** | `ClosureExpression` (operand of `*`) |
| register-kerkelijke-ensembles | T2 | **refused** | `ClosureExpression` (operand of `*`) |
| enb-301-titels | T3 | **refused** | `ClosureExpression` (operand of `*`) |

**Success criterion 3 (≥7/10 green, all pure-T1 green) is NOT met**, by one
case, and the reason is §1.2: `coll-schraven` was selected as pure-T1 on the
strength of a classifier that cannot see bare function calls. Its one
offending snippet is `reverseNames("${_M4['voornaam1']} ${_M4['achternaam']}")`,
and `reverseNames` is a rec-def-defined Groovy function whose body
(`parts = it.toString().split(","); if (parts.length > 1) …`) needs
assignment, array indexing and `.length` — squarely T2/T3. Supporting it
would have meant quietly widening "T1" until the criterion passed, which
would have destroyed the measurement the spike exists to produce.

Every case the converter *accepts* verifies green. That is the substantive
result: the architecture (Groovy AST → Lua → gopher-lua → RDF/XML that Jena
finds isomorphic to the Groovy engine's output) works.

### 2.1 Why SEMANTIC and not IDENTICAL

Expected: `builder.lua` deliberately does not reproduce the reference
serializer's attribute/namespace ordering or its whitespace (documented in its
module header), and it emits every rec-def namespace on the root rather than
only the used ones. Graph isomorphism is the contract Task 5 established;
byte-identity was never the target.

### 2.2 Converter refusal list (the deliverable, not a failure)

| Construct name | Cases | What it is |
|---|---|---|
| `ClosureExpression` | collectie-schraven, register-kerkelijke-ensembles, enb-301-titels | `_input.record * { _record -> … }` — the T2 spread operators (`*`, `**`, `>>`, `\|`) with a closure body |
| `MethodCall:reverseNames` | coll-schraven | a bare call to a rec-def-defined Groovy function |

The converter names the *reason*, not the token: a `*`-spread is reported as
`ClosureExpression` rather than `BinaryExpression:*`, because the closure is
what puts the snippet outside T1. Full refusal vocabulary is in
`GroovySnippetToLua`'s javadoc and pinned by `GroovySnippetToLuaTest`.

### 2.3 gs() empty-template suppression: exercised, but not *decisively*

Success-criterion note carried over from Task 7. Instrumenting `stdlib.gs`
shows suppression firing **6 times** across the green cases (5 in
`synagoge-groningen`, 1 in `huizer-museum`). But re-running all six goldens
with suppression disabled produced **byte-identical output**: every suppressed
template rendered to the empty string, so `Utils.stripEmpty`'s equivalent in
`builder.lua` would have removed those elements anyway.

**Recorded gap:** no golden case has a suppressible template with non-empty
literal text (the `"http://host/${empty}.jpg"` shape, where suppression means
"drop it" and no-suppression means "emit `http://host/.jpg`"). Suppression is
therefore *executed* but not *discriminated* by the golden set. Sub-project 2
should add a case with that shape before trusting §3.4 of the spec.

### 2.4 Divergences the generator makes loud rather than silent

- **Element multiplication is not implemented.** Groovy's `list * { … }`
  collects every value and the reference builder multiplies the element
  (spec §3.5); `builder.lua` defers this. The generated Lua therefore
  *raises* on a second value instead of keeping the first — a case that needs
  multiplication fails as a Lua error, not as a plausible wrong answer. No
  golden case triggers it.
- **Multi-attribute elements.** `CodeGenerator.startBuilderCall` re-emits the
  element tag once per mapped attribute, which only produces valid Groovy for
  a single one; the Lua generator emits one `b:elem` with an attribute table.
  No golden case has two mapped attributes on one element, so this is recorded,
  not verified — and the Groovy side looks like a latent bug worth its own
  ticket.
- **Ternary and Elvis are lowered unsoundly for falsy true-branches.**
  `a ? b : c` becomes Lua's `((a) and (b) or (c))` idiom
  (`GroovySnippetToLua.java`, `TernaryExpression` case), which silently yields
  `c` whenever `b` evaluates to `false` or `nil`. Sound for T1, where every
  branch value is a string or a gstring table, and no golden case uses a
  ternary at all — but unlike the two divergences above this one fails
  *quietly*, so sub-project 2 should replace it with an
  immediately-invoked-function lowering rather than inherit it.
- **Option lists / dictionaries / `ifAbsent`** raise by name; none appear in
  the golden set.

### 2.5 A concrete Lua-hosting hazard found by running the code

**9 of the 10 golden mappings ship a fact named `type`.** Emitted naively as a
module-level `local type = ""`, it shadows Lua's `type` function for every
helper defined after it, and three of the generated helpers call it. Three
goldens died on `attempt to call a non-function object` until the fact locals
were moved inside the record function, below the helpers. Fact names are user
data; any Lua codegen must treat them as hostile to the standard library.

---

## 3. Lua ecosystem verdict (success criterion 4, part 2)

From spec §9, and confirmed by building on it.

**XML: vendor SLAXML.** `Phrogz/SLAXML` (MIT, pure Lua, `lua >= 5.1`) satisfies
all seven requirements the Navigation contract imposes (document order,
repeated siblings, element prefix, qualified attributes, CDATA distinguishable,
parent links, mixed content). It is vendored at
`lua-poc/engine/vendor/slaxml.lua` and carried the whole golden run.
`xml2lua`'s `tree` handler is disqualified by its own documented mixed-content
behaviour; its `dom` handler is a viable fallback but loses namespace
resolution.

**RDF: build, do not reuse.** There is no usable pure-Lua 5.1 RDF library
(`lua-rdf` does not exist; `volksdata` declares `lua >= 5.4` and depends on
penlight; raptor/redland are C). More importantly the requirement is
mis-stated as "RDF": the engine builds a **DOM** that the Java side serialises
and validates. What Lua needs is an element builder — namespace-prefixed
elements, attribute maps, ordered children, CDATA spans, empty-element
stripping. That is `builder.lua`: **327 lines**, no dependencies, and it
produced graph-isomorphic output on the first six cases it was given.

**Host: gopher-lua is a good fit, with two costs.** It is Lua 5.1 plus 5.2
`goto`, cannot load C modules (which is what forces pure-Lua everything), has
no `utf8` library and no bitwise ops. The two costs that bite:

1. **No regex engine in-VM** (§4.3).
2. **Byte strings only** — `tagToVariable`'s accent folding had to be a
   hand-written 60-entry UTF-8 byte table (`node.lua`), and
   `Utils.stripNonPrinting`'s C1 control range is not reproducible without a
   full UTF-8 decoder. Both are documented deviations, neither is exercised by
   the corpus.

Rock names `lua-mapping-engine` and `lua-rdf` are both free on LuaRocks.

---

## 4. Go/no-go recommendation (success criterion 4, part 1)

### 4.1 What the spike proved

The full chain works: mapping XML → `LuaMappingGenerator` → `mapping.lua` →
gopher-lua → RDF/XML that Jena finds isomorphic to the Groovy engine's output,
for every mapping the T1 converter accepts, including non-trivial ones
(7-way tuple merges, `if`/`else` with node-text equality, multi-slot template
URIs). Nothing architectural blocked. The engine is 4 Lua modules plus a
vendored parser, and the host is 119 lines of Go.

### 4.2 What it also proved

**A T1-only engine covers 5.49% of mappings (123 of 2,241)** — and it failed
one of the seven T1 goldens, because the 5.98% those goldens were selected
against was itself optimistic (§1.2); 5.49% is the corrected figure.
Shipping T1 alone would convert roughly one mapping in twenty and leave the
Groovy engine running for the other nineteen: two engines, two behaviours, two
sets of bugs, indefinitely. **That is a worse position than not starting.**

### 4.3 What sub-project 2 must include, and what it costs

Scoped from the corrected corpus numbers (§1.2). "Coverage" is
mapping-level: the share of the 2,241 canonical mappings whose *every* snippet
becomes convertible.

| Increment | Constructs | Est. effort (engineering judgement, not measured) | Cumulative mapping coverage |
|---|---|---|---:|
| **Baseline** (this spike) | T1 | done | 5.49% |
| **A. User functions** | bare calls to mapping/rec-def-defined Groovy functions — emit each as a Lua function, converting its body with the same converter | 1–2 wks | ~8–12% |
| **B. Spread + closures** | `*`, `**`, `>>`, `\|` with closure bodies; element multiplication in `builder.lua`; list literals | 3–4 wks | ~25–34% |
| **C. Regex** | host-callback `regex.*` over Go's RE2 (closes ~100% of the 12.36% gap), or pure-Lua translation (closes 87.62%) | 1 wk | **≤34% (T2 tier complete)** |
| **D. Option lists / dictionaries** | `_optLookup`, generated `lookupX` closures, `_absent_`/`ifAbsent` | 1–2 wks | ≤34% (prerequisite, not an extension — see below) |
| **E. The T3 long tail** | `def`/local variables, `each`/`collect`/`findAll`, `getValueNodes`, `first`/`last`, date helpers, `discardRecord` | 4–6 wks | 85%+ (the only increment that breaks 34.1%) |

**Hard ceiling on A+B+C: 34.1%.** Increments A, B and C add only T1 and T2
constructs, so the most they can reach is every mapping whose `max_tier` is T1
or T2 — **764 of 2,241 = 34.1%** by the committed canonical tier table
(`analysis/TIER_TABLE.md`: 134 T1-only + 630 T2-max). The rows above are capped
at that. Anything beyond 34.1% requires **E**, which is where the 1,477 T3-max
mappings (65.9%) live.

**D is a prerequisite, not an extension.** Option lists, dictionaries and
`ifAbsent` are properties of a mapping's *structure*, not of its snippet tier —
a mapping can be T1-only in every snippet and still use an option list, and
this spike refuses it (`OptListLookup`). D is therefore needed to actually
*reach* the 34.1% ceiling, not to go past it. It is listed separately because
it is separable work, not because it buys separable coverage.

The effort column is engineering judgement from the construct inventory, not a
measurement, and the coverage ranges are extrapolations from snippet-frequency
data (`TIER_TABLE.md` top-20 constructs) onto mapping-level coverage. The shape
is what matters — **A+B+C is the minimum viable increment**, roughly 5–7 weeks,
and it is the point at which conversion becomes a migration rather than a
science project. It is *not* the point at which the Groovy engine can be
retired: that needs D and E.

**A+B is non-negotiable.** `closure_literal` is the 5th most common construct
in the corpus (30,003 occurrences) and every single T2/T3 golden refused on it.

### 4.4 Recommended decision

**Proceed to sub-project 2, scoped as T1+A+B+C+D.** Do not scope it as "finish
T1". Fund it against a **34.1% ceiling**, not against "most of the corpus" —
the Groovy engine keeps running for the other 65.9% until increment E lands, so
plan for a two-engine period and decide up front how long it is acceptable. Do
not begin mass conversion until §6 and §7 are answered.

Two decisions to take before starting:

- **Regex strategy** (spec open question 5). The host callback (Go RE2) closes
  the gap outright but makes the Lua rock non-standalone. Recommendation:
  **host callback with a pure-Lua fallback for the 87.62% subset**, so the rock
  still runs standalone for testing and degrades to a named error rather than a
  wrong answer.
- **Null-receiver semantics** — §6 below. This one is the user's to make.

---

## 5. Framing, stated plainly

The spike succeeded and the T1 tier is not a viable shipping boundary. Both
are true. The value delivered here is a working architecture plus a scope for
the real work that is honest about what it does and does not know:

- **Measured:** T1-only mappings are 5.49–5.98% of the corpus, T1+T2 mappings
  are 34.1%, and the golden set proves the architecture end to end for
  everything the T1 converter accepts.
- **Bounded, not measured:** the snippet-level T1/T3 correction in §1.2 is a
  ceiling, because the callee bodies behind 13,315 bare calls were not
  classified.
- **Estimated:** every figure in the effort column of §4.3.
- **Definitional, not measured:** "T1" names two slightly different sets —
  the corpus classifier (`analyze_corpus.py`) excludes `substring` (counted
  T3, 58 occ) and routes `matches`/`matcher` to T2 (377 T2 + 779 T3 occ),
  while the converter whitelist (`GroovySnippetToLua`) accepts both. The
  direction is conservative — true T1-convertible coverage is marginally
  higher than the 5.49% cited above — and reconciling the two definitions is
  sub-project 2 work, not done here.

A go decision that does not fund T2 is a no decision wearing a yes. A go
decision that assumes T2 retires the Groovy engine is a different mistake:
it stops at 34.1%.

---

## Decisions taken (2026-08-26, project owner)

- **Null-receiver semantics: PARITY.** The Lua engine must reject a record where
  Groovy would (visible per-record failure in the report), not publish it with
  empty strings. Sub-project 2 reworks the stdlib accordingly; the ergonomic fix
  for missing values belongs to the DSL phase, not the engine.
- **Success criterion 3: spike ACCEPTED at 6/10.** The miss exposed and corrected
  a real measurement bug (bare-call blind spot); the engine produced correct
  output for every case its converter accepted.

## 6. OPEN DECISION for sub-project 2: stdlib null-receiver semantics

**Parked from Task 7. The user must rule on this; it was not decided here.**

`lua-poc/engine/stdlib.lua` makes every function's receiver argument
null-safe: `nil` or a Task-6 absorber coerces to `""` and the call proceeds.
That is what the Task 7 brief specified literally
(`assert.equal("", stdlib.capitalize(nil))`). It **contradicts**
`docs/specs/mapping-language-core.md` §5.2/§5.3, which is explicit that a
method on null throws and that *throwing* is part of the contract.

The two options and their consequences:

**Option A — preserve Groovy's NPE (spec-faithful).**
- A record the Groovy engine fails or discards keeps failing or discarding.
  Byte-for-byte behavioural parity on the error path; migration is invisible
  to downstream consumers and to operators reading discard counts.
- Cost: reintroduces crash-on-absence for every mapping that calls a String
  method on a possibly-absent field — the exact behaviour Task 6's absorber
  was built to remove. Navigation stays total, stdlib does not, and the
  boundary between them becomes a thing mappers must know about.

**Option B — null-safe receivers (what the spike implements).**
- Chained navigation and stdlib are uniformly total; a missing field yields
  `""` rather than an exception. Simpler mental model, fewer defensive
  `?.`/`?:` in mappings.
- Cost, stated without softening: **a record that Groovy discards would
  silently succeed in Lua, with an empty value standing in for what Groovy
  refused to answer.** Output counts change. Data that was previously withheld
  starts flowing. This is a behaviour change to production output, not an
  implementation detail.

**Current status:** no golden case exercises the difference — the spike's
evidence is silent, which is why this is a decision and not a finding. One
golden where the reference engine discards a record via this exact path would
settle it empirically. Sub-project 2 should capture that case *first* and
decide against real output.

**If forced to advise:** Option A for the migration, with a per-mapping opt-in
to Option B afterwards. Parity first, improvement second — an engine swap that
also changes which records ship is two changes wearing one commit.

---

## 7. Next phase: evaluate a mapping DSL before mass conversion

**There is a distinct phase between this spike and any mass conversion, and it
is not optional.** (Reproduced here in full rather than cited: the design
document that named it is not under version control — see the note at the top
of this file.)

Groovy was chosen historically because the app was Java and needed scripting.
The snippet language is an accident of platform, not a design. If the execution
engine is being replaced anyway, the snippet surface the mappers actually type
is up for redesign at the same time — and **converting the corpus twice would
be waste**, so the question must be answered *before* sub-project 2's
conversion work starts, not after.

Before committing to raw Lua snippets as the user-facing language, evaluate a
purpose-built mapping DSL that:

- **converts 1:1 to Lua for execution** — the engine built here stays the
  target, so this is a front end, not a second runtime;
- **is form-representable** — the same expression renders as GUI form controls
  in the editor *and* as text, round-tripping both ways;
- **is easy to write, review and diff for non-programmer mappers** — the people
  who maintain these 2,241 mappings are not Groovy developers, and the corpus
  shows it;
- **supports browser autocompletion**, typed against source-tree paths and the
  `mapping-language-core` function set.

The corpus tier table scopes that DSL directly: **T1 constructs define what the
DSL must express natively.** §1.2's bare-call finding adds one requirement the
original framing missed — user-defined functions are used heavily enough
(13,315 snippet occurrences; `remove_whitespace` alone 2,259 times) that they
are a first-class DSL feature, not an escape hatch.

---

## 8. Artifacts

| Path | What |
|---|---|
| `lua-poc/analysis/analyze_corpus.py`, `TIER_TABLE.md` | tier classifier and table |
| `lua-poc/analysis/regex_gap.py`, `regex-gap-report.json` | regex gap measurement |
| `lua-poc/analysis/bare_call_gap.py`, `bare-call-gap-report.json` | bare-call blind-spot measurement (§1.2) |
| `docs/specs/mapping-language-core.md` | engine-neutral semantic contract |
| `lua-poc/golden/` | 10 golden cases, capture + verify Makefile |
| `lua-poc/engine/` | `node.lua`, `stdlib.lua`, `builder.lua`, `xml.lua`, vendored SLAXML — `make test` (136 specs) |
| `sip-core/src/test/java/eu/delving/metadata/GroovySnippetToLua.java` | Groovy AST → Lua, hard-fails by construct name |
| `sip-core/src/test/java/eu/delving/metadata/LuaMappingGenerator.java` | mapping XML → `mapping.lua` |
| `lua-poc/spike-go/` | gopher-lua host + `run-goldens.sh` pipeline driver |
