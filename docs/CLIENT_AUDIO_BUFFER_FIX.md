# 0.10.23 — audio and vertex buffer fix

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.23-audio-buffer/Wurm-Server.apk).

The 0.10.22 Thor report reached approximately 32 minutes at 1280 × 720 with
median 30 displayed FPS. Graphics changes and gameplay worked. The client then
exited 139 during a native vertex-array copy. The server continued running and
later shut down normally when stopped. Audio had fallen back to silent mode.

## What changed

- GL4ES's legacy vertex-array setters and CPU readers now agree on buffer offsets.
  The old normal-array copy added an offset twice. For the reported 64-byte vertex
  stride and a 28-byte normal offset, the last 12-byte copy can cross the buffer's
  end. A guard-page test crashes with the pinned upstream code and passes with
  this patch. It also checks buffer replacement, zero offsets, client pointers,
  generic attributes, skip ranges, and copies into existing memory.
- CPU consumers for colors, texture coordinates, selection and compiled arrays
  resolve the captured buffer's current allocation. GLES offsets stay separate.
  The earlier internal render-list pointer correction remains in place.
- The APK includes a checksum-pinned ARM64 build of OpenAL Soft 1.25.2 with the
  Android OpenSL ES backend. An explicit absolute library path avoids the 32-bit
  `libopenal.so` extracted from the imported client JAR in 0.10.22.
- The LWJGL compatibility layer now retains the context it actually created,
  rather than passing that context to a second device-based creation call. It
  cleans up partial failures and resets state so sound can be recreated.
- Client reports include the chosen audio library, native backend/device logs,
  and `OPENAL_CONTEXT_READY` only after context and capability setup succeeds.
  Backend selection requires OpenSL ES; it cannot silently select OpenAL's null
  output device. Wurm can still fall back to its silent engine if setup fails.

The crash stack identifies `copy_gl_array → copy_gl_pointer_raw →
arrays_to_renderlist → glDrawElements`. Its 12-byte read and 64-byte stride match
the reproduced defect. The saved draw breadcrumb describes the last completed
GLES draw, so it does not prove the exact contents of the failing conversion.
The reproduction supports this targeted fix; extended device testing must still
confirm that this addresses the observed gameplay crash.

## Test on the Thor

1. Keep the working 0.10.22 app and your exports. Install this separate
   `audiobuffer` preview and import the same complete client/server runtime ZIPs.
   Stop the older local server before starting this one. Use an unused player
   name such as **Thoraudio**: client login identity does not yet migrate between
   separate preview apps, even with a world export.
2. Select 1280 × 720 and your preferred graphics settings. Start local play.
   Keep Android media volume audible and check music, ambient sounds and footsteps
   where applicable. The app does not change Wurm's saved sound volumes/toggles.
3. Complete character creation if prompted, move and interact, then play beyond
   the previous roughly 32-minute run. Confirm the gear, opacity, fullscreen and
   graphics settings still work. Check sound again after leaving/reopening the
   viewer, then after stopping and retrying the client.
4. Export Client Report and Server Session Report after the test, especially if
   sound is missing or the client crashes. State which sounds you heard and the
   approximate time played. Keep the server running if you want to export its
   live report; stop it normally before using another preview.

Host regressions verify the graphics overread and audio lifecycle. CI builds all
Android variants, runs unit tests/lint, exercises the pinned-source regressions,
and verifies ARM64 libraries, native dependency closure, APK signature, asset
hashes and the unchanged source-backed server POC. Actual audibility, extended
gameplay stability and the previously observed native shutdown issue remain
device checks. The server runtime, world format, graphics defaults and working
fullscreen controls are unchanged.

## Public source and reproduction

- [GL4ES source pin](https://github.com/ptitSeb/gl4es/tree/81547d986798e876de8b434193920b606a72363f)
- [LWJGL compatibility source pin](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237)
- [OpenAL Soft 1.25.2](https://github.com/kcat/openal-soft/tree/1.25.2)

`graphics-compat/native-sources.json` pins the source archives and hashes.
`scripts/patch-gl4es.py` checks each changed upstream file before applying the
address correction. `scripts/build-lwjgl-api.py` checks and patches the legacy
audio methods. Set `WURM_GL4ES_ARCHIVE` and `WURM_LWJGL_ARCHIVE` to the matching
public archives and run `tests/test_gl4es_array_addresses.py` and
`tests/test_legacy_openal.py` through Python unittest. The release's corresponding
source archive contains all public inputs, patches, audio CMake configuration
and license notices. It contains no imported Wurm game or server JARs.
