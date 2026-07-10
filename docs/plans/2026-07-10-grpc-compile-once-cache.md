# gRPC compile-once mapping cache

**Status:** implemented 2026-07-10. This parks the plan (originally refined 2026-07-07; the
cloud PR from that session never landed, so the fix was implemented fresh) alongside the code.

## Root cause

`MappingServiceImpl.processRecord` (the gRPC `MapRecord` path used by Orchestra's indexer at
`MAP_CONCURRENCY=8`) paid a full cycle **per record**:

1. Re-parse RecDef + RecMapping from the request strings (XStream, recdef ~300 KB).
2. Regenerate the Groovy mapping code via `CodeGenerator`.
3. Construct a new `BulkMappingRunner` (a Groovy compile of the generated script).
4. Log the entire generated script at INFO — ~750 log lines per record
   (observed: 61 calls produced a 1.7 MB / 45,873-line log).

`EngineHolder` additionally resets the whole Groovy engine every 100 compilations, so
per-record compiling also churned the engine (and its internal class cache) every 100 records.
Narthex never had this problem: it compiles once (`CodeGenerator` → `BulkMappingRunner`
with a thread-safe `CompiledScript`) and maps in parallel.

## Fix

A bounded (32-entry) synchronized access-order LRU in `MappingServiceImpl`, keyed by a
SHA-256 over mapping XML + recdef XML + any edit-path content (NUL-separated fields).

- Cache **hit**: reuse the shared `BulkMappingRunner` — safe because its `CompiledScript`
  is immutable and bindings are per-invocation (the established Narthex pattern).
- Cache **miss**: existing parse/generate/compile path, then `put`. One INFO line per
  compile ("Compiled mapping <hash12> (n mappings cached)").
- **Never cached**: file-based requests (files can change on disk) and failed compiles
  (exceptions propagate before `put`).
- The per-record INFO dump of generated code is demoted to a DEBUG line without the code.
- Orchestra needs zero changes — it already sends mapping content per call.

Tests: `MappingServiceImplCacheTest` (sip-app) — identical content compiles once;
an edit path (Mapper preview live edit) busts the cache. Note the counter subtlety:
`EngineHolder.setResetThreshold(0)` disables *counting*, not just resets — the tests
raise the threshold instead.

## Measured (2026-07-10, dev stack, aidsmemorial EDM mapping via grpcurl)

| Scenario | Old jar | Cached jar |
|----------|---------|------------|
| 200 calls, 8-way concurrent | 2.72 s | 1.50 s |
| 30 calls sequential (warm) | 1.05 s | 0.87 s |
| Log volume | ~750 lines/record | 2 lines total, 1 compile |

grpcurl process startup (~25 ms/call) dominates the sequential numbers; the server-side
win is larger than the totals suggest and grows with mapping size and record count.

## Next bottleneck (layering decided 2026-07-07)

A (this cache) → **C: hub3 ingest throughput** (4 hardcoded bulk parser workers, sequential
250-chunks) → B (convert-to-JS / goja strategic track, unhurried).
