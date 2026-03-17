# SIP-Creator Swing Application Analysis for Web Migration

## Executive Summary

This document provides a comprehensive analysis of the SIP-Creator Swing desktop application to prepare for migration to a web-based frontend. The analysis covers:
- Complete UI component inventory with ASCII wireframes
- Model architecture and Swing dependencies
- Backend separation strategy for Scala/Play integration

---

## 1. Main Application Window Layout

```
+================================================================================+
|  File   View   Expert   Theme   Help                              [SIP-Creator] |
+================================================================================+
|          |                                                                      |
| [DATA    |  +------------------+  +------------------+  +------------------+    |
|  SETS]   |  | SourceFrame     |  | TargetFrame      |  | CreateFrame      |    |
|          |  | (XML Tree)      |  | (RecDef Tree)    |  | (Mapping Create) |    |
| [QUICK   |  |                 |  |                  |  |                  |    |
|  MAPPING]|  | +-------------+ |  | +-------------+  |  | Source Stats     |    |
|          |  | | source.xml  | |  | | recdef.xml  |  |  | Target Docs      |    |
| [BIG     |  | |   /record   | |  | |   /rdf:RDF  |  |  | Hints Panel      |    |
|  PICTURE]|  | |     /title  | |  | |     /skos   |  |  |                  |    |
|          |  | |     /date   | |  | |       /...  |  |  | [Create Mapping] |    |
| [CODE    |  | +-------------+ |  | +-------------+  |  +------------------+    |
|  TWEAKING|  | [Filter:____]   |  | [Filter:____]    |                          |
|          |  +------------------+  +------------------+                          |
| [DEEP    |                                                                      |
|  DELVING]|  +------------------+  +----------------------------------------+   |
|          |  | RecMappingFrame |  | FieldMappingFrame                       |   |
| [DECADENT|  | (Node Mappings) |  | (Groovy Code Editor)                    |   |
|  DISPLAY]|  |                 |  |                                         |   |
|          |  | > title → label |  |  output.title = input.title?.text()    |   |
| [FUNCTION|  | > date → date   |  |  output.date = cleanDate(input.date)   |   |
|  S]      |  | > creator → ... |  |                                         |   |
|          |  +------------------+  | [Doc] [Output] [Compile Status]        |   |
| [STATS]  |                        +----------------------------------------+   |
|          |  +------------------+  +------------------+                          |
| [REPORT] |  | InputFrame      |  | OutputFrame      |                          |
|          |  | (Record XML)    |  | (RDF/JSON-LD)    |                          |
| [MAPPING |  |                 |  |                  |                          |
|  CODE]   |  | <record>        |  | @prefix skos:... |                          |
|          |  |   <title>...</  |  | <uri> a skos:... |                          |
| [LOG]    |  | </record>       |  |   skos:label ... |                          |
|          |  +------------------+  +------------------+                          |
+----------+----------------------------------------------------------------------+
| StatusPanel (Workflow Buttons)                    | WorkPanel (Background Jobs) |
| [Source] [Analyze] [Map] [Validate] [Process]     | PROCESS [dataset/edm] 45%   |
|    *        *        O       -         -          | ETA: 2:30 remaining         |
+---------------------------------------------------+-----------------------------+
```

---

## 2. Frame Inventory (13 Total)

### 2.1 Core Mapping Frames

| Frame | Class | Purpose | Key Components |
|-------|-------|---------|----------------|
| **Source** | `SourceFrame` | Display source XML structure | JTree + FilterTreeModel, stats overlay |
| **Target** | `TargetFrame` | Display target RecDef schema | JTree + FilterTreeModel, view menu |
| **Create** | `CreateFrame` | Create new node mappings | Source stats, target docs, hints panel |
| **Field Mapping** | `FieldMappingFrame` | Edit Groovy code per field | RSyntaxTextArea (Groovy), doc/output tabs |
| **Rec Mapping** | `RecMappingFrame` | List all node mappings | JList, sort/revert options, selection sync |

### 2.2 Input/Output Frames

| Frame | Class | Purpose | Key Components |
|-------|-------|---------|----------------|
| **Input** | `InputFrame` | Show input XML record | JTree + raw XML text, record navigation |
| **Output** | `OutputFrame` | Show transformed output | RDF/JSON-LD/N-Quads/Turtle format tabs |

### 2.3 Analysis & Reporting Frames

| Frame | Class | Purpose | Key Components |
|-------|-------|---------|----------------|
| **Stats** | `StatsFrame` | Statistical analysis | Charts: word count, frequency, histograms |
| **Report** | `ReportFrame` | Validation reports | Record list with error details |

### 2.4 Code Management Frames

| Frame | Class | Purpose | Key Components |
|-------|-------|---------|----------------|
| **Mapping Code** | `MappingCodeFrame` | Full mapping Groovy code | Lists, builder, complete code view |
| **Functions** | `FunctionFrame` | Custom function library | Function list, editor, library management |
| **Log** | `LogFrame` | System log output | Text area with log messages |

### 2.5 Dataset Management Frame

| Frame | Class | Purpose | Key Components |
|-------|-------|---------|----------------|
| **Data Sets** | `RemoteDataSetFrame` | Local/remote dataset sync | Tables for local/remote, upload/download |

---

## 3. View Arrangements (Predefined Layouts)

