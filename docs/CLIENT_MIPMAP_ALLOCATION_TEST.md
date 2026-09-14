# 0.10.45 — mipmap correction and allocation diagnostics

Work continues on `mod-launcher-test`, following the
[37m39s device review](CLIENT_PERIODIC_GC_REVIEW_20260914.md). That run completed
with normal client/server shutdown and three skipped periodic cleanup requests.
One startup texture-level error and five later allocation-triggered full-GC
stalls of 571–674 ms remain. Client PSS was still rising; the reports do not
establish a leak or show that memory has settled.

## Graphics correction

Inspection of the exact private client shows its precompressed texture loader
drains *earlier* GL errors before uploading each level. The warning at that point
does not prove the following compressed upload failed. No game bytes, assets or
disassembly are included in this repository or release.

The pinned public GL4ES `realize_textures` function has three related defects:

- It attempts automatic mipmap generation on a new/default texture without a
  valid base image, then marks that texture's mipmaps done.
- When a binding already matches, it can issue generation on a different active
  texture unit. Sampler realization selects the unit only afterward.
- It reads the enabled target from the caller's active unit for every loop
  iteration and hardcodes generation to `GL_TEXTURE_2D`.

The downstream patch waits for a valid, nonzero-sized base image, uses each
unit's enabled target and its mapped GLES target, and selects the intended unit
before generation. Deferral does not mark the texture done; a later valid upload
remains eligible. Existing compression, NPOT, automatic-mipmap and no-drawing
exclusions remain. Explicit generation and copy/update delegates retain their
behavior. This is not a general texture rewrite or a global mipmap disable.

All three direct GLES mipmap delegates now expose a fixed thread-local context
during the call. The existing synchronous KHR_debug error callback includes the
site, target, texture number, unit, format, dimensions and validity/compression
flags when available. It reads no pixels or asset paths, requests no GL state
and consumes no GL errors. Outside those calls it reports no stale context.
The existing 64-message bound and optional-driver behavior remain.

The host regression compiles the actual pinned realization function with public
GL4ES structures and an authored GLES boundary. It reproduces empty-image and
wrong-unit calls in the original, then checks deferral, later generation, unit
and target selection, and retained policy exclusions in the patched source.
These are host boundary tests, not a Thor GPU reproduction. The startup warning
is a strong candidate for these defects; its resolution still needs a device
report. The older in-play GL error has not recurred and is not declared fixed.

## Allocation evidence

A client-only daemon samples the JVM's approximate per-thread heap-allocation
counters every 30 seconds. It retains only primitive IDs/counters for at most
256 live threads and reports the four largest contributors. Each window states
its actual elapsed time, matched/new/reset/missing/departed/omitted coverage.
New or reset counters become baselines, not false allocation spikes. Terminated
threads are discarded; thread and game objects are never retained.

The counter delta covers threads observed in both samples, not all process
allocations. Short-lived threads can be missed. These counters identify threads,
not allocating classes/stacks or retained objects. They do not measure direct,
native, GPU or ASan memory. The existing heap/direct/PSS/descriptor observations
remain necessary. See the [JDK 17 allocation-counter contract](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/ThreadMXBean.html).

Counters are enabled only when supported. A missing/disabled/failed facility
logs `UNAVAILABLE` and cannot prevent gameplay or hold up shutdown. The sampler
requests no GC, heap dump, stack walk or allocation event stream. Client GC logs
also include generation occupancy before/after collections. Allocation, GC and
safepoint records join the bounded observation history so console rotation does
not erase the evidence needed to compare allocation pressure with pauses.

The frame publisher already uses a persistent direct image buffer; the inspected
raw publication path creates small header/path/channel wrappers, not a full
heap pixel array per frame. No speculative producer rewrite, heap expansion or
collector change is made. Allocation-triggered stalls are still unresolved.
The periodic-cleanup switch remains optional and defaults off; the server's
collector/heap, persistence, buffer cleanup, input and audio policies remain.

## Device test

1. In 0.10.44, save and stop the client and server, then export a complete backup.
   Keep that working installation. Install the separate `.mipmaptest` package
   and restore the backup. No stock-server reimport is needed.
2. Enable **Skip periodic client cleanup (test)** to match the latest successful
   long run; keep verbose diagnostics off. Start the same world and play for
   approximately 30–40 minutes, including similar movement and area loading.
3. Note visible missing/black textures and any noticeable stalls. Stop the client
   and server normally, then export one complete support bundle.

Review startup KHR messages (with mipmap context if an error recurs), in-play
graphics errors, allocation-thread windows around full GC, old-generation
occupancy after collection, direct/PSS trends, frame gaps, and normal saves/exits.
The repeat serves the new graphics and allocation checks; periodic-skip activation
itself was already qualified. Device results and published-build verification
belong in the maintained handoff after they are available.

## Local validation

Host suite: 190 tests, 20 expected private/public-fixture or platform skips.
The exact private-client overlay tests and public pinned graphics regressions
ran locally. All four changed GL4ES translation units compile; the exported
mipmap accessor has default visibility. The driver callback test also confirms
thread-local isolation and clears the context after the call. All 100 Android
application/test Kotlin sources compile; four focused JVM tests pass, including
history retention and existing collector selection. CI must additionally pass
all three Android variants, lint, packaged source/native checks and publication.
