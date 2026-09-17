# 0.10.52 — pipelined GPU readback test

Version 0.10.52/code 66, separate package `.gpupipeline`, branch
`mod-launcher-test`, immutable release tag `v0.10.52-gpu-pipeline`.

Support8 verified the inventory matcher improvement and background publication.
Its comparable 60 FPS spans delivered about 39.3 FPS publication off / 45.3 on,
with activity differences. The on run still spent about 14.2 ms in client work
and 7.8 ms in synchronous readback. The new option targets their serialization;
it is not a measured Adreno improvement or a promise of sustained 60 FPS.

## Change and limits

Diagnostics has **Pipelined GPU readback (test) · next client start**, off by
default. It uses one real GLES3 pixel-pack buffer in the already-owned context.
Support8 reports GLES 3.2 on the Thor despite the existing EGL ES2 request. We do
not change that context request, graphics quality, resolution or GL4ES patches.
If the active driver lacks GLES3 or required functions, startup logs an explicit
synchronous fallback. Allocation/read/map failures stop the client with an error;
they never publish uncertain pixels or silently disable error checks.

Each swap collects and publishes the previous capture, then queues the current
one and flushes submission. Between swaps the game thread can prepare the next
frame while the GPU completes readback. The first swap only queues; resize and
normal close drain the final pending capture before disposing its storage.
There is no unbounded queue, GPU worker, context transfer or dropped capture.
One extra GPU buffer is 3,686,400 bytes (3.52 MiB) at 1280×720, plus possible
driver bookkeeping. The two background-publication buffers remain unchanged.

The pipeline adds **one frame of display latency**. Mapping can still block,
and PBO submission may itself block on a driver. Copying mapped pixels into the
publication buffer adds a CPU copy, so device timing must establish the net
benefit. The existing 14 ms client work has not independently been optimized.

The native path calls the pinned GL4ES RGBA8 read function, retaining its flush
and read-FBO selection/restoration. That path passes a zero PBO offset directly
to GLES. Native pack binding, alignment, row length and skips are saved/restored;
an emulated client PBO conflict is rejected. Mapped storage is copied and unmapped
on the EGL owner before publishing. Pixel bytes, dimensions, sequence, pointer
position/visibility and input acknowledgement are paired with the original
capture, not with the later delivery frame. Existing file format/atomicity,
inventory matcher and text reuse are unchanged.

## Verification

- Native fault harness: exact pixels at small size and 720p; one pending capture,
  bounds, missing capabilities/functions, allocation/read/map/unmap errors,
  emulated PBO conflict, restored pack state, balanced buffer lifetime.
- Real software GLES3/Mesa + pinned, identically patched GL4ES: 40 frames match
  synchronous readback byte for byte even when the framebuffer is changed after
  submission; native pack state is restored. Reproduce with host Mesa EGL/GLES
  development packages and `python3 scripts/test-readback-host.py`. CI runs this
  against the same hash-verified public archive used for Android packaging.
- Window tests: initial delayed delivery, exact pixels and original metadata,
  both publication modes, unsupported-driver fallback, read failures, resize,
  final-frame drain and pack restoration. Existing synchronous tests remain.
- Host checks cannot qualify Adreno timing, visual gameplay or control latency.
  Build/unit/lint/package verification status is recorded in HANDOFF.md.

## Thor procedure

1. Save and stop both runtimes. Export a full backup; keep the working 0.10.51
   installation. Install this separate app and restore the backup. The separate
   package avoids CI debug-signature conflicts with older installations.
2. Keep 1280×720, the same graphics preset, **Background frame delivery on**,
   text reuse on, job profiling on and periodic-cleanup skipping off. First leave
   **Pipelined GPU readback off**. Check normal play at 30 FPS.
3. At 60 FPS, warm up in the chosen location, then start a five-minute memory
   recording. Keep the camera and inventory contents fixed; leave inventory
   open for the recording. Stop the client normally after recording completes.
4. Enable **Pipelined GPU readback** before restarting. Briefly check 30 FPS:
   text, colours, orientation, menus, touch/controller aiming and chat. If the
   display is wrong or controls feel worse, stop and turn it off; export logs.
5. If visually correct, repeat the same five-minute 60 FPS recording/location/
   camera/inventory state. Stop normally and export one support bundle. Report
   whether controls feel more delayed. No forced GC or memory-pressure test.

The toggle applies only on next client start. Keep background delivery on in
both recordings so only GPU readback differs. This is one new controlled test;
there is no need to repeat the old background-delivery-off procedure.

## Log interpretation

`FRAME_READBACK requested=... active=...` and `GPU_READBACK` establish actual mode.
`FRAME_TIMING` retains viewer-independent render/delivery counts and adds:

- `readbackMode`: sync or pipelined.
- `readbackIssueMs`: CPU wall time in readback submission, including driver waits.
- `readbackCollectMs`: delayed map/wait/copy/unmap wall time; zero in sync mode.
- `captureSamples`: submitted captures in the interval.
- `pendingReadbacks`: zero or one; not a growing backlog.

`readbackMs` is total issue + collect wall time divided by captureSamples; in
steady state it includes both stages of the pipeline. `maxReadbackMs` includes
the sum of the two stages for a swap. `samples`/`presentedFps` now count completed
captures handed to publication; the initial pending capture accounts for a
one-frame difference from renderFps. `UI_TIMING` remains the actual display rate.
Compare matched durations and activity; do not add overlapping writerMs to the
serial frame budget. A smaller collect time alone does not prove faster play:
check total work + readback + publication wait and displayed FPS as well.
