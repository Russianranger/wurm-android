# 0.10.44 — sound fallback, capability checks and periodic-GC comparison

The user authorized work on the three findings in the
[35-minute 0.10.43 review](CLIENT_FRAME_PERFORMANCE_REVIEW_20260913.md).
This release repairs the verified damaged audio fallback, guards unsupported
vendor GPU-memory queries, adds non-consuming driver error diagnostics, and
provides an opt-in comparison for periodic world-tick GC pauses. Two remaining
GL_INVALID_OPERATION observations still need a new Thor report; they are not
claimed fixed or hidden. Continue on `mod-launcher-test` only.

## Graphics

The inspected WurmClientBase queries `GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX`
(0x9048) and `VBO_FREE_MEMORY_ATI` (0x87fb) without checking their extensions,
then calls glGetError and prints the vertex/index memory limit. This matches the
location of the startup 0x500 observation in the supplied report. GL4ES forwards
these unsupported vendor queries to the GLES driver.

The private, SHA-verified engine overlay now redirects only its glGetInteger
method reference to ClientGlCapabilities. NVIDIA/ATI memory queries first check
the actual corresponding LWJGL capability. If unavailable, the adapter returns
the game's existing zero/unknown-memory result without issuing an invalid query
or inventing a memory budget. Other queries and supported vendor queries still
delegate, including exceptions. All instructions, branches and attributes are
retained; reversing both this reference and the existing offscreen reference
must recover the original engine SHA exactly. No global GL getter is overridden.

The owned native EGL context optionally registers the GLES `GL_KHR_debug`
callback when the extension and required entry points exist. It retains only
API errors/undefined behavior, capped at 64 records and 512 characters per
message. It performs no GL calls in the callback, no additional glGetError, and
no error clearing or retry substitution. Compiler text and routine performance
messages are excluded. Callback records, capability results and existing errors
survive console rotation in runtime observations.

The context remains non-debug. The
[KHR_debug specification](https://registry.khronos.org/OpenGL/extensions/KHR/KHR_debug.txt)
allows implementations to emit no debug messages for a non-debug context, so
installation alone is not proof of complete attribution. Existing GL4ES error
breadcrumbs, exceptions and ASan remain authoritative fallback evidence. No
speculative uniform, texture, depth or draw-state patch is applied to the two
unattributed 0x502 observations. A new device report will determine whether the
driver identifies their actual cause.

## Audio

The supplied client JAR is SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
Its `res/missingsound.ogg` contains UTF-8 replacement-character sequences in
binary headers and fails Ogg CRC checks. Its SHA is
`2d54bb2275eee9604fb564ff447c44eef3eec04f5d3c072da24ac6e6ba2325c9`.
The game's actual OggInputStream reproduces the report's decoding error and
returns rate 0, channels 0 and no PCM. AlSample then forwards that rate to
alBufferData, explaining the invalid-sample-rate warnings.

A valid short Ogg candidate exposed a separate EOF-handling limitation in that
old decoder and was discarded. The implemented fallback uses the game's
existing WAV path instead. The private overlay changes exactly one resource
mapping, from `res/missingsound.ogg` to `res/wurm-android-missing-sound.wav`.
The complete input mappings SHA is
`e6705f4781fce58e0d24c6f6971025ba098b81c9e469bcfc713c15a07b010dd8`;
reversing the replacement must reproduce those exact bytes. Every other mapping
is retained. The new WAV is generated from code: 100 ms, 44,100 Hz, mono,
16-bit zero PCM; 8,864 bytes including the header. No proprietary media is
included in the APK or repository, and imported files remain unchanged.

The actual InternalPack selects the overlay mapping and WAV. The game's actual
WavData decoder produces 8,820 direct-buffer PCM bytes at 44,100 Hz/one channel,
all zero. Missing sounds therefore use intentional silence instead of invalid
audio. This does not recover an absent effect or repair unrelated damaged
assets; normal sound-pack mappings and the OpenAL native engine are retained.

## Periodic GC experiment

World.tick requests System.gc every 14,400 ticks, corresponding to the observed
approximately ten-minute full collections and 0.4–0.5 second viewer gaps. The
only System reference in the pinned World class is that gc method. Its SHA is
`af81f62dc45950de1c053951823754e6b90f05fae81a961ab176966b8589dedf`.
The overlay changes only that owner to ClientWorldGc and verifies an exact
reverse. The tick interval and every method body remain unchanged.

**Diagnostics → Skip periodic client cleanup (test)** defaults off. Off delegates
the original request; on skips only this caller. The preference is locked while
the client runs, applies at next start, and travels in a complete backup.
Timestamped WORLD_GC_POLICY/WORLD_GC_REQUEST observations identify the effective
policy and each actual request. The option is a controlled comparison, not a
claim that all hitches are removed.

The client stays on Serial GC with its existing heap sizes, native instrumentation
and direct-buffer cleanup. Allocation-triggered collection, direct-memory
pressure, other explicit callers and shutdown GC continue. The server remains
unchanged. No DisableExplicitGC flag, G1 switch, manual freeing or heap expansion
is used. Java defines System.gc as a
[best-effort request](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#gc());
skipping this periodic request may change retained memory and later automatic
collection timing. Compare both latency and memory over longer device play.

## Validation and device steps

Host suite: 188 tests run successfully with 20 unavailable fixture/platform skips locally,
using pinned public GL4ES/LWJGL/JDBC fixtures and the private client for its
applicable check. Focused tests execute capability routing, unchanged delegates
and errors, byte-exact reversal, input/output rejection, both GC modes with real
Serial collection and heap pressure, WAV decoding, actual client overlay/resource
selection and original Ogg failure. Native callback tests cover optional support,
exact extension matching, filtering, message bounds and unconsumed errors.
The Android app/test Kotlin sources compile locally. All three Android variants
passed build, unit tests and lint in
[CI run 34777691235](https://github.com/Russianranger/wurm-android/actions/runs/34777691235).
CI ran 188 host tests with 24 expected fixture/platform skips; required subsequent
native/input and packaging checks passed, with one real-host-EGL shader skip.
The independently downloaded APK passed checksum, identity, signature and packaged
runtime verification. Its actual runtime JAR also generated and verified all nine
private overlay entries, with successful InternalPack selection and WavData decode.
Immutable APK/commit identities and comparisons with 0.10.43 are in HANDOFF.md.

Version 0.10.44, code 58, package
`io.github.russianranger.wurmlauncher.sessionfix`, tag `v0.10.44-session-fixes`.

1. Keep 0.10.43. Save and stop both runtimes, export a complete backup, install
   0.10.44 alongside it and restore. No original-server reimport is required.
2. Leave verbose diagnostics and Skip periodic client cleanup **off**. Play as
   usual, exercise movement/actions/sounds, and save/stop normally. Export a
   support bundle to assess the capability/audio changes and driver messages.
3. With the client stopped, turn Skip periodic client cleanup **on**. Repeat
   similar play for at least 20–30 minutes when convenient, retaining graphics
   settings. Export a second support bundle. This crosses two expected periodic
   requests and permits a memory/pause comparison. Ordinary GC pauses may remain.
4. Note any missing audio, visible hitch, graphics issue, or lifecycle/restart
   failure. Keep the original backup. Longer soak and save/restart qualification
   remain useful; no 60–90-minute production-readiness pass is claimed yet.