```
ARRANGEMENT          FRAMES SHOWN                                    USE CASE
-------------------------------------------------------------------------------
CLEAR               (none)                                          Empty desktop
DATA_SETS           RemoteDataSetFrame                              Dataset management
QUICK_MAPPING       Source, Target, Create, RecMapping              Fast mapping workflow
BIG_PICTURE         Source, Target, Create, RecMapping,             Validation investigation
                    FieldMapping, Input, Output
CODE_TWEAKING       FieldMapping, MappingCode, Functions            Code editing focus
DEEP_DELVING        Source, Target, Create, RecMapping,             Detailed analysis
                    FieldMapping, Input, Output, Stats
DECADENT_DISPLAY    All frames visible                              Wide-screen comprehensive
FUNCTIONS           Functions                                        Function library editing
STATISTICS          Stats                                            Statistical analysis
REPORT              Report                                           Validation reports
MAPPING_CODE        MappingCode                                      Full code view
LOG                 Log                                              System logging
```

---

## 4. Individual Frame Wireframes

### 4.1 SourceFrame
```
+------------------------------------------+
| Source                              [X]  |
+------------------------------------------+
| [Filter: ________________] [Clear]       |
+------------------------------------------+
| - /record                                |
|   +- /title          (1,234 values)      |
|   +- /creator        (856 values)        |
|   +- /date           (1,102 values)      |
|   +- /description    (945 values)        |
|   +- /subject        (2,341 values)      |
|      +- @type                            |
|      +- @href                            |
+------------------------------------------+
| Selected: /record/title                  |
| Unique: 1,234 | Total: 5,678            |
+------------------------------------------+
```

### 4.2 TargetFrame
```
+------------------------------------------+
| Target (edm)                       [X]   |
+------------------------------------------+
| [Filter: ________________] [View: v]     |
+------------------------------------------+
| - /rdf:RDF                               |
|   +- /edm:ProvidedCHO  [required]        |
|      +- dc:title       [required]        |
|      +- dc:creator     [optional]        |
|      +- dc:date        [optional]        |
|      +- dc:description                   |
|   +- /ore:Aggregation                    |
|      +- edm:isShownAt  [required]        |
|      +- edm:isShownBy                    |
+------------------------------------------+
```

### 4.3 CreateFrame
```
+------------------------------------------+
| Create Mapping                     [X]   |
+------------------------------------------+
| SOURCE STATISTICS                        |
| Path: /record/title                      |
| Values: 1,234 unique / 5,678 total      |
| Sample: "Example Title", "Another..."   |
+------------------------------------------+
| TARGET DOCUMENTATION                     |
| dc:title                                 |
| A name given to the resource.           |
| Typically used for the name of the      |
| cultural heritage object.               |
+------------------------------------------+
| MAPPING HINTS                            |
| Suggested: title → dc:title             |
|           creator → dc:creator          |
+------------------------------------------+
| [Create Node Mapping]                    |
+------------------------------------------+
```

### 4.4 FieldMappingFrame
```
+------------------------------------------+
| Field Mapping: dc:title            [X]   |
+------------------------------------------+
| [Code] [Documentation] [Output]          |
+------------------------------------------+
| // Groovy mapping code                   |
| output.dc_title = input.title?.text()   |
|                                          |
| // With transformation                   |
| if (input.title) {                       |
|     def t = input.title.text().trim()   |
|     output.dc_title = cleanText(t)      |
| }                                        |
+------------------------------------------+
| Status: OK | Last compile: 12:34:56     |
+------------------------------------------+
```

### 4.5 RecMappingFrame
```
+------------------------------------------+
| Record Mapping                     [X]   |
+------------------------------------------+
| [Sort: Path v] [Revert v]               |
+------------------------------------------+
| > /record/title    → dc:title           |
| > /record/creator  → dc:creator         |
| > /record/date     → dc:date            |
|   /record/subject  → dc:subject   [!]   |
| > /record/descr... → dc:description     |
+------------------------------------------+
| Selected: dc:title                       |
| Source: /record/title                    |
+------------------------------------------+
```

### 4.6 InputFrame
```
+------------------------------------------+
| Input Record                       [X]   |
+------------------------------------------+
| [Tree] [XML] | Record: [<] 42/1234 [>]  |
+------------------------------------------+
| <record>                                 |
|   <title>The Starry Night</title>       |
|   <creator>Vincent van Gogh</creator>   |
|   <date>1889</date>                     |
|   <description>                         |
|     Oil on canvas painting...           |
|   </description>                        |
| </record>                               |
+------------------------------------------+
```

### 4.7 OutputFrame
```
+------------------------------------------+
| Output                             [X]   |
+------------------------------------------+
| [RDF/XML] [JSON-LD] [N-Quads] [Turtle]  |
+------------------------------------------+
| @prefix dc: <http://purl.org/dc/...> .  |
| @prefix edm: <http://europeana...> .    |
|                                          |
| <urn:dataset:12345> a edm:ProvidedCHO ; |
|   dc:title "The Starry Night" ;         |
|   dc:creator "Vincent van Gogh" ;       |
|   dc:date "1889" .                      |
+------------------------------------------+
```

### 4.8 StatsFrame
```
+------------------------------------------+
| Statistics                         [X]   |
+------------------------------------------+
| [Word Count] [Frequency] [Presence]     |
+------------------------------------------+
|                                          |
|   ####                                   |
|   ####  ####                            |
|   ####  ####  ####                      |
|   ####  ####  ####  ####                |
|   ----  ----  ----  ----                |
|   1-5   6-10  11-20  20+                |
|                                          |
| Field: /record/title                     |
| Total records: 5,678                     |
+------------------------------------------+
```

### 4.9 ReportFrame
```
+------------------------------------------+
| Validation Report                  [X]   |
+------------------------------------------+
| Valid: 5,432 | Invalid: 246 | Total: 5,678 |
+------------------------------------------+
| Record 42: INVALID                       |
|   - Missing required: dc:title          |
|   - SHACL violation: dc:date format     |
|                                          |
| Record 156: INVALID                      |
|   - XSD error: invalid URI              |
+------------------------------------------+
| [Show Record] [Export Report]            |
+------------------------------------------+
```

