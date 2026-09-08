# sip-app threading hardening

**Status:** planned 2026-09-08, not started. Decision recorded in
`docs/decisions/0001-edt-ownership-and-per-dataset-job-serialization.md` (Proposed;
flip to Accepted when step 1 merges).

## Goal

Stop the class of GUI crashes where the EDT reads a Swing model or a model field that a
worker thread is replacing, and stop two jobs for the same dataset overlapping. Do it in
the model layer once, not per frame.

## Current state

Ten fix commits landed on `main` on 2026-09-08 from a Sentry triage, none pushed yet:

```
da1404be fix(sip-app): do not report user cancels of analysis or download as errors
1e3e446c fix(sip-app): stop WorkModel job list timer racing the background driver
bbc22077 fix(sip-app): correct "foudn" typo in missing validation XSD message
e0ef471a fix(sip-app): keep RemoteDataSetFrame work item list consistent with the JList
eb67b666 fix(sip-core): map RDF-style xsd: datatypes to xs: in XsdGenerator
9e3652f6 fix(schema-repo,sip-app): make the unknown SchemaVersion fallback usable
561b9b8e fix(sip-app): write source.xml.gz atomically in fromSipZip
03c1ef3f fix(sip-app): tolerate already-removed mapping in RemoveNodeMappingAction
300d6faa fix(sip-app): clear CreateModel target when RecMapping is replaced
2fdc2dfe fix(sip-core): skip xml:lang empty guard when no enclosing loop var
```

Three read-only surveys of `sip-app` followed. Their findings, condensed, are the
inventory below. File and line references are as of `da1404be`.

### Inventory A: notifications and Swing touched off the EDT

| Site | What runs off-EDT |
|---|---|
| `model/MappingModel.java:109-147, 206-209` | `notifyLockChanged`, `notifyFunctionChanged`, `nodeMapping*`, `populationChanged`, `fireRecMappingSet` fire on the mutating thread |
| `model/CreateModel.java:278-284` | `fireStateChanged` on `SILENT` workers |
| `model/MappingCompileModel.java:741-757` | `notifyStateChange`, `notifyCodeCompiled`, `notifyMappingComplete` from `MappingJob` |
| `model/MappingCompileModel.java:529` | `outputArea.setDocument(outputDocument)` from `MappingJob.compilationComplete` |
| `model/StatsModel.java:123` | `sourceTreeModel.setRoot` from the `CLEAR_FACTS_STATS` work (`Application.java:317-321`) |
| `frames/{Create,Source,RecMapping,Function,Target,FieldMapping}Frame.java` `lockChanged` | `FrameBase.setFrameLocked` → glass pane `setVisible` |
| `frames/FieldMappingFrame.java:250-265` | `functionModel.refresh()` fires list events on `recMappingSet` and `functionChanged` |
| `frames/FieldMappingFrame.java:117-132, 221-227` | `contextVarModel.setList` on `CreateModel` transition |
| `frames/TargetFrame.java:266, 270` | `getRecDefTreeRoot().getRecDefTreeNode(node).fireChanged()` unchecked, on the pool; `FilterNode.fireChanged` throws if `filterModel` unset |
| `panels/StatusPanel.java:52-66` | `setBorder` on `recMappingSet` |
| `model/NodeMappingListModel.java:87-92` | `indexOf` on the plain `entries` list on the caller's thread |
| `model/MappingSaveTimer.java:120-152` | `Timer.restart()` from the mutating thread |
| `model/DataSetModel.java:165` | `timer.restart()` from the `CHECK_STATE` worker |
| `xml/FileProcessor.java:750-790` + `actions/ValidateAction.java:105-124` | `Termination` callbacks on the consumer thread run `deleteResults()` and `ReportFileModel.refresh()` there |
| `xml/AnalysisParser.java:144-159` + `model/SipModel.java:311-329` | `success`/`failure` on the parser thread; `setStats` there |
| `base/NetworkClient.java:234-249, 279-303` | `loginFailure`, `listReceived`, `failed` on the network thread (only `RemoteDataSetFrame` re-marshals) |
| `base/VisualFeedback.java:117-135, 211-237` | `form`/`ask`/`confirm` build and show dialogs on the calling worker thread |

### Inventory B: job overlap in `WorkModel`

| Site | Problem |
|---|---|
| `model/WorkModel.java:141-158` | `dataSetPrefix` merges only when `prefix == null`, then `contextPrefix.equals(null)`; never merges |
| `model/WorkModel.java:160-175` | `dataSet` skips contexts whose head is `DATA_SET_PREFIX`, so `SET_DATASET`/`SCAN_RECORDS` overlap `PROCESS` |
| `model/WorkModel.java:121-123` | `SILENT` jobs go straight to the executor |
| `model/WorkModel.java:63-74` | pool is `ThreadPoolExecutor(0, MAX_VALUE, SynchronousQueue)` |
| `model/WorkModel.java:108-116, 146-172` | `isDataSetBusy` and merge scans peek the queue twice; not atomic with `justDoIt` |
| `model/WorkModel.java:303-313` | `JobContext.add` busy guard unreachable for `FileProcessor` |
| `actions/ValidateAction.java:79-81` | only `setEnabled(false)` guards double Validate |
| `frames/RemoteDataSetFrame.java:678-692` | double Open queues two `SET_DATASET` chains |
| `menus/RevertMappingMenu.java:96-99` | `setRecMapping` then a full `setDataSet` reload on top |

