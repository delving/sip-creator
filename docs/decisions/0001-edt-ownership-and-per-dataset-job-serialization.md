# ADR-0001: Models marshal their notifications to the EDT; WorkModel serializes jobs per dataset

Date: 2026-09-08
Status: Proposed

---

## Why this decision was needed

A Sentry triage on 2026-09-08 fixed seven GUI crashes in one day (commits `8ccc985e`
through `e6d6a8a8`). Five of them were the same shape: a Swing model or a model field
was read on the Event Dispatch Thread (EDT) while a worker thread was replacing it, or
two workers touched the same dataset at once. A follow-up review of the whole `sip-app`
module (three read-only surveys, findings recorded in
`docs/plans/2026-09-08-sip-app-threading-hardening.md`) showed these are not isolated
mistakes. Without a rule, every new frame or model repeats the pattern, and each fix
only covers the one site that happened to throw.

Three gaps produce the races:

1. **Model listeners fire on whatever thread mutated the model.** `MappingModel`,
   `CreateModel`, `MappingCompileModel`, `FunctionCompileModel`, `FactModel`,
   `ReportFileModel`, `AnalysisParser`, `FileProcessor` and `NetworkClient` notify from
   the calling thread. Only `DataSetModel.SwingListener` promises the EDT. Every other
   listener type leaves marshalling to each implementer, so it is applied by habit and
   missed in at least nine places (glass-pane toggles in six frames, a live
   `RSyntaxTextArea` document swap, list-model events from `FieldMappingFrame`, a JTree
   root swap in `StatsModel`).
2. **Per-dataset job serialization does not work.** `WorkModel.dataSetPrefix` only tries
   to merge when `prefix == null` and then compares `contextPrefix.equals(prefix)`, which
   is always false. So `PROCESS`, `SAVE_MAPPING`, `COMPILE_*`, `RELOAD_MAPPING`,
   `REVERT_MAPPING` and `LOAD_REPORT` never queue and always run concurrently, even for
   the same dataset and prefix. `SILENT` jobs bypass merging entirely. The pool is
   unbounded. The "busy" guard in `JobContext.add` is unreachable for `FileProcessor`.
3. **Model state has no ownership rule.** `DataSetModel.dataSet`,
   `MappingModel.recMapping` and `recDefTreeRoot`, all of `CreateModel`, most of
   `MappingCompileModel`, `StatsModel.sourceTree`, `ReportFileModel.reportFile` and
   `SipModel.parser` are plain fields written on workers and read on the EDT. Several
   plain collections are iterated on one thread while mutated on another.

## What we decided

Two rules, enforced in the model layer rather than in every frame:

- **A model that notifies listeners delivers the notification on the EDT.** The
  `fire*`/`notify*` methods in `MappingModel`, `CreateModel`, `MappingCompileModel`,
  `FunctionCompileModel`, `FactModel`, `ReportFileModel` and `SipModel.ParseListener`
  dispatch through `Swing.Exec.later`, which gains an `isEventDispatchThread()`
  shortcut so EDT callers stay synchronous. Listener interfaces that carry this
  guarantee are named `SwingListener`, following the existing `DataSetModel`
  precedent. Frames and panels then touch Swing directly in their listeners and drop
  their ad-hoc `exec(Swing)` wrappers. Callbacks that arrive from non-pool threads
  (`NetworkClient`, `FileProcessor.Termination`, `AnalysisParser`) are marshalled at
  the boundary in the same way.
- **Jobs for the same dataset run one at a time.** `WorkModel` merges every
  `DATA_SET` and `DATA_SET_PREFIX` job into the `JobContext` for that dataset,
  regardless of prefix, and makes the scan-then-add atomic. `SILENT` selection jobs
  (`SELECT_*`, `CREATE_MAPPING`, `REMOVE_NODE_MAPPING`, and the like) route through
  the same per-dataset context. The single background driver owns `doWork`; the Swing
  timer only snapshots for display.

With those two rules in place, model fields written by a job and read by a listener are
handed over by the EDT dispatch and by per-dataset ordering, so they need no
per-field locking. Fields that are still read cross-thread outside a listener
(`MappingCompileModel.enabled`, `MappingSaveTimer.freezeMode`,
`DataSetModel.currentState`) are declared `volatile`.

## What we considered and rejected

- **Keep fixing crash sites one by one as Sentry reports them.** Rejected because the
  fixes of 2026-09-08 each closed one window while the survey found roughly thirty
  more of the same shape. The cost is open-ended and users hit each one first. It
  would, however, have kept every change small and independently reviewable, which
  the plan preserves by staging the work.
- **Wrap every listener body in the frames with `exec(Swing)`.** Rejected because that
  is the current convention and it failed: it depends on every implementer remembering,
  and the model cannot be tested for it. Doing it in the model fixes all implementers
  at once. Its advantage would have been zero change to model code.
- **Add `synchronized` or locks to the model fields.** Rejected because it hides the
  ownership problem rather than solving it, and `VisualFeedback` already uses
  `invokeAndWait` from worker threads, so holding a model lock while calling into
  feedback risks deadlocking the EDT. Locks would have given stronger guarantees for
  the few fields that are legitimately shared, which is why those get `volatile`
  instead.
- **Run all model mutation on the EDT and only IO on workers.** The cleanest Swing
  design, and the direction a rewrite should take. Rejected for now because
  `CreateModel`, `MappingCompileModel` and the Groovy compile path are written as
  `Work` jobs that mutate models mid-flight, and moving them is a rewrite of the
  mapping editor rather than a hardening pass. Per-dataset serialization gets most of
  the benefit for a fraction of the change.
- **Bound the thread pool.** Considered as a mitigation; rejected as the fix because a
  smaller pool only makes the races rarer. It may still be worth doing for memory
  reasons, but that is a separate decision.

## Consequences

- **Easier:** frames become plain Swing code; a listener can call `setBorder`,
  `setModel` or `fireIntervalAdded` without thinking about threads. Two clicks on
  Validate or Open no longer start two processors on the same dataset. New models
  inherit the guarantee by using the shared dispatch helper.
- **Harder:** a worker that mutates a model and then immediately reads a listener's
  effect will no longer see it synchronously. The few places that rely on that
  (`CreateModel.createMapping` showing a dialog mid-job, `RevertMappingMenu`
  reloading right after `setRecMapping`) need to sequence via the job queue instead.
  Per-dataset serialization also means a long `PROCESS` blocks `SAVE_MAPPING` for that
  dataset until it finishes; the save timer must tolerate the delay.
- **Constrained:** any new listener interface in `sip-app` is either a `SwingListener`
  or documents which thread calls it. Any new `Work.Job` picks a `Kind` that routes it
  through a dataset context; `SILENT` is reserved for jobs that touch no shared model.
  The `fireIntervalAdded(this, 0, size)` off-by-one, present in about ten list models,
  is fixed as part of the same plan and treated as a defect wherever it recurs.

## References

- Implementation plan: `docs/plans/2026-09-08-sip-app-threading-hardening.md`
- Prior threading overview: `docs/SWING_APP_ANALYSIS.md` (section on WorkModel and
  the EDT/worker routing diagram)
- Fixes that motivated this ADR, all on `main` dated 2026-09-08: `d7e8d139`
  (stale `CreateModel` target), `8019c3db` (double remove), `660f2e7b`
  (`RemoteDataSetFrame` list drift), `236f56df` (`WorkModel` double driver),
  `e6d6a8a8` (cancel reported as error)
- Sentry issues: 7706901827, 7696467688, 7695359832, 7678612971, 7582237133