### 4.10 RemoteDataSetFrame
```
+------------------------------------------+
| Data Sets                          [X]   |
+------------------------------------------+
| LOCAL DATASETS                           |
+------------------------------------------+
| Name              State      Size        |
| amsterdam-museum  MAPPING    45 MB       |
| anne-frank        PROCESSED  23 MB       |
| museum-weesp      SOURCED    12 MB       |
+------------------------------------------+
| [Delete] [Upload]                        |
+------------------------------------------+
| REMOTE DATASETS (Narthex)               |
+------------------------------------------+
| Name              Versions   Updated     |
| rijksmuseum       12         2025-01-10  |
| van-gogh-museum   8          2025-01-08  |
+------------------------------------------+
| [Download] [Refresh]                     |
+------------------------------------------+
```

---

## 5. StatusPanel Workflow

```
+------------------------------------------------------------------------+
| [Source]  [Analyze]  [Map]  [Validate]  [Process]                      |
|    *         *         O        -           -                          |
+------------------------------------------------------------------------+
  State:    SOURCED → ANALYZED_SOURCE → MAPPING → VALIDATED → PROCESSED

  Legend:  * = completed   O = current   - = pending

  Each button triggers a Work job:
  - Source: Import XML source file
  - Analyze: Run AnalysisParser to generate statistics
  - Map: Enter mapping mode (enable code editing)
  - Validate: Run FileProcessor with validation
  - Process: Run FileProcessor to generate output
```

---

## 6. Model Architecture

### 6.1 SipModel (Central Aggregator)

```
SipModel
├── Storage storage              // File I/O (sip-core)
├── WorkModel workModel          // Thread pool + job queue
├── DataSetModel dataSetModel    // Dataset state machine
├── MappingModel mappingModel    // Mapping data (PURE)
├── StatsModel statsModel        // Statistics + tree model
├── CreateModel createModel      // Mapping creation state
├── MappingCompileModel          // Code compilation (UI-tied)
├── FunctionCompileModel         // Function compilation (UI-tied)
├── MappingHintsModel            // Hint suggestions (PURE)
├── FactModel factModel          // Key-value facts (PURE)
├── ReportFileModel              // Report display
├── JDesktopPane desktop         // <<< SWING DEPENDENCY
└── ViewSelector viewSelector    // <<< SWING DEPENDENCY
```

### 6.2 Swing Dependency Analysis

| Model | Swing Deps | Reusability | Notes |
|-------|------------|-------------|-------|
| **MappingModel** | None | REUSABLE | Pure observer pattern |
| **FactModel** | None | REUSABLE | Pure key-value store |
| **MappingHintsModel** | None | REUSABLE | Pure data |
| **Feedback** | Interface | REUSABLE | Abstract user interaction |
| **Work** | Interface | REUSABLE | Abstract async work |
| **DataSetModel** | Timer, SwingListener | REFACTOR | Remove Timer |
| **StatsModel** | Timer, TreeModel | REFACTOR | Extract analysis logic |
| **CreateModel** | JComboBox | REFACTOR | Use Feedback interface |
| **WorkModel** | AbstractListModel, Timer | REFACTOR | Remove ListModel |
| **SipModel** | JDesktopPane | MAJOR REFACTOR | Remove UI refs |
| **MappingCompileModel** | RSyntaxDocument | UI-ONLY | Cannot reuse |
| **FunctionCompileModel** | RSyntaxDocument | UI-ONLY | Cannot reuse |
| **FilterTreeModel** | TreeModel | UI-ONLY | Cannot reuse |
| **NodeMappingListModel** | AbstractListModel | UI-ONLY | Cannot reuse |
| **SourceTreeNode** | TreeCellRenderer | UI-ONLY | Cannot reuse |
| **RecDefTreeNode** | TreeCellRenderer | UI-ONLY | Cannot reuse |

---

## 7. Work/Async System

### 7.1 Work Categories

```
Work.Kind
├── SILENT          // Quick UI updates (~40ms)
├── NETWORK         // Server communication
├── DATA_SET        // Heavy per-dataset work
└── DATA_SET_PREFIX // Heavy per-mapping work

Work.Job (40+ types)
├── PARSE_ANALYZE   // AnalysisParser
├── PROCESS         // FileProcessor
├── VALIDATE        // Validation run
├── UPLOAD          // NarthexUploader
├── DOWNLOAD        // NarthexDatasetDownloader
└── ... (35+ more)
```

### 7.2 Execution Flow

```
User Action
    │
    ▼
Action.actionPerformed()
    │
    ├──────────────────────────────────────┐
    │                                      │
    ▼                                      ▼
sipModel.exec(Swing)              sipModel.exec(Work)
    │                                      │
    ▼                                      ▼
SwingUtilities.invokeLater()      workModel.exec(work)
    │                                      │
    ▼                                      ▼
EDT (Event Dispatch Thread)       Route by Work.Kind
                                           │
                                           ▼
                                   executor.submit(work)
                                           │
                                           ▼
                                   Worker Thread Pool
                                           │
                                           ▼
                                   work.run()
                                           │
                                           ▼
                                   Progress → WorkPanel
```

---

## 8. Backend Separation Strategy

### 8.1 Package Structure Proposal