### Inventory C: unowned model state

Plain fields written on workers, read on the EDT: `DataSetModel.dataSet/currentState`,
`MappingModel.recMapping/recDefTreeRoot` (lazy init reachable from both threads),
`StatsModel.sourceTree/sourceTreeModel/recordCount`, `FactModel.facts`,
`CreateModel.{sourceTreeNodes,recDefTreeNode,nodeMapping,state,setter}`,
`MappingCompileModel.{recMapping,nodeMapping,metadataRecord,validator,MappingRunner,assertions,enabled,ignoreDocChanges,rdfFormat}`,
`FunctionCompileModel.{functionRunner,mappingFunction,ignoreDocChanges}`,
`MappingSaveTimer.{freezeMode,running}`, `ReportFileModel.reportFile`, `SipModel.parser`,
`WorkModel.JobContext.{start,future,progressImpl}` and `ProgressImpl` counters,
`ReportFile.Rec` fields.

Plain collections iterated on one thread and mutated on another:
`SourceTreeNode.nodeMappings` (HashSet) and `children`, `NodeMappingListModel.entries`,
`recMapping.getNodeMappings()` in `frames/MappingCodeFrame.java:196-213` and
`model/MappingHintsModel.java:76-92`.

Unchecked check-then-act: `model/CreateModel.java:108-109, 153-154, 252-254`,
`model/MappingModel.java:101-107`, `frames/FunctionFrame.java:372, 405, 434`,
`frames/CreateFrame.java:292-294`, `frames/TargetFrame.java:177`.

### Inventory D: list-model event bookkeeping

`fireIntervalAdded(this, 0, size)` where `size` is the count (should be `size-1`), and
related defects:

| Model | Sites |
|---|---|
| `model/NodeMappingListModel.java` | `:152` added off-by-one; `:134` `fireContentsChanged(0, getSize())`; `:101`, `:114` fire with the anonymous `Swing` as source; `:57` `getElementAt` returns null, renderer `NodeMappingEntry.java:99` does not null-check; `NodeMappingEntry.list` is static (`:38`) |
| `frames/RemoteDataSetFrame.java` `DownloadModel` | `:404` removed off-by-one and fired before `clear()`; `:414` added off-by-one; `:386` alias makes `clear()` silent |
| `frames/RemoteDataSetFrame.java` `UploadModel` | `:547`, `:558` same; `:521` alias; `:541` throws `RuntimeException` on the EDT |
| `frames/FieldMappingFrame.java` `FunctionListModel`, `ContextVarListModel` | `:318`, `:321`, `:341`, `:344` off-by-one; `:308`, `:353` no bounds check |
| `frames/FunctionFrame.java` `FunctionListModel` | `:477`, `:483` off-by-one; `:496` no upper bound |
| `frames/StatsFrame.java` `HistogramModel` | `:250`, `:254` off-by-one; `:265` no bounds; renderer `:272` no null check |
| `frames/DictionaryPanel.java` `DictionaryModel`, `ValueModel` | `:186`, `:192`, `:355`, `:359` off-by-one; `:369` no bounds |
| `files/ReportFile.java` `ReportedRecListModel` | `:365` no bounds check |
| `model/FilterTreeModel.java:133-136` | `refreshNode` mixes filtered index with unfiltered path |

### Inventory E: Sentry noise (alerts with a throwable for expected conditions)

`model/WorkModel.java:72` (every throwable incl. `CancelException`),
`base/NetworkClient.java:248, 302, 449` (offline, unauthorized, upload abort; two of them
report twice), `frames/RemoteDataSetFrame.java:596, 818`, `model/MappingHintsModel.java:111`,
`xml/FileProcessor.java:776` (user chose "Investigate").

## Steps

Ordered by leverage. Each step is one PR, green on `mvn test -pl sip-app` and
`mvn test -pl sip-core`, and each ends with a human diff review before commit.

### Step 1: make `WorkModel` merging real

- Fix `dataSetPrefix`: merge into any context whose head job is for the same dataset,
  regardless of prefix. Drop the `prefix == null` condition and the `todo`.
- Make merge-or-create atomic: one `synchronized` block around scan plus `justDoIt`,
  or a `ConcurrentHashMap<String, JobContext>` keyed by dataset spec.
- Route `SILENT` jobs that carry a dataset (`SELECT_*`, `CREATE_MAPPING`,
  `REMOVE_NODE_MAPPING`, `REFRESH_DICTIONARY`, `DUPLICATE_ELEMENT`) through the
  dataset context; keep true `SILENT` for jobs that touch no shared model.
- Remove the EDT timer's call to `doWork`; the background loop is the sole driver, the
  timer only snapshots. Delete the now-dead `NoSuchElementException` catch.
