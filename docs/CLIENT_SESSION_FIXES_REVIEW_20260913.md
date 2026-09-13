# 0.10.44 Thor comparison — 2026-09-13

The user reports the first client boot used Skip periodic client cleanup off,
the second used on, and the second felt smoother. The effective policy records
confirm that order. The second run has somewhat higher mean viewer FPS and much
faster frame read/copy measurements. However, it ends before the first periodic
cleanup would be due, and there is no WORLD_GC_REQUEST action=skipped record.
This is useful initial feedback, not yet a measured benefit from skipping GC.

## Evidence and session boundaries

- Support ZIP: 151,216 bytes, SHA-256
  `6d0aee2e44b50399273e5d2595c58f4143f9b37de036c219572ea944360dec0b`.
  Exported together 2026-09-13T20:47:20.053982Z; version 0.10.44,
  `io.github.russianranger.wurmlauncher.sessionfix`, Android 13/API 33.
- The report retains both client sessions. Exact duplicate timestamped records
  in observations and console are counted once. Repeated untimestamped startup
  and shutdown messages belong to their respective sessions.
- Off: client PID 17513, policy false at 20:24:57.158Z; first game-loop
  observation 20:25:05.318610Z, final cleanup at 20:43:50Z, stopped at
  20:43:51.933654Z. Approximately 18m45s of observed play, final sequence 33,201.
- On: client PID 19721, policy true at 20:44:26.459Z; game loop at
  20:44:33.398645Z, final cleanup at 20:47:02Z, stopped at 20:47:04.051458Z.
  Approximately 2m29s of observed play, final sequence 4,355.
- Both attempts pass inventory, compatibility, graphics preparation and entry
  with child exit 0. The same Adventure server, PID 17326, continues running
  through both clients and the export; the second client successfully reconnects.
  This is not a pair of cold server boots or a server save/shutdown/restart test.

## Frame timing and GC

The following uses periodic viewer windows after the first 30 seconds of each
game-loop observation and before final cleanup. This excludes loading windows
with approximately 3.3-second gaps and excludes shutdown GC. Means and medians
describe approximately five-second windows, not global frame percentiles or GPU
scanout. Unequal durations and unconstrained activity prevent a controlled A/B
interpretation.

| Measurement | Cleanup off | Cleanup on |
| --- | --- | --- |
| Included windows | 218 | 24 |
| Mean viewer FPS | 29.720 | 29.892 |
| Lowest window FPS | 25.6 | 29.0 |
| Median window interval p95 | 48.10 ms | 48.11 ms |
| Largest viewer interval | 350.93 ms | 228.12 ms |
| Median window read p95 | 4.795 ms | 1.790 ms |
| Median window copy p95 | 1.955 ms | 0.535 ms |
| Skipped published sequences in these windows | 4 | 0 |

Both attempts reuse one 3,686,400-byte viewer payload allocation. No frame
read/copy/readback failure occurs. For a matched early interval, seconds 30–140
after game-loop observation (22 windows each), mean viewer FPS is 29.482 off
versus 29.891 on. Median window interval p95 is 44.725 versus 47.980 ms, and
largest intervals are 151.31 versus 228.12 ms. Thus individual metrics do not
uniformly favor on even though the user perceived smoother play.

At 20:35:04.255Z the off run logs WORLD_GC_REQUEST action=collect from World.tick.
GC(32) then completes a System.gc full collection at 20:35:04.583Z, taking
326.718 ms. The viewer window ending 20:35:05.482Z records a 350.93 ms gap and
27.8 FPS; the next window returns to 30 FPS. This directly corroborates the
periodic hitch targeted by the option.

The on run stops approximately 7.5 minutes before its first expected request.
It therefore exercises policy selection, ordinary collection and shutdown, but
not the actual periodic skip path on the device. An allocation-triggered young
collection at 20:45:14.765Z takes 199.697 ms, aligning with its 228.12 ms viewer
gap. Shutdown System.gc collections of 500.805 and 418.347 ms also remain, as
intended. These shutdown pauses are not evidence that the periodic switch failed.
The option changes only World.tick's request; it cannot explain a pre-request
difference through skipped collection. Warm caches, activity and device operating
conditions are possible confounders, not established causes.

## Memory and lifecycle limits

During observed play, the off client has 37 samples: PSS 1,195.1–1,476.0 MiB,
heap 401.9–688.2 MiB and direct buffers 147.1–188.0 MiB. The on client has five
samples: PSS rises from 1,420.5 to 1,941.8 MiB; direct buffers rise from 189.3
to 364.3 MiB, with heap 584.8–717.5 MiB. The second run already has a different
allocation/loading profile before any periodic request. This is not proof of a
leak or of memory growth caused by skipping GC; a longer on run is needed to
see whether memory settles and how automatic collection behaves.

Client descriptors remain 53–54 off and 53 on; process thread counts remain 49
and 48 respectively. Android-app PSS after the first 30 seconds ranges
101.4–125.7 MiB off and 108.2–130.1 MiB on. No JVM swap, OOM, ASan fatal report,
fatal signal, abnormal client exit or database-open failure is present.

The server remains alive at export, with PSS approximately 1.44 GiB near the
end, young collections continuing and descriptors cycling back down with GC
(208 to 77 observed). It has the six familiar startup warnings: duplicate eye
template, four duplicate guard-tower templates and the login-server time-sync
notice. No actual server SEVERE/exception appears. Both clients save their
settings/player files before normal exit. No server shutdown or post-restart
world verification is captured, and the storage report says no audit completed.

## Graphics and audio findings

Both attempts select the verified engine, World, mappings and generated WAV
overlay bytes. Both NVIDIA/ATI GPU-memory queries report the capability fallback.
There is no 0x500 record, original Ogg-bitstream decoding failure or invalid
sample-rate warning. This supports the fixes working in these attempts; it does
not prove that every sound or missing-resource fallback was exercised. The
OpenAL-already-stopped message is associated with repeated shutdown cleanup.

The optional KHR_debug callback works on this Thor and supplies a more specific
driver message for the recurring startup 0x502:

| Time UTC | Client PID | Driver message |
| --- | --- | --- |
| 20:25:06.625 | 17513 | unable to generate levels for texture target 3553 |
| 20:44:35.125 | 19721 | unable to generate levels for texture target 3553 |

Each coincides exactly with a driver-origin GL_INVALID_OPERATION observation,
followed by TextureLoader.uploadPreCompressedTexture reporting an invalid
operation while cleaning the error state. This narrows the investigation to
texture level/mipmap generation. It does not yet identify the exact asset,
texture object, format or originating GL call; an error-cleanup location alone
does not prove which upload generated the pending error. Keep the existing
error state/diagnostics intact while tracing this path.

The separate in-play CLIENT_GL_ERROR/WindowBackend exception seen in 0.10.43
does not recur in either attempt. Its absence here is not proof of repair.

## Next qualification

Keep 0.10.44 and leave Skip periodic client cleanup on for a 20–30-minute client
session, preferably with similar movement and graphics settings. Export after
normal client/server shutdown. The next bundle should contain at least two
WORLD_GC_REQUEST action=skipped records so pause timing and memory can be checked
across actual skipped requests. The existing off run supplies one measured
baseline event; it need not be repeated merely to verify policy selection.

Continue tracing the startup texture-level error as the next graphics target.
Do not change the default GC setting or declare the remaining renderer issues
fixed from this short on run. This review updates documentation only; APK,
runtime behavior, version and tag remain unchanged. Private raw logs and game
assets stay outside git and release attachments.