```
sip-core/           (existing - already framework-agnostic)
├── groovy/         MappingRunner, GroovyCodeResource
├── metadata/       RecDef, NodeMapping
└── files/          Storage, DataSet

sip-model/          (NEW - extracted pure models)
├── MappingModel.java        (copy as-is)
├── FactModel.java           (copy as-is)
├── MappingHintsModel.java   (copy as-is)
├── DataSetModel.java        (refactored - remove Timer)
├── StatsModel.java          (refactored - extract analysis)
├── WorkQueue.java           (new - abstract Work executor)
├── Feedback.java            (copy as-is)
├── Work.java                (copy as-is)
└── ProgressListener.java    (copy as-is)

sip-web/            (NEW - Scala/Play frontend)
├── WebFeedback.scala        (implements Feedback)
├── WebWorkQueue.scala       (implements WorkQueue)
├── controllers/
│   ├── DataSetController.scala
│   ├── MappingController.scala
│   └── ValidationController.scala
└── views/
    ├── datasets/
    ├── mapping/
    └── validation/
```

### 8.2 Key Files to Extract/Refactor

**Directly Reusable (copy to sip-model):**
- `sip-app/.../model/MappingModel.java`
- `sip-app/.../model/FactModel.java`
- `sip-app/.../model/MappingHintsModel.java`
- `sip-app/.../model/Feedback.java`
- `sip-app/.../base/Work.java`
- `sip-app/.../base/ProgressListener.java`

**Require Refactoring:**
- `sip-app/.../model/DataSetModel.java` → Remove javax.swing.Timer
- `sip-app/.../model/StatsModel.java` → Extract analysis from TreeModel
- `sip-app/.../model/WorkModel.java` → Extract WorkQueue interface
- `sip-app/.../model/SipModel.java` → Create CoreSipModel without UI

**Heavy Processing (already mostly clean):**
- `sip-app/.../work/FileProcessor.java`
- `sip-app/.../work/AnalysisParser.java`

---

## 9. Web Frontend Component Mapping

### 9.1 Swing → Web Component Mapping

| Swing Component | Web Equivalent | Library Suggestion |
|-----------------|----------------|-------------------|
| JDesktopPane | Draggable panels or tabs | React DnD, Floating UI |
| JInternalFrame | Modal/Panel component | Headless UI Dialog |
| JTree | Tree component | React Arborist, Mantine Tree |
| JList | List/Table | TanStack Table |
| RSyntaxTextArea | Code editor | Monaco Editor, CodeMirror |
| JTabbedPane | Tabs | Headless UI Tabs |
| JSplitPane | Resizable panels | React Split |
| JTable | Data table | TanStack Table |
| StatusPanel | Progress bar + buttons | Native HTML |
| WorkPanel | Job list with progress | Custom component |

### 9.2 View Arrangement Implementation

```typescript
// Web equivalent of frame-arrangements.xml
const arrangements = {
  QUICK_MAPPING: {
    panels: ['source', 'target', 'create', 'recMapping'],
    layout: {
      source: { x: 0, y: 0, w: 3, h: 6 },
      target: { x: 3, y: 0, w: 3, h: 6 },
      create: { x: 6, y: 0, w: 3, h: 3 },
      recMapping: { x: 6, y: 3, w: 3, h: 3 }
    }
  },
  BIG_PICTURE: {
    panels: ['source', 'target', 'create', 'recMapping',
             'fieldMapping', 'input', 'output'],
    layout: { ... }
  }
};
```

---

## 10. First Version: Swing in WebView

For the initial web version (Swing in WebView approach):

### 10.1 Technology Options

1. **CheerpJ** - Compile Java/Swing to WebAssembly
2. **JPro** - Run JavaFX (would require Swing→JavaFX migration first)
3. **Eclipse SWT + RAP** - Server-side rendering
4. **JNLP/WebStart replacement** - Download and run locally

### 10.2 Recommended Approach

The cleanest path for "Swing in browser" without code changes:
1. Package application with jpackage
2. Serve via browser with download prompt
3. Or use Electron-style wrapper with embedded JRE

---

## 11. Files for Reference

### Key UI Files
```
sip-app/src/main/java/eu/delving/sip/
├── Application.java              # Main entry, window setup
├── base/
│   ├── FrameBase.java           # Base class for all frames
│   ├── Work.java                # Async work interface
│   ├── Swing.java               # Swing thread wrapper
│   └── ProgressListener.java    # Progress callback
├── frames/
│   ├── AllFrames.java           # Frame manager + arrangements
│   ├── SourceFrame.java
│   ├── TargetFrame.java
│   ├── CreateFrame.java
│   ├── FieldMappingFrame.java
│   ├── RecMappingFrame.java
│   ├── InputFrame.java
│   ├── OutputFrame.java
│   ├── StatsFrame.java
│   ├── ReportFrame.java
│   ├── MappingCodeFrame.java
│   ├── FunctionFrame.java
│   ├── LogFrame.java
│   └── RemoteDataSetFrame.java
├── panels/
│   ├── StatusPanel.java         # Workflow buttons
│   └── WorkPanel.java           # Background jobs
├── menus/
│   ├── FileMenu.java
│   ├── ExpertMenu.java
│   ├── ThemeMenu.java
│   ├── HelpMenu.java
│   └── RevertMappingMenu.java
└── model/
    ├── SipModel.java            # Central aggregator
    ├── MappingModel.java        # Mapping data (PURE)
    ├── DataSetModel.java        # Dataset state
    ├── StatsModel.java          # Statistics
    ├── WorkModel.java           # Job queue
    ├── CreateModel.java         # Creation state
    ├── MappingCompileModel.java # Code compilation
    ├── FunctionCompileModel.java
    ├── FactModel.java           # Key-value (PURE)
    ├── MappingHintsModel.java   # Hints (PURE)
    └── Feedback.java            # User interaction interface
```

---

## 12. Summary

### What We Have
- 13 internal frames in MDI architecture
- 11 predefined view arrangements
- Central SipModel aggregating ~10 sub-models
- Async work system with 40+ job types
- Clean Feedback abstraction for user interaction

