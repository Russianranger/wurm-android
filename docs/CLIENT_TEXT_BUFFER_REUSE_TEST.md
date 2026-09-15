# 0.10.47 — bounded text buffer reuse

## Evidence and scope

The completed 0.10.46 recording attributes at least 174.937 MiB (77.688%) of
225.179 MiB completed-job heap allocations to GUI Renderer calls. Worker zero
executes many of those jobs; its name does not identify a scheduler defect.
See [the device review](CLIENT_JOB_MEMORY_FPS_REVIEW_20260915.md).

Inspection of the owner's exact client identifies a concrete repeated source:
SimpleTextFont creates a VertexBuffer for each string, using six vertices per
character and five floats per vertex. The original queue releases it after use.
This allocates buffer wrappers, vertex objects and direct storage repeatedly.
The job recording cannot assign all GUI bytes to this one callee, so the next
device recording measures its actual contribution.

The original client JAR remains private and unchanged. Three hash-pinned overlay
classes redirect only this text factory and its release calls. Two authored
bridges delegate to a bounded helper. Original instruction bodies are retained;
verification reverses the adapters to byte-exact original hashes and reconstructs
the complete patch before startup. Unsupported/altered input fails preparation.
No proprietary class bytes or disassembly are distributed.

## Ownership and limits

- Only the exact text layout is eligible. Sizes must match exactly; each buffer
  is at most 64 KiB. Unused glyph-tail bytes are zeroed before reuse, as in a
  fresh allocation. Original glyph generation, primitive values, draw ordering,
  lock/unlock and dirty GPU uploads execute normally.
- A buffer is reusable only after the original release point and with one
  reference, no active lock, no bound-buffer flag, compatible GPU mode and valid
  direct storage. Shared references or other layouts use original deletion.
  The bound-buffer check is conservative: a fixed-function path may fall back
  even after a global unbind. Counters will expose low reuse on the device.
- At most 256 buffers and 4 MiB of system-storage capacity are registered,
  including active and idle entries. Pool-owned logical GPU payload is at most
  another 4 MiB. These are cache bounds, not process/native-residency bounds:
  driver buffering, metadata, deferred frees and non-pooled allocations are extra.
- Idle entries expire after 30 seconds when the next eligible creation trims
  the pool. Expiry is lazy, not a timer. Eviction uses the original engine delete
  path, including deferred GL-thread deletion. Active entries are never evicted.
  The pool belongs to the client process and does not survive a restart.
- No forced GC, heap dump, collector change, scheduler change or additional
  background thread is introduced. Existing native diagnostics remain enabled.

Diagnostics adds **Reuse text buffers · next client start**, default on for this
experimental package. Off restores the prior overlay set (nine entries, or ten
with job profiling); on adds three text classes (twelve/thirteen). The setting
is disabled during a client run. No server/runtime/native renderer changes are
part of this release. The 15/30/40/50/60 FPS controls remain available.

## Measurements and host validation

`[client-text-buffers]` records time/PID, enabled/initialized, factory calls,
created/reused/released/bypassed/evicted counts, active/idle entries and reserved
capacity. Existing 30-second observations and five-second recording samples
retain these lines. Counters are cumulative since client start. Creation counts
include bypasses; release counts mean successful returns to the pool. Capacity
includes reserved active entries even before their first lock allocates storage.

`engineBufferAccountingBytes` is the client's existing approximate buffer counter,
not JVM-retained memory, native residency or evidence of a leak. It can include
other buffers and briefly differ from sampled runtime counters. Interpret job
heap allocations, heap occupancy after natural collections, direct buffers and
PSS separately. A small bounded cache may intentionally retain more idle storage
while allocating far less over time.

The complete host suite runs 205 tests with 37 expected unavailable fixture or
platform skips and no failures. Nine focused host tests pass, including four checks using the exact private
client. The others use authored ABI/work/driver fixtures available to CI:

- Original queued release, exclusive concurrent ownership, shared references,
  repeated releases, zero-filled reuse, expiry, entry/byte limits and oversize
  fallback.
- Exact SimpleTextFont/Queue/Primitive/Matrix/VertexBuffer code with authored
  glyph-atlas boundaries: 160 varied string draws including empty and non-ASCII
  fallback text produce identical full buffer geometry and advance/count values
  with reuse off/on. Seven buffers remain idle, 4,560 bytes total; 133 reuses.
- Exact VertexBuffer GPU upload/bind and deletion code with an authored GL driver
  boundary: worker writes upload on the GL thread, existing VBO/VAO contents
  change correctly, live resources are retained, expiry defers deletion, and a
  GPU-mode change cannot borrow an incompatible entry.
- Complete optional overlay preparation/selection passes all four reuse/job
  profiling combinations. Every changed class reverses exactly; tampering fails.

A warmed host workload retaining 64 buffers per frame, with 16,000 measured text
buffer draws, allocates 5,376,880 heap bytes without reuse and 72 bytes with it in
one measured run. Over the entire warm-up and measurement it creates 64 pooled
buffers and reuses them 22,336 times, reserving 117,120 bytes. These numbers cover
that buffer workload, not the whole GUI job. Host timing is not a device FPS
prediction. Android/GL4ES visual correctness, actual reuse rate, GC pauses and
long-run memory still require the device check below.

## Device check

1. Save and stop the client and server in 0.10.46, export a complete backup,
   install the separate `.textbuffertest` APK and restore. Keep the working app
   and backup. Existing character/world/client imports can be reused.
2. In Diagnostics, keep **Reuse text buffers** on and enable **job profiling**
   before starting the client. Use 30 FPS and normal logging. Match the previous
   world, route, graphics/mod settings and cleanup policy: support bundle 5 had
   **Skip periodic client cleanup** off.
3. Start the five-minute memory recording during normal play. Move around and
   open chat/inventory; check changing labels, long messages and non-ASCII text
   for missing, stale or corrupted glyphs. Avoid a synthetic memory stress test.
4. After recording completes, stop both runtimes normally and export the support
   bundle. Report any visual defects or pauses. We will compare GUI allocation
   bytes per call, actual cache reuse, direct/PSS behavior and GC-related gaps.

If text is wrong or pauses noticeably worsen, stop the client, turn reuse off,
restart and check the same scene. This same-build fallback removes the new text
adapters. Export both observations if available; no long stress session is needed.
A later longer run can assess the memory floor once this targeted change is
qualified. This release does not claim that the unresolved memory growth is fixed.