- Make `ProgressImpl` counters and `JobContext.start/future` `volatile`.
- Check: two rapid Validate clicks produce one `FileProcessor` and one "busy" alert.
  Two rapid Open clicks produce one load. `RevertMappingMenu` no longer double-loads.

**Step 1 review: Human diff review**
Reference skill: diff-review-before-task-commit
Wait for human acknowledgment before proceeding to commit.

### Step 2: marshal notifications in the models

- `Swing.Exec.later`: run synchronously when already on the EDT.
- `MappingModel.fireRecMappingSet`, `notifyLockChanged`, `notifyFunctionChanged`,
  `nodeMappingChanged/Added/Removed`, `populationChanged`: dispatch via
  `Swing.Exec.later`. Rename `SetListener`/`ChangeListener` to `SwingSetListener`/
  `SwingChangeListener` (or document the guarantee on the existing names; pick one and
  apply it everywhere).
- Same for `CreateModel.fireStateChanged`, `MappingCompileModel.notify*`,
  `FunctionCompileModel` listeners, `FactModel`, `ReportFileModel`,
  `SipModel.ParseListener`.
- Boundary marshalling: `AnalysisParser.Listener` calls in `SipModel:311-329`,
  `FileProcessor.Termination` callbacks, `NetworkClient` `loginFailure`/`listReceived`/
  `failed`.
- Remove the ad-hoc `exec(Swing)` wrappers that frames no longer need
  (`NodeMappingListModel:95-118`, `FieldMappingFrame:228-233`, `InputFrame:92-97`,
  `RemoteDataSetFrame:584-602`, `FunctionFrame:213, 239`).
- Fix the direct offenders that a marshalled listener does not cover:
  `MappingCompileModel:529` `setDocument`, `StatsModel:123` from the clear-stats
  work, `TargetFrame:266, 270` null checks, `FieldMappingFrame:117-132`.
- `VisualFeedback.form/ask/confirm`: wrap in `invokeAndWait` when off the EDT, as
  `alert`/`info` already do.
- Check: run with `-Dsun.awt.exception.handler` or a `RepaintManager` EDT checker
  (a `CheckThreadViolationRepaintManager`) in a dev profile; no violations on load,
  map, validate, revert, remote list.

**Step 2 review: Human diff review**
Reference skill: diff-review-before-task-commit
Wait for human acknowledgment before proceeding to commit.

### Step 3: list-model bookkeeping sweep

- Every `fireIntervalAdded/Removed(this, 0, n)` becomes `n - 1`, guarded by `n > 0`.
- Fire removal after the actual removal, or build aside and swap as `WorkItemModel`
  now does.
- Break the `filtered = items` aliasing in `DownloadModel` and `UploadModel`.
- Bounds-check every `getElementAt`; null-check every renderer.
- `NodeMappingListModel:101, 114`: fire with the model as source.
- `NodeMappingEntry.list`: instance, not static.
- `FilterTreeModel.refreshNode`: compute index and path over the same view.
- Check: a unit test per model that registers a `ListDataListener` and asserts the
  index ranges after refresh with 0, 1 and n items. These models are inner classes of
  frames today; pull the ones with logic into package-private top-level classes so
  they can be constructed without a frame.

**Step 3 review: Human diff review**
Reference skill: diff-review-before-task-commit
Wait for human acknowledgment before proceeding to commit.

### Step 4: field ownership and Sentry noise

- `volatile` on the cross-thread flags: `MappingCompileModel.enabled`,
  `MappingSaveTimer.freezeMode/running`, `DataSetModel.currentState`,
  `DataSetModel.StateCheckTimer.running`.
- `MappingModel.getRecDefTreeRoot`: build the tree eagerly in `setRecMapping` on the
  EDT path, or synchronize the lazy init.
- `SourceTreeNode.nodeMappings`: `ConcurrentHashMap.newKeySet()` or copy-on-read.
- Null checks at the check-then-act sites in Inventory C.
- Sentry noise (Inventory E): cancel and expected-absence paths alert without a
  throwable, or not at all; drop the double reporting in `NetworkClient`.
- Check: Sentry shows no `CancelException` and no "Unauthorized" events after a week
  on the new build.

**Step 4 review: Human diff review**
Reference skill: diff-review-before-task-commit
Wait for human acknowledgment before proceeding to commit.

## Out of scope, tracked separately

- Red marking of mappings whose source path disappeared (`inputPathMissing`): works on
  load, but the flag is sticky, re-analysis never re-validates, and selection hides
  the colour. Three-file change in `RecMapping.validateMappings`, `SipModel.analyzeFields`
  and the two renderers. Separate plan when picked up.
- `RecMapping.resolve` silently drops mappings whose output path left the recdef.
- Narthex rebuild against sip-core `eb67b666` so newly uploaded recdefs get a loadable
  generated XSD.
- Bounding the thread pool.

## What's next

Push the ten fix commits, then start Step 1 on a worktree. Flip ADR-0001 to Accepted
when Step 1 merges.

## Commits
- `013ceba0` 2026-09-08 21:27: docs: record EDT ownership decision and sip-app threading hardening plan ( 2 files changed, 341 insertions(+))