### Reusable Without Changes
- MappingModel, FactModel, MappingHintsModel
- Work interface, ProgressListener interface
- Feedback interface pattern
- All sip-core processing logic

### Requires Refactoring
- DataSetModel (remove Timer)
- StatsModel (extract analysis logic)
- WorkModel (abstract the queue, remove ListModel)
- SipModel (create UI-free CoreSipModel)

### UI-Specific (Rebuild for Web)
- All frame UIs
- Tree/List models and renderers
- Code editor integration
- Progress/status displays

---

## 13. Web Migration Options Analysis

### 13.1 Option A: CheerpJ (WebAssembly)

**How It Works:**
- Compiles Java bytecode to WebAssembly + JavaScript at runtime
- Runs unmodified .jar files directly in browser
- Full Java SE runtime (Java 8, 11, 17; Java 21 planned early 2026)
- Each Swing window converts to HTML element hierarchy with HTML5 canvases

**Viability Assessment: MODERATE**

| Aspect | Status | Notes |
|--------|--------|-------|
| Swing Components | Works | RSyntaxTextArea, JTree, all components supported |
| Tree Views | Works | Tested with 800,000+ elements |
| File I/O | Limited | IndexedDB for persistence, upload/download via browser |
| Network | Limited | Same-origin only (or requires Tailscale) |
| Large Datasets | Problematic | >500MB datasets hit browser memory limits |
| Firefox | Poor | 10x slower than Chrome |
| Startup | Slow | 5-15 seconds initial load |
| Licensing | Cost | Requires paid license for business use |

**Best For:** Demo/proof-of-concept, small-medium datasets (<100MB), institutional deployments where Narthex is same-origin.

**Not Recommended For:** Large batch processing, Firefox users, cost-sensitive projects.

---

### 13.2 Option B: Svelte Frontend

**Why Svelte:**
- 40-60% smaller bundles than React (compiler-first, no virtual DOM)
- Fine-grained reactivity ideal for real-time updates
- Built-in stores for complex state management
- SvelteKit provides full-stack capabilities

**Recommended Tech Stack:**

```
SvelteKit (framework)
├── Layout: PaneForge (resizable panels)
├── Tree View: svelte-treeview (XML/RDF hierarchy) + TanStack Virtual
├── Code Editor: Monaco Editor (Groovy syntax highlighting)
├── Components: Bits UI (headless, accessible primitives)
├── Styling: Tailwind CSS
├── Real-time: WebSocket or SSE for progress updates
└── State: Built-in Svelte stores (feature-based modules)
```

**Key Libraries:**

| Purpose | Library | Notes |
|---------|---------|-------|
| Resizable Panels | PaneForge | Nested pane groups, localStorage persistence |
| Tree View | svelte-treeview | Svelte 5, async loading, search indexing |
| Virtual Scrolling | TanStack Virtual | 60FPS with 100,000+ nodes |
| Code Editor | Monaco Editor | svelte-monaco wrapper, Groovy support |
| UI Components | Bits UI | Headless, accessible, built on Melt UI |

**Backend Integration:**
- REST API for CRUD operations (datasets, mappings)
- Server-Sent Events (SSE) for progress streaming
- WebSocket for real-time validation feedback

**Performance Considerations:**
- Virtual scrolling essential for large trees
- Web Workers for background code compilation
- Lazy-load tree nodes on expansion

---

### 13.3 Option C: Narthex Integration (RECOMMENDED)

**Key Discovery:** Narthex already has sophisticated mapping infrastructure!

**Current Narthex Mapping Capabilities:**

```
Already Implemented:
├── DefaultMappingRepo (organization-wide mappings)
│   └── ~/NarthexFiles/orgid/factory/prefix/mappings/
├── DatasetMappingRepo (per-dataset mappings)
│   └── ~/NarthexFiles/orgid/datasets/{spec}/mappings/
├── Version Control (rollback, compare, track source)
├── Mapping XML Storage (timestamped versions with SHA-256)
├── SIP Integration (sip-core 1.4.0-SNAPSHOT dependency)
├── MappingEngine (executes Groovy code via sip-core)
└── Processing Pipeline (SourceProcessor applies mappings)

Missing:
└── Visual Mapping Editor UI
```

**Existing API Endpoints (in AppController.scala):**

```
GET  /narthex/app/default-mappings                    # List all mappings
GET  /narthex/app/default-mappings/:prefix/:name      # Get mapping info
GET  /narthex/app/default-mappings/:prefix/:name/xml/:v  # Get XML content
POST /narthex/app/default-mappings/:prefix/create     # Create new
POST /narthex/app/default-mappings/:prefix/:name/upload  # Upload version
POST /narthex/app/dataset/:spec/set-mapping-source    # Select mapping
POST /narthex/app/dataset/:spec/rollback-mapping/:hash   # Rollback

# Statistics
GET  /narthex/app/dataset/:spec/source-sample/:size/*path
GET  /narthex/app/dataset/:spec/source-histogram/:size/*path
```

**Architecture:**

```
Narthex (Play Framework 2.8.20 / Scala 2.13)
├── Frontend: AngularJS 1.3.17 (legacy)
├── Actors: Akka 2.6.21 for async processing
├── Triple Store: Apache Fuseki (SPARQL)
├── Dependencies: eu.delving:sip-core:1.4.0-SNAPSHOT
└── Mapping: Two-tier (default + dataset-specific)
```

**Integration Readiness:**

| Component | Status | Notes |
|-----------|--------|-------|
| Mapping Storage | Ready | DefaultMappingRepo & DatasetMappingRepo |
| Mapping APIs | Ready | Full CRUD endpoints exist |
| SIP Integration | Ready | MappingEngine executes Groovy |
| Processing Pipeline | Ready | SourceProcessor uses effectiveMappingXml |
| Version Control | Ready | Rollback, compare, track source |
| Groovy Execution | Ready | Via sip-core MappingEngine |
| Visual Editor UI | Missing | This is what needs to be built |

