# 0.10.48 — completed text buffer release fix

## Why this change is needed

The [0.10.47 device review](CLIENT_TEXT_BUFFER_REUSE_REVIEW_20260915.md)
confirmed legible text, clean shutdowns and an enabled pool, but every retained
sample showed zero successful returns and zero reuse. Registered buffers were
being rejected, rather than exhausting the pool's capacity.

Inspection of the exact imported client identifies an incorrect eligibility
condition: `boundBufferObject` records whether the last fixed-function vertex
pointer layout used a VBO. The original `bind(false)` sets it true and global
unbind does not reset it. It does not indicate that another draw still owns the
buffer. Renderer completion waits for jobs, flushes the pipeline and clears the
HUD queue. Original Queue cleanup deletes each owned vertex and clears its
reference; this existing release point supplies the lifetime boundary.

The regression now executes original SimpleTextFont glyph generation, Queue
sort/render/clear and VertexBuffer upload/bind/delete against authored glyph and
GL driver boundaries. Sorting must run even for an unsorted queue to initialize
its indices. Before the fix, all 120 rendered buffers were rejected over twenty
frames. Only undrawn fallback text could be reused (19 hits). After the fix,
there are 120 VBO-layout returns, 133 total reuses, zero rejected returns and zero
evictions. All 160 string calls produce identical geometry and advances with
reuse off/on; all 120 draw submissions match in order, uploaded vertex data,
color and matrix values. This is host execution of the exact private code,
not a native GL4ES raster or Android performance test.

## Implementation and safeguards

The helper no longer rejects the last drawing layout. It does not clear that
flag, alter GL bindings, query GL from workers or change original instruction
bodies. It retains checks for one reference, no active lock, exact vertex count,
compatible GPU mode, and writable direct storage of the expected capacity.
Duplicate returns and a disabled pool still take the engine's original delete
path. Original lock/unlock drives dirty GPU uploads; eviction still uses original
deferred GL-thread deletion. Active entries cannot be borrowed or evicted.

The 0.10.47 limits remain: exact text layout only, at most 64 KiB per buffer,
256 registered active-plus-idle entries and 4 MiB system-storage capacity, with
at most equal logical GPU payload. Driver buffering, metadata and deferred frees
are separate from those capacity bounds. Unused glyph-tail storage is zeroed
before reuse. Idle expiry remains lazy after thirty seconds on the next eligible
creation. Imported client files remain unchanged; the same three hash-pinned
optional overlays and byte-exact reverse verification remain in use.

Existing sampled `[client-text-buffers]` lines now include:

- `vboLayoutReturns`: successful releases whose last layout used a VBO.
- `releaseRejected` and `borrowRejected`: failed eligibility checks at return
  and later idle-entry checkout, respectively.
- `rejectRefs`, `rejectSize`, `rejectLocked`, `rejectGpuMode`, `rejectStorage`,
  `rejectDuplicate`, `rejectDisabled`: mutually exclusive first rejection reasons.
  Their sum equals the two rejection totals. Unregistered ordinary deletions,
  capacity bypasses and idle expiry do not count as eligibility rejections.

These are fixed primitive counters, with no per-buffer diagnostic objects or
per-draw log lines. Counters are cumulative from process start and emitted only
through the existing observations/recording. Engine buffer accounting remains an
approximation, not retained memory or proof of a leak.

The full host suite passes 208 tests with 37 expected unavailable fixture/platform
skips and no failures. Twelve focused tests pass, including the full legacy draw path, shared/locked/
wrong-size/mode/storage/duplicate/disabled rejection, exclusive concurrent
borrowers, capacity limits, zeroing, actual GPU upload/VAO reuse and deferred
eviction. The complete optional overlay passes all four reuse/profiling settings.
The private client JAR stays private; published fixtures contain authored ABI
boundaries, not the client's implementation.

A warmed 16,000-iteration buffer workload measured 5,376,880 heap bytes without
reuse and 72 with it in one host run. A separate workload forces every return
to reject because of a shared reference: 5,376,000 bytes through original
allocation/deletion versus 5,376,880 through the adapter, with 22,400 correctly
classified rejections including warm-up. The fallback does not save allocations;
its fixed counters add no material measured per-call allocation. The full-suite
repeat measured 5,376,880 original versus 5,397,360 adapter bytes (under 0.4%
extra). Host/JIT noise
means these exact figures will vary. These focused measurements cannot explain
all GUI job bytes or predict device GC pauses, FPS or PSS.

## Short device check

1. Save and stop both runtimes in your working app, export a full backup, install
   the separate `.textreleasefix` APK and restore. Keep the working app and backup.
2. Before client launch, explicitly turn **Reuse text buffers** on and enable
   **job profiling** in Diagnostics. A restored preference may have reuse off.
   Use **30 FPS**, normal logging, the same world/route/graphics/mod settings,
   and **Skip periodic client cleanup off**, matching the last recording.
3. Record five minutes during ordinary play. Open chat and inventory and watch
   changing labels, long messages and non-ASCII text for missing/stale glyphs.
   No synthetic memory load, forced GC or heap dump is needed.
4. After recording completes, stop both runtimes normally and export the support
   bundle. We will first confirm growing `reused`, `releases` and
   `vboLayoutReturns`; then compare GUI allocation bytes per call, natural GC
   gaps, direct-buffer capacity and PSS. If rejection remains high, the new
   reason counters will identify the next condition to investigate.

If text degrades or pauses worsen, stop the client, disable reuse and restart
in the same build. Keep that observation with the support export. Do not extend
the run to a long stress test yet. Device reuse and allocation improvement remain
unqualified, and this release does not claim to fix all memory growth or GC stalls.
