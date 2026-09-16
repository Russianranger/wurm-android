# 0.10.51 — inventory allocation and background frame delivery

Version 0.10.51/code 65, separate package `.inventoryframes`, branch
`mod-launcher-test`, tag `v0.10.51-inventory-frames`.

Support7 identified inventory rendering as 85-88% of measured GUI allocation.
At the 60 FPS setting, delivered FPS averaged 48.4, with about 12.0 ms client
work, 5.7 ms readback and 2.9 ms frame publication. This build addresses the
inventory matcher and overlaps publication with subsequent game work. It does
not claim that device allocations or sustained 60 FPS are already qualified.

## Inventory change

A private, exact-hash overlay replaces one `String.matches` invocation in
`WurmTreeList$TreeListPanel.renderComponent`. The original class SHA-256 is
`6b235f2eb2ce126f95ee4526592e6546d47dd19fcb30a042d731f914c6d359ee`.
Only that three-byte call and appended constant-pool entries change. Reversal
restores the exact original class. Imported files remain unchanged.

The helper reuses one pattern/matcher per rendering worker. It uses the exact
original expression, preserving decimal separator, digit, empty-string and
alignment behavior. Each use clears the matcher input in a finally block; no
item string, inventory object or result cache is retained. An expression change
replaces the single cached matcher. The original expression construction remains;
there is no number parsing, approximate match or change to text geometry.

A warmed host test of 100,000 calls through the actual invocation transform
allocated 89,600,000 bytes originally and 5,600,000 bytes with reuse: **93.75% less
for this check**. This is not a measured percentage reduction for the complete
inventory window. Tests compare all three decimal separators available on the
host, edge cases, 10,000 randomized values, exception types, concurrent workers,
item-string reclamation and exact reversal. The exact private class passes JVM
verification. Existing text-buffer pooling remains unchanged.

## Frame delivery change

**Background frame delivery**, in Diagnostics, defaults on and applies at the
next client start. Off selects the existing synchronous path. Both modes retain
synchronous GPU readback on the game thread and the same RGBA8 V3 frame format.
No rendering quality, resolution, GL state, readback algorithm or frame target
changes. This is CPU/file-publication overlap, not asynchronous GPU readback.

The background mode uses two owned direct pixel buffers and one writer thread.
After readback completes, it hands the buffer and matching pointer metadata to
the writer. Rendering can continue while that frame is published. A busy writer
applies backpressure when both buffers are occupied; the queue never grows and
frames are not discarded. At 1280 x 720 this adds one 3.52 MiB pixel buffer over
the original path. The producer cannot overwrite a buffer until writing finishes.
The worker makes no GPU calls and does not copy the full pixel payload again.

Publication still closes the pending file and atomically renames it. Readers
that opened the previous frame retain its complete inode. Resize drains the old
publisher before replacing it. Close drains submitted frames and releases pixel
references; worker failures propagate to the game thread. Waits have a ten-second
failure deadline rather than hanging indefinitely. Tests exercise blocked writers,
FIFO order, backpressure, errors, owner checks, interrupted close, changed size,
exact pixels/metadata and readers spanning replacement. The real window swap path
also passes its GL pack restoration/error tests in both modes.

### Reading the timing fields

- `publicationMode` records `sync` or `async`.
- `publishMs` is game-thread publication work: full writing in sync mode, enqueue
  time in async mode. A smaller value alone does not prove less total CPU work.
- `publishWaitMs` / `maxPublishWaitMs` record buffer acquisition/backpressure.
- `writerMs` is actual writing time per completed writer frame; `writerSamples`
  is its sample count and `pendingFrames` is the outstanding count (0-2).
- `samples`, `renderFps` and the historical `presentedFps` field describe captures/
  submissions. Async completion can fall in the next reporting bin. Use Android
  `UI_TIMING` displayed FPS for actual delivery and compare enough bins.
- The serial game-thread budget is client work + pacing + acquisition wait +
  capture setup + readback + enqueue/publication + restoration + EGL swap.
  **Do not add async writerMs to that sum:** it overlaps the game thread.

Memory/thread reports will include the extra pixel buffer and one publisher
thread. This is a fixed cost, not evidence of a new leak. GPU readback and other
client work may remain limiting; no thermal explanation is established.

## Thor check

1. Save and stop both runtimes in the working app, export a full backup, install
   this separate app and restore through Backups & migration. Keep the old app.
2. Enable **Reuse text buffers** and **job profiling** before launch. Keep
   **Skip periodic client cleanup off**, normal logging, 1280 x 720 and the same
   preset. Inventory optimization is automatic in both delivery modes.
3. Briefly check 30 FPS: chat text, inventory numeric columns/alignment, scrolling,
   opening/closing bags, changed item values, map, pointer/touch/controller input.
4. For the comparison, start with **Background frame delivery off**, launch at
   **60 FPS**, let the world settle one minute, then record five minutes. Keep
   the same location/camera and inventory contents; use inventory closed for the
   first minute, open for the next three, then closed for the last minute.
5. Stop the client, turn **Background frame delivery on**, restart and repeat
   the same 60 FPS workload after settling. Both runs have the inventory fix;
   their difference isolates the delivery mode more closely than the previous
   30-vs-60 comparison. Note any movement or camera/workload differences.
6. Save, stop normally and export one support bundle. Report numeric alignment,
   stale/torn frames, cursor lag or shutdown trouble. If background delivery is
   worse, turn it off and restart; the inventory optimization remains active.

Next review should compare inventory bytes/call within similar open-window
periods, text reuse/rejections, displayed FPS, readback, game-thread wait,
writer time, GC pauses and memory. A retained-memory leak requires longer evidence
than these short performance comparisons. Build/release results are in HANDOFF.md.