---

## 14. Recommended Approach: Narthex + Svelte Visual Editor

### Why This Approach

1. **Narthex already has backend** - no need to build mapping storage, versioning, or execution
2. **sip-core integration exists** - Groovy mapping execution proven working
3. **User is in dataset scope** - natural workflow integration
4. **Statistics available** - source analysis endpoints ready
5. **No separate processing needed** - existing pipeline handles validation

### What Needs to Be Built

**New Svelte Component: Visual Mapping Editor**

```
MappingEditor/
├── SourceTreePanel/       # XML source structure (from statistics)
│   └── Uses: svelte-treeview + virtual scrolling
├── TargetTreePanel/       # RecDef schema structure
│   └── Uses: svelte-treeview
├── MappingPanel/          # Visual drag-drop mapping
│   └── Connect source paths to target fields
├── CodeEditorPanel/       # Generated Groovy code
│   └── Uses: Monaco Editor
├── PreviewPanel/          # Sample record transformation
│   └── Shows RDF/JSON-LD output
└── VersionHistoryPanel/   # Mapping versions (uses existing API)
```

**New API Endpoints Needed:**

```
# Get source samples for visual editor
GET  /narthex/app/dataset/:spec/mapping-editor/samples/:count

# Generate mapping XML from visual definition
POST /narthex/app/dataset/:spec/mapping-editor/generate

# Preview mapping on sample records
POST /narthex/app/dataset/:spec/mapping-editor/preview

# Get mapping editor state
GET  /narthex/app/dataset/:spec/mapping-editor/state
POST /narthex/app/dataset/:spec/mapping-editor/save-state
```

### Implementation Phases

**Phase 1: Source Tree Visualization**
- Fetch source analysis from existing `/source-sample` endpoints
- Display as interactive tree with statistics overlay
- Virtual scrolling for large datasets

**Phase 2: Target Schema Display**
- Load RecDef from factory or SIP
- Display as expandable tree with field documentation
- Mark required vs optional fields

**Phase 3: Visual Mapping Interface**
- Drag source paths to target fields
- Auto-suggest mappings based on path names
- Show existing mappings from loaded XML

**Phase 4: Code Generation & Preview**
- Generate Groovy code from visual mappings
- Display in Monaco Editor (editable)
- Preview transformation on sample records

**Phase 5: Integration with Narthex**
- Save mappings via existing API
- Trigger processing with new mapping
- Show validation results

---

## 15. Comparison of Options

| Criteria | CheerpJ | Svelte Standalone | Narthex + Svelte |
|----------|---------|-------------------|------------------|
| Development Effort | Low | High | Medium |
| Backend Work | None | Full stack | Minimal (APIs exist) |
| Large Datasets | Poor | Good (server-side) | Good (existing) |
| User Experience | Swing-like | Modern web | Modern web |
| Maintenance | JVM updates | Full ownership | Shared with Narthex |
| Cost | License fees | Infrastructure | Infrastructure |
| Time to MVP | 1-2 weeks | 3-4 months | 1-2 months |

**Recommendation:** **Narthex + Svelte Visual Editor**

- Fastest path to production
- Leverages existing infrastructure
- Natural workflow integration
- Statistics already available
- Processing pipeline ready

---

## 16. Narthex File Structure Reference

```
~/NarthexFiles/
├── {orgid}/                          # Organization ID
│   ├── factory/
│   │   └── {prefix}/                # Schema factory
│   │       ├── {prefix}_record-definition.xml
│   │       ├── {prefix}_validation.xsd
│   │       └── mappings/             # DEFAULT MAPPINGS
│   │           └── {name}/versions/
│   │               ├── metadata.json
│   │               └── *.xml
│   ├── datasets/
│   │   └── {spec}/                  # Dataset
│   │       ├── raw/                 # Uploaded source
│   │       ├── source/              # After source parsing
│   │       ├── sourceTree/          # Source analysis
│   │       ├── processed/           # After mapping/processing
│   │       ├── tree/                # Processed analysis
│   │       ├── sips/                # Generated SIP zips
│   │       ├── mappings/            # DATASET MAPPINGS
│   │       │   ├── metadata.json
│   │       │   └── versions/
│   │       │       └── *.xml
│   │       └── trends.jsonl
│   └── rawDir/
│       └── {spec}.xml               # Pocket file for processing
└── sipsDir/                          # Cross-dataset SIPs
    └── {spec}__{timestamp}.sip.zip
```

---

---

## 17. Svelte vs AngularJS for Narthex Mapping Editor

### 17.1 Current Narthex Frontend State

**AngularJS Version**: 1.3.17 (released 2014)

| Aspect | Current State | Assessment |
|--------|---------------|------------|
| Framework Version | AngularJS 1.3.17 | CRITICAL - EOL, no security updates |
| Build System | RequireJS + sbt-web | OUTDATED - No modern tooling |
| Module Pattern | AMD with define() | LEGACY - No ESM support |
| TypeScript | None | MISSING - Runtime error prone |
| Hot Reload | None | MISSING - Slow development |
| Performance | Digest cycle tuned to 15 iterations | WARNING - Already stressed |
| Testing | No frontend tests visible | GAP |
| Total JS Code | ~8,000 lines across 39 files | MODERATE complexity |

**Technical Debt Identified:**
- Manual DOM manipulation in filters
- String concatenation for URLs
- Callback-based async (no async/await)
- God controllers (600+ lines)
- Hardcoded prefixes in code
- String.prototype polyfills for basic methods

