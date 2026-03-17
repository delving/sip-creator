# SIP-Creator Web Migration Plan

## Executive Summary

Build a **Svelte-based visual mapping editor** integrated into **Narthex** (Play Framework), leveraging existing backend infrastructure.

**Full documentation:** `docs/SWING_APP_ANALYSIS.md`

---

## Recommended Approach: Narthex + Svelte

### Why This Approach

1. **Narthex already has mapping infrastructure** - DefaultMappingRepo, DatasetMappingRepo, version control
2. **sip-core integration exists** - MappingEngine executes Groovy code
3. **Statistics endpoints ready** - /source-sample, /source-histogram
4. **Processing pipeline ready** - SourceProcessor applies mappings
5. **Only missing piece** - Visual mapping editor UI

### Why Svelte over AngularJS

| Aspect | AngularJS 1.3.17 | Svelte |
|--------|------------------|--------|
| Status | EOL (2014), no security updates | Active development |
| Performance | Digest cycle stressed | Compiled reactivity |
| Tooling | RequireJS, no TypeScript | Vite, TypeScript native |
| Bundle Size | Large runtime | 40-60% smaller |
| Developer Experience | Outdated | Modern |

**Verdict: Do not extend AngularJS. Build new editor in Svelte.**

---

## Integration Strategy: Svelte SPA with Shared Backend

```
Play Framework Backend
├── /narthex/app/*    → AngularJS (existing, unchanged)
├── /narthex/api/*    → REST APIs (shared)
└── /narthex/editor/* → Svelte SPA (new mapping editor)
```

---

## Visual Mapping Editor Architecture

```
+------------------------------------------------------------------+
| Mapping Editor: amsterdam-museum / edm                    [Save] |
+------------------------------------------------------------------+
| +------------------+    CONNECTIONS    +------------------+      |
| | SOURCE TREE      |==================>| TARGET TREE      |      |
| | (XML Structure)  |    [SVG/Canvas]   | (RecDef Schema)  |      |
| |                  |                   |                  |      |
| | - /record        |    ─────────────> | - /rdf:RDF       |      |
| |   +- /title ●────┼────────●─────────┼──+- dc:title ●    |      |
| |   +- /creator ●──┼────────●─────────┼──+- dc:creator    |      |
| +------------------+                   +------------------+      |
|                                                                  |
| +---------------------------------------------------------------+|
| | GROOVY CODE                                            [Docs] ||
| | output.dc_title = input.title?.text()                         ||
| +---------------------------------------------------------------+|
|                                                                  |
| +---------------------------------------------------------------+|
| | PREVIEW                                    Record: [<] 1 [>]  ||
| | Input XML              | Output RDF/JSON-LD                   ||
| +---------------------------------------------------------------+|
+------------------------------------------------------------------+
```

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Framework | SvelteKit + Vite |
| Panels | PaneForge (resizable) |
| Trees | svelte-treeview + TanStack Virtual |
| Code Editor | Monaco Editor |
| UI Components | Bits UI (headless) |
| Styling | Tailwind CSS |
| State | Svelte stores |
| Real-time | SSE or WebSocket |

---

## Development Timeline

| Phase | Description | Duration |
|-------|-------------|----------|
| 1 | Project setup + basic layout | 1 week |
| 2 | Source/Target tree components | 2 weeks |
| 3 | Connection line rendering (SVG) | 2 weeks |
| 4 | Drag-to-map interaction | 2 weeks |
| 5 | Code editor + generation | 1 week |
| 6 | Live preview + compilation | 2 weeks |
| 7 | API integration | 1 week |
| 8 | Testing + polish | 2 weeks |
| **Total** | | **13 weeks** |

---

## New API Endpoints Needed

```
GET  /narthex/app/dataset/:spec/mapping-editor/samples/:count
POST /narthex/app/dataset/:spec/mapping-editor/generate
POST /narthex/app/dataset/:spec/mapping-editor/preview
GET  /narthex/app/dataset/:spec/mapping-editor/state
POST /narthex/app/dataset/:spec/mapping-editor/save-state
```

---

## Existing Narthex APIs (Ready to Use)

```
GET  /narthex/app/default-mappings
GET  /narthex/app/default-mappings/:prefix/:name/xml/:v
POST /narthex/app/default-mappings/:prefix/:name/upload
POST /narthex/app/dataset/:spec/set-mapping-source
POST /narthex/app/dataset/:spec/rollback-mapping/:hash
GET  /narthex/app/dataset/:spec/source-sample/:size/*path
GET  /narthex/app/dataset/:spec/source-histogram/:size/*path
```

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Large schemas | Virtual scrolling, lazy loading |
| Complex state sync | Well-designed store architecture |
| SVG performance | Canvas fallback if needed |
| Auth integration | Share session cookies |

---

## Next Steps

1. Create narthex-editor Svelte project
2. Implement tree components with virtualization
3. Build SVG connection line rendering
4. Add drag-to-map interaction
5. Integrate Monaco Editor
6. Connect to Narthex APIs
7. Polish and optimize

---

## Files Reference

- Full analysis: `docs/SWING_APP_ANALYSIS.md`
- Narthex mapping APIs: `~/code/scala/narthex/app/controllers/AppController.scala`
- Narthex mapping repos: `~/code/scala/narthex/app/mapping/`
- SIP-Creator frames: `sip-app/src/main/java/eu/delving/sip/frames/`
