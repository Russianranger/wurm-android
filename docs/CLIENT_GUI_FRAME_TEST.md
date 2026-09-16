# 0.10.50 — GUI allocation tracing and frame publication

Version 0.10.50/code 64, separate package `.guiframe`, branch `mod-launcher-test`,
tag `v0.10.50-gui-frame`. The oak theme and qualified text reuse stay in place.

## Why this step

Support bundle `wurm-support(6).zip` already proves successful text reuse. Its
GUI job averages vary sharply: 6.292/36.349 KiB per printed call in the 60/30
runs, with different UI/text workloads. The job-level observations cannot identify
which widget/overlay allocated those bytes. The 60-target run also loses FPS as
reported readback grows, but that time includes completion of pending rendering;
it does not isolate GPU transfer, thermal behavior or scene cost.

This release adds the missing attribution before changing another game allocator.
It also removes proven allocation churn from our frame publisher. Device GUI
allocation reduction, a leak fix and sustained 60 FPS are **not yet demonstrated**.

## Implementation

- With existing job profiling enabled before launch, a private overlay redirects
  six inspected call sites in `Renderer.execute`: top-level `WurmComponent.render`,
  spyglass, two crosshair sites, hover information and onscreen messages. The
  complete original class SHA is pinned to
  `44d7584a7273457ebf13b6a7896eef57ddf05f2bcb25d950b979f48c2b617112`.
  Only three-byte invocations and appended constant-pool entries change. Reverse
  verification restores the exact original; imported JARs remain unchanged.
- GUI measurement runs only during the existing five-minute recording. Original
  method arguments, locks, control flow, returns and exception identity remain.
  Cached method handles avoid reflection argument arrays. Counters retain at most
  128 thread/class-or-overlay pairs, never widget, job or queue instances. Stop,
  timeout and restart share the existing sampler and clear both tables. No new
  sampler, forced GC, heap dump, stress allocation or unbounded cache is added.
- `GUI_SCOPES` totals and all retained `GUI` rows accompany each 30-second job
  window. Row bytes include the component's nested text/widgets; those nested
  methods are not separately hooked or double-counted. Overlay names start with
  `overlay.`. Multiple instances of a class are aggregated per worker. Omitted
  pairs remain counted in totals and explicitly reported. Counters unavailable
  on a call are counted, not treated as zero. Completed scopes exclude the GUI
  renderer remainder; whole-job bytes also include instrumentation overhead.
  Snapshots cross method/job boundaries: compare several windows, not exact
  single-window subtraction. This is heap allocation, not retained or GPU memory.
- One thread-owned `FrameFile.RawWriter` reuses the target/pending paths, a 36-byte
  direct header and a view of the current readback buffer. Resize replaces that
  view; close releases references. Channel close plus atomic rename still gives
  readers a complete V3 frame. No in-place rewriting of the published inode,
  shared-memory race, frame skipping, scaling, format or GL-state change occurs.
- `FRAME_TIMING` retains existing fields and adds `clientWorkMs`, `pacingMs`,
  `captureSetupMs` (including GL error checks), `captureRestoreMs`, `eglSwapMs`,
  `maxClientWorkMs`, `maxReadbackMs`, sample counts and dimensions. Client work is
  wall time between swap return and next entry, including client logic, waits and
  rendering submission; it is not pure GPU render time. Readback still includes
  pending GPU work plus the copy. Samples reset on FPS/resolution changes; the
  first work interval is omitted. No per-frame logging, timing objects, GPU
  query, glFinish or additional render synchronization is introduced. Existing
  five-second reporting/hud-observation time is outside these stage averages.

## Verification

The original client input was recovered and verified as SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
The new renderer overlay reverses byte-for-byte and loads under `-Xverify:all`.
Authored GUI fixtures execute every patched call and check arguments, original
exceptions, allocation counts, cancellation, restart, bounded overflow and weak
receiver/queue lifetime. 100,000 warmed wrapper calls allocate 128 measured heap
bytes while recording and zero while idle in the local host run.

A 2,000-frame host publisher comparison measures 3,104,120 original versus
1,136,000 reused heap bytes (about 63% lower for this publisher operation only).
It verifies exact pixels/metadata, unchanged caller position/limit, resize,
old open-reader lifetime across atomic replacement, failure cleanup and ownership.
The real WindowBackend swap path runs against authored GL/EGL boundaries to check
pack restoration, readback errors, timing stages, target/size reset and unchanged
pixels. These host numbers do not predict device GUI-job allocations or FPS.

Full-suite, Android CI and downloaded-APK results are recorded in HANDOFF.md.
The private client and support bundle are not committed or distributed.

## Device check

1. Save and stop both runtimes in the working app, export a full backup, install
   this separate app and restore through Backups & migration. Keep the old app.
2. Enable **job profiling** and **Reuse text buffers** before client launch.
   Leave **Skip periodic client cleanup off**, normal logging, 1280×720 and the
   same graphics preset as before. Let the world settle for about one minute.
3. At **30 FPS**, record five minutes in one location: minute 1 normal UI,
   minute 2 chat/inventory open, minute 3 close them, minute 4 map open,
   minute 5 return to normal UI. Keep text contents and camera direction stable.
4. Repeat at **60 FPS** with the same location, camera and UI sequence. If you
   restart or pause between runs, mention it. This is a workload-matched check,
   not proof of an FPS-only or thermal cause.
5. Save, stop normally and export one support bundle. Note text/colour/orientation
   glitches, input lag or unusual pauses. If the new GUI hook causes trouble,
   disable job profiling and restart; the original GUI path is then selected.

Next analysis: rank GUI rows by bytes/call and total bytes, compare high/low
windows at the same FPS, preserve successful text reuse, then use frame-stage
budgets to choose the rendering/readback intervention. Do not change heap, GC
policy, pool limits or implement asynchronous GPU readback from the older logs.