### 17.2 Comparison: Extend AngularJS vs Replace with Svelte

| Criteria | Extend AngularJS | Replace with Svelte |
|----------|------------------|---------------------|
| **Security** | No updates (EOL) | Active maintenance |
| **Performance** | Digest cycle limits | Compiled reactivity |
| **Bundle Size** | Large (Angular runtime) | 40-60% smaller |
| **Developer Experience** | Outdated patterns | Modern tooling |
| **TypeScript** | Not supported | First-class support |
| **Hot Reload** | Not available | Built-in with Vite |
| **Tree Components** | Custom, limited | svelte-treeview, TanStack |
| **Code Editors** | Prism (CDN) | Monaco/CodeMirror native |
| **Talent Pool** | Shrinking rapidly | Growing |
| **Future Proof** | Dead end | Active ecosystem |
| **Migration Risk** | Low short-term | Medium short-term |
| **Long-term Cost** | High (tech debt) | Lower (maintainable) |

**Verdict: Svelte is strongly recommended**

### 17.3 Integration Approaches for Svelte in Narthex

#### Option A: Svelte Islands (Hybrid)
Embed Svelte components within existing AngularJS pages.

```
Narthex (AngularJS shell)
├── Header/Nav (AngularJS)
├── Dataset List (AngularJS)
├── Dataset Detail (AngularJS)
│   └── Mapping Editor (Svelte Island) ← NEW
└── Footer (AngularJS)
```

**Pros:**
- Minimal disruption to existing features
- Can deploy incrementally
- Low risk for initial rollout

**Cons:**
- Two build systems to maintain
- Communication complexity between frameworks
- Bundle size overhead

**Implementation:**
```javascript
// In AngularJS template
<div id="svelte-mapping-editor"
     data-dataset-spec="{{dataset.spec}}"
     data-org-id="{{orgId}}">
</div>

// Svelte mounts to this element
new MappingEditor({
  target: document.getElementById('svelte-mapping-editor'),
  props: { spec, orgId }
});
```

#### Option B: Svelte SPA with Shared Backend (Recommended)
New Svelte frontend served alongside AngularJS, sharing Play backend.

```
Play Framework Backend
├── /narthex/app/* → AngularJS (existing)
├── /narthex/api/* → REST APIs (shared)
└── /narthex/editor/* → Svelte SPA (new)
```

**Pros:**
- Clean separation of concerns
- Independent deployment
- Full modern tooling for new code
- Can migrate features gradually

**Cons:**
- Two frontends to maintain initially
- Requires route coordination
- Session/auth sharing needed

**Build Pipeline:**
```
sip-creator/
├── narthex/                    # Existing Play app
│   ├── app/                    # Scala backend
│   └── public/                 # AngularJS (legacy)
└── narthex-editor/             # New Svelte app
    ├── src/
    │   ├── lib/
    │   │   ├── components/     # Reusable UI
    │   │   ├── stores/         # State management
    │   │   └── api/            # Play API client
    │   └── routes/
    │       └── [spec]/         # Dataset-scoped pages
    ├── vite.config.ts
    └── package.json
```

#### Option C: Full Svelte Migration (Long-term)
Replace entire AngularJS frontend with Svelte over time.

**Timeline:** 8-12 weeks for full migration
**Phases:**
1. Set up Svelte + Vite build (1 week)
2. Migrate defaultMappings UI first (2 weeks)
3. Migrate dataset list/detail (3 weeks)
4. Migrate remaining modules (3 weeks)
5. Remove AngularJS (1 week)

---

## 18. Visual Mapping Editor: What's Actually Needed

### 18.1 Core Features Required

| Feature | Description | Complexity |
|---------|-------------|------------|
| Source Tree | XML structure with statistics | Medium |
| Target Tree | RecDef schema with required indicators | Medium |
| Connection Lines | Visual links between source → target | High |
| Drag-to-Map | Create mappings by dragging | High |
| Code Editor | Groovy with syntax highlighting | Medium |
| Live Preview | Transform sample records in real-time | Medium |
| Validation | Type checking, schema constraints | Medium |
| Undo/Redo | Command pattern for reversible edits | Low |
| Version Control | Save/load/rollback mappings | Low (API exists) |

### 18.2 UI Layout (Based on Industry Tools)

```
+------------------------------------------------------------------+
| Mapping Editor: amsterdam-museum / edm                    [Save] |
+------------------------------------------------------------------+
|                                                                  |
| +------------------+    CONNECTIONS    +------------------+      |
| | SOURCE TREE      |==================>| TARGET TREE      |      |
| | (XML Structure)  |    [SVG/Canvas]   | (RecDef Schema)  |      |
| |                  |                   |                  |      |
| | - /record        |    ─────────────> | - /rdf:RDF       |      |
| |   +- /title ●────┼────────●─────────┼──+- dc:title ●    |      |
| |   +- /creator ●──┼────────●─────────┼──+- dc:creator    |      |
| |   +- /date       |                   |   +- dc:date     |      |
| |   +- /subject    |                   |   +- dc:subject  |      |
| |                  |                   |                  |      |
| | [Filter: ____]   |                   | [Filter: ____]   |      |
| +------------------+                   +------------------+      |
|                                                                  |
| +---------------------------------------------------------------+|
| | GROOVY CODE                                            [Docs] ||
| |---------------------------------------------------------------|
| | output.dc_title = input.title?.text()                         ||
| | output.dc_creator = input.creator?.text()                     ||
| |                                                               ||
| +---------------------------------------------------------------+|
|                                                                  |
| +---------------------------------------------------------------+|
| | PREVIEW                                    Record: [<] 1 [>]  ||
| |---------------------------------------------------------------|
| | Input XML              | Output RDF/JSON-LD                   ||
| | <record>               | @prefix dc: <...>                    ||
| |   <title>Starry...</   | <uri> dc:title "Starry Night" .     ||
| +---------------------------------------------------------------+|
+------------------------------------------------------------------+
```

