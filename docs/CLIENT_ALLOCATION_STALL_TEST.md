# 0.10.53 — GUI allocation reduction and slow-frame attribution

Version 0.10.53/code 67, separate `.allocationstalls` package, branch
`mod-launcher-test`, immutable release tag `v0.10.53-allocation-stalls`.

## Why this change

The [0.10.52 device review](CLIENT_GPU_READBACK_DEVICE_REVIEW_20260918.md) shows
58.35 displayed FPS and 0.625 ms readback with the pipeline on. Its largest
404 ms gap aligns with 385 ms of allocation-triggered GC. Other 120-133 ms gaps
align with client work without a corresponding collection. The largest pause
precedes measured inventory rendering. Main-thread allocation remains roughly
37-53 MB per 30-second window, with model/sound loading adding other allocation.

The new build reduces one recurring GUI allocation and records individual slow
frames. It does not claim to eliminate GC pauses or all remaining client stalls.
Pipeline, publisher, text-buffer reuse, resolution, rendering quality, heap limits
and collector policy retain their existing behavior/defaults.

## Clip snapshots

The inspected `ScissorControl$ClipRect.clip` creates a new rectangle for every
intersection, even if the resulting bounds are identical. Queued primitives
retain these rectangles after the clipping stack pops, so recycling and mutating
them would corrupt delayed draws. The new helper instead memoizes matching
snapshots and never changes their fields or the clipping stack.

One fixed 256-slot cache holds results for one controller. Changing controllers
clears the cache. A collision replaces a reference; the evicted rectangle remains
valid for any original queue still using it. Hits check the rectangle's actual
owner and four coordinates. Intersection, visibility, GUI origins, scissor
conversion and stack push/pop behavior remain in the original classes. Changing
canvas dimensions still uses the original `doClip` conversion at draw time.
There is no cache of client text, inventory contents, textures or frame pixels.

Only the exact original ClipRect class is wrapped: its original method is kept
as `androidOriginalClip`, with a typed forwarding wrapper. The overlay reverses
byte-for-byte and rejects unsupported/tampered classes. The controller hash is
also checked during preparation. Inspection of all 17 original classes referring
to ClipRect found no field writes outside its constructor; the known client
treats these snapshots as read-only. A mod that mutates clip rectangles or
depends on their identity is outside that qualification; disable this option
if it changes clipping behavior.

Diagnostics adds **Reuse GUI clip snapshots · next client start**, on by default
in this test build. Off removes the new class overlay on the next launch. The
existing recording emits cumulative `CLIP_CACHE` hits/misses and entry counts;
compare counter deltas, not cumulative totals between windows. Cache storage is
bounded to 256 original rectangle objects and the reference table. Existing draw
queues can independently retain evicted snapshots for their normal lifetime.

## Frame observations

Enable job profiling before launch and use the existing five-minute recording.
The window installs a primitive-only observer for that recording; normal play
has no observer. The existing observation daemon checks for long work roughly
every 20 ms and continues memory/summary reports every five seconds. No extra
game worker, forced GC, heap dump, synthetic workload or JFR recording is added.

- `FRAME_WORK`: per-window frame count, main-thread work time, CPU time and Java
  allocated bytes. Missing counters are explicit. Work includes callbacks/input
  polling between swaps; it excludes capture/pacing/publication and periodic
  report formatting. It is broader than the existing GUI/job scopes.
- `FRAME_STALL`: one completed-frame record when client work is at least 50 ms
  or the work-plus-swap cycle is at least 75 ms. UTC start/end timestamps allow
  correlation with the retained GC/safepoint log. Separate elapsed stages cover
  work, pacing, prior-frame collection, setup, readback issue, enqueue/write,
  state restoration and EGL swap. Collection includes buffer wait and prior-frame
  publication; these are not GPU-only timings. At most 32 pending records and
  64 reported stalls are retained per recording, with omitted counts.
- `STALL_STACK`: at most one attempt per long client-work frame, 32 attempts per
  recording, with at most 16 stack entries. Sampling occurs on the observation
  daemon after work exceeds 50 ms. A sample is discarded if the frame/phase has
  changed by the time it returns. This is a sampled execution stack, not an
  allocation stack. STW GC also pauses the sampler, so the GC log remains needed.

Per-frame hooks reuse primitive arrays and do not allocate objects, write files,
format reports or walk stacks. Counter/time queries still add overhead. CPU time
near elapsed work suggests CPU execution; much lower CPU time suggests a pause,
blocking or descheduling, without identifying which one by itself. Profiling and
bounded stack sampling can perturb timing, especially on device. The measurements
do not establish that any sampled method caused all of a long frame.

Partial frames at recording attach, timing/FPS reset, resize or shutdown are
excluded. Cancel/deadline stops collection and detaches the observer; restarting
creates a fresh bounded recording. Periodic formatting is excluded from this
frame timeline, so not every viewer gap must appear as a `FRAME_STALL`. Viewer
`UI_TIMING` remains the displayed-frame metric. Existing recording, GC and new
frame rows are retained in support exports; deduplicate repeated history lines.

## Host verification

- Exact private ClipRect overlay reverses to SHA-256
  `fa0ab689092d527e86188b38a33049f770140d5fdf47d28564df409cd246aac6`
  and executes under JVM verification. Controller SHA-256 is
  `a2ff8b75e313dc5201b6b9f22de0f8067281a1f3fee9b1085da6db616ea37d01`.
  Imported client SHA-256 remains
  `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
- Authored and exact private-class workloads preserve intersection coordinates
  across integer extremes, empty intersections, cache eviction, controller
  changes and delayed draws after canvas-height changes. The warmed repeated
  intersection workload measures 3,200,000 -> 0 Java heap bytes over 100,000
  calls. This is clipping-only savings, not an on-device GUI/GC reduction claim.
- Frame tests distinguish real sleeping versus busy work, attribute a known
  allocation, enforce stack/stall limits, exclude partial frames and stop on
  cancellation. Warmed per-frame hooks measure zero heap allocation.
- Existing synchronous/pipelined window checks preserve exact pixels/input
  metadata, pack state, failure behavior, resize and final-frame drain. They also
  verify stage order and observer removal. All eight clip/text/job overlay
  combinations are checked against the imported client.

No proprietary class bytes or support reports are shipped or committed.

## Thor test

1. Save/stop both runtimes in the working app and export a full launcher backup.
   Install the separate 0.10.53 APK and restore the backup. Keep the working app.
2. In Diagnostics, keep **Pipelined GPU readback**, **Background frame delivery**,
   **Reuse text buffers** and the new **Reuse GUI clip snapshots** on. Enable job
   profiling before starting the client. Leave **Skip periodic client cleanup**
   off, matching the last recording. Use the same 1280 x 720 / 60 FPS settings.
3. Check window borders, nested panels, scrolling lists, chat, inventory and
   tooltips. Move/resize an in-game window and open/close panels; watch for text
   drawing outside a panel, missing edges or misplaced clipping. If that occurs,
   turn clip reuse off and restart, then export support with what happened.
4. After loading settles, start one five-minute memory recording during normal
   play on a similar route. Include some movement and inventory/chat interaction.
   There is no need to repeat the unchanged pipeline off/on comparison.
5. Let recording finish, then stop normally, save/stop the server and export a
   support bundle. Report any visual change or noticeable stutter and roughly
   what you were doing. Longer ordinary play can follow if it remains smooth.

The next review should check clip hit rate, GUI/per-frame allocation, post-GC
memory, displayed intervals and the individual stall/stack timeline. Further
fixes should follow that attribution; do not increase heap limits or suppress
allocation-triggered GC to hide the remaining pauses.
