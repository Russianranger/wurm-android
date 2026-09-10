# 0.10.24 — nearby object visibility

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.24-visibility/Wurm-Server.apk).

The Thor's 0.10.23 video shows groups of nearby buildings and other objects
disappearing and reappearing during movement. The report already has all three
object-distance settings on Far. Audio is now audible on the device, and the
report confirms OpenAL Soft 1.23.1 using the Android backend.

## Cause and correction

The pinned GL4ES implementation in `src/gl/queries.c` initializes occlusion
queries with zero samples and reports their results as immediately available.
It does not implement the visibility measurement. Wurm nevertheless enables
core occlusion queries because GL1.5 is available. In the inspected client,
`Cell.performOcclusionCheck()` treats a zero result as hidden, except when the
player is inside the cell or less than five metres away. Far distance cannot
override that hidden state. This explains the nearby group disappearance seen
in the recording; device testing must confirm the correction visually.

This release adds an owned startup adapter that marks Wurm's occlusion-query
capability unsupported and disables its corresponding option. Setting only
the option is insufficient: `Cell.wantsOcclusionCulling()` consults the separate
`GLHelper.isOcclusionQueryAvailable()` capability. Both are corrected after
`GLHelper.initialize()` and before the scene loads. The adapter checks the
GL4ES backend and the inspected option ABI, and fails explicitly if they change.

The diagnostic marker is:

```text
WURM_VISIBILITY_POLICY occlusion=disabled reason=GL4ES-placeholder-samples
```

The marker also records previous capability and option values. A profile dump
made before renderer initialization may still show `occlusion_queries_enabled=2`;
the later marker confirms the effective policy. This applies to both imported
profiles and app presets. Distance and camera-frustum culling remain active.
No proprietary game classes are redistributed or changed by this adapter.

More objects may now be drawn, including objects hidden behind other objects.
That can reduce FPS; this is a visibility correction, not a performance promise.
The graphics dialog explains why occlusion culling is disabled. Existing distance
settings, 1280 × 720 fullscreen, gear controls, opacity and audio remain available.

## Test on the Thor

1. Keep the working 0.10.23 app and exports. Install the separate `visibility`
   preview, import the same complete client/server runtime ZIPs, and stop the
   older local server before starting this one. Use an unused player name such
   as **Thorview**: client login identity does not yet migrate between separate
   preview apps, including through a world export.
2. Select the same graphics profile and 1280 × 720 resolution. Leave object
   distances on Far for the comparison. Start local play and revisit buildings,
   trees and other nearby objects like those in the recording.
3. Walk slowly forwards and backwards, then turn in place. Check that nearby
   groups stay visible across the previous disappearance points. Compare FPS
   with the previous build. Also check objects when entering and leaving a building.
4. Confirm sound, movement, graphics settings and the fullscreen controls. Export
   both client and server reports after the session, and provide a short recording
   if objects still disappear. A longer session remains useful for crash testing.

## Verification and remaining limits

The host regression executes the actual adapter against a fixture matching the
inspected public engine ABI. It covers core/extension/unsupported starting states,
the zero-sample near-distance boundary, idempotence, retained distance/frustum
limits, and changed backend/option guards. It models the relevant cell decisions;
it does not substitute for running the complete game on the Thor. The APK audit
requires both the adapter class and its startup call.

CI builds, unit-tests and lints all three Android variants; it also runs the
existing native graphics, audio and input regressions and verifies the packaged
assets, native dependency closure, signature and unchanged server POC. The
release includes corresponding public graphics and runtime source archives.

The 0.10.23 report also contains a startup native allocator abort in
`clear_program` during shader linking, and an EGL cache/allocator abort after a
later user-requested quit. Neither is proven fixed by this visibility change.
Long-session stability remains an open milestone. The server report shows a
normal requested shutdown.

## Relevant source

- [Pinned GL4ES query implementation](https://github.com/ptitSeb/gl4es/blob/81547d986798e876de8b434193920b606a72363f/src/gl/queries.c)
- Owned adapter: `graphics-compat/window/wurm/graphics/WurmVisibility.java`
- Startup hook: `graphics-compat/window/wurm/graphics/CapabilityChecks.java`
- Host regression: `tests/test_wurm_visibility.py`
- Prior audio/buffer correction: [0.10.23 evidence](CLIENT_AUDIO_BUFFER_FIX.md)