### 18.3 Technical Implementation Details

#### Connection Line Rendering (SVG Approach)

```svelte
<!-- ConnectionOverlay.svelte -->
<svg class="connections-layer" bind:this={svg}>
  {#each mappings as mapping}
    <path
      d={calculatePath(mapping.sourceNode, mapping.targetNode)}
      class="connection-line"
      class:selected={selectedMapping === mapping}
      on:click={() => selectMapping(mapping)}
    />
  {/each}

  {#if dragging}
    <path d={dragPath} class="drag-preview" />
  {/if}
</svg>

<script>
  function calculatePath(source, target) {
    const sourceRect = source.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    const svgRect = svg.getBoundingClientRect();

    const x1 = sourceRect.right - svgRect.left;
    const y1 = sourceRect.top + sourceRect.height/2 - svgRect.top;
    const x2 = targetRect.left - svgRect.left;
    const y2 = targetRect.top + targetRect.height/2 - svgRect.top;

    // Bezier curve for smooth connection
    const midX = (x1 + x2) / 2;
    return `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
  }
</script>
```

#### Tree Virtualization (TanStack Virtual)

```svelte
<!-- VirtualTree.svelte -->
<script>
  import { createVirtualizer } from '@tanstack/svelte-virtual';

  const virtualizer = createVirtualizer({
    count: flattenedNodes.length,
    getScrollElement: () => scrollElement,
    estimateSize: () => 28, // row height
    overscan: 5
  });
</script>

<div bind:this={scrollElement} class="tree-container">
  <div style="height: {$virtualizer.getTotalSize()}px">
    {#each $virtualizer.getVirtualItems() as row}
      <TreeNode
        node={flattenedNodes[row.index]}
        style="transform: translateY({row.start}px)"
      />
    {/each}
  </div>
</div>
```

#### State Management (Svelte Stores)

```typescript
// stores/mapping.store.ts
import { writable, derived } from 'svelte/store';

interface MappingState {
  sourceTree: TreeNode[];
  targetTree: TreeNode[];
  mappings: NodeMapping[];
  selectedMapping: NodeMapping | null;
  groovyCode: string;
  compileState: 'idle' | 'compiling' | 'valid' | 'error';
  previewOutput: string;
}

export const mappingState = writable<MappingState>({...});

// Derived store for connection rendering
export const connections = derived(mappingState, $state =>
  $state.mappings.map(m => ({
    sourceNode: findNode($state.sourceTree, m.sourcePath),
    targetNode: findNode($state.targetTree, m.targetPath),
    mapping: m
  }))
);
```

### 18.4 Development Timeline

| Phase | Description | Duration |
|-------|-------------|----------|
| **Phase 1** | Project setup + basic layout | 1 week |
| **Phase 2** | Source/Target tree components | 2 weeks |
| **Phase 3** | Connection line rendering | 2 weeks |
| **Phase 4** | Drag-to-map interaction | 2 weeks |
| **Phase 5** | Code editor integration | 1 week |
| **Phase 6** | Live preview + compilation | 2 weeks |
| **Phase 7** | API integration (Narthex) | 1 week |
| **Phase 8** | Testing + polish | 2 weeks |
| **Total** | | **13 weeks** |

---

## 19. Recommended Implementation Plan

### Phase 1: Foundation (Weeks 1-2)
- Set up narthex-editor Svelte project with Vite
- Configure Play routes to serve Svelte app at `/narthex/editor/`
- Implement API client for existing Narthex endpoints
- Create basic layout with PaneForge panels
- Set up Svelte stores for state management

### Phase 2: Tree Components (Weeks 3-4)
- Implement SourceTreePanel with statistics display
- Implement TargetTreePanel with RecDef loading
- Add TanStack Virtual for large trees
- Implement filtering and search
- Add node selection and highlighting

### Phase 3: Visual Mapping (Weeks 5-7)
- Create SVG connection overlay
- Implement drag-to-create mapping interaction
- Add click-to-select and delete connections
- Visual feedback during drag operations
- Sync visual state with mapping model

### Phase 4: Code & Preview (Weeks 8-10)
- Integrate Monaco Editor for Groovy code
- Implement Groovy code generation from visual mappings
- Add bidirectional sync (visual ↔ code)
- Create preview panel with sample record transformation
- Implement compilation status feedback

### Phase 5: Integration & Polish (Weeks 11-13)
- Connect to Narthex mapping APIs (save/load/rollback)
- Add version history panel
- Implement undo/redo
- Performance optimization
- Testing and bug fixes

---

## 20. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Performance with large schemas | Medium | High | Virtual scrolling, lazy loading |
| Complex state synchronization | Medium | Medium | Well-designed store architecture |
| SVG connection rendering lag | Low | Medium | Canvas fallback if needed |
| API incompatibilities | Low | Low | Narthex APIs already stable |
| Team learning curve (Svelte) | Medium | Low | Svelte has gentle learning curve |
| Integration with existing auth | Low | Medium | Share session cookies |

---

## Next Steps

1. **Phase 1**: Create narthex-editor Svelte project with Vite
2. **Phase 2**: Implement tree components with virtualization
3. **Phase 3**: Build connection line rendering with SVG
4. **Phase 4**: Add drag-to-map interaction
5. **Phase 5**: Integrate Monaco Editor and code generation
6. **Phase 6**: Connect to Narthex APIs for persistence
7. **Phase 7**: Polish, test, and optimize
