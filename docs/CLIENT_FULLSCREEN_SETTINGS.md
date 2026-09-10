# 0.10.22 — fullscreen and graphics controls

[Download Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.22-fullscreen/Wurm-Server.apk).

The Thor's 0.10.21 report has median 30.0 producer FPS and 29.9 displayed FPS,
with the HUD visible in the attached recording. This release builds on that result.

## Changes

- The game fills the viewer behind a small top-right gear. Fullscreen starts enabled
  for client sessions and hides the Android status/navigation bars. Swipe from an
  edge to reveal them temporarily. The game keeps its aspect ratio.
- Tap the gear to open or collapse launcher controls without reducing game size.
  The panel contains Graphics settings, Fullscreen, opacity, Resume, Retry, Stop,
  report navigation, Restore game UI and diagnostics. Close it to resume game input;
  rendering continues while the panel is open.
- Panel opacity is adjustable from 0–100% and persists. The gear stays visible.
  **Hold the gear to reset opacity to 85%** and open the panel, including after
  making it completely transparent. Android Back also opens/closes the panel.
- Graphics settings offers **1280 × 720** alongside 800 × 480 and 960 × 540.
  This changes the actual game render size on the next client start. Fullscreen
  changes the viewer immediately. The initial resolution remains 800 × 480.
- Seventeen individual controls cover water, reflections, tree/building/creature
  distance, detailed trees, weather particles, sun glare, caves, shadows, shadow
  resolution, model detail distance, bloom, vignette, FXAA and dynamic lighting.
  Each can inherit its base preset or override it. “Use preset values for all
  options” clears the overrides. Performance retains the previously tested eight
  reductions; the additional options inherit the imported profile unless overridden.
- Graphics choices and the 15/30 FPS target can apply during play. Android retains
  the choices across starts without overwriting the imported client profile.
  Settings continue to use the Android menu instead of the desktop JavaFX launcher.

## Test on the Thor

1. Keep the working 0.10.21 app and backups. Install this separate `fullscreen`
   preview and import the same complete client and server runtime ZIPs. Stop older
   local servers before starting this version. Use a new name, for example
   **Thorhd**: a world export does not yet transfer the separate app's client login
   identity, so reusing another preview's character can fail authentication.
2. In Client → Graphics Settings, select **1280 × 720**, keep Performance and the
   30 FPS target initially, then Save and Start Local Game. If already playing,
   use Stop client and Retry Client for a saved resolution change.
3. Confirm the game fills the screen with the gear at top right. Complete any
   character prompt, move, and select a UI item near each edge. Open the gear while
   holding a control: it should release the game input. Navigating the panel should
   not move or click in the world. Close it and check touch/controller input resumes.
4. Adjust opacity, collapse/reopen the panel, and hold the gear to restore visibility.
   Toggle Fullscreen off/on. Open the graphics dialog from both the gear and Wurm's
   own Settings menu. Try a distance or effect override, Save, then clear overrides
   to return to the base preset. Resolution itself requires a client restart.
5. Record briefly, return to the game and confirm the HUD, touch mapping and gear
   still work. Export the Client Report and Server Session Report, plus a screenshot
   or recording. Compare actual `UI_TIMING displayedFps` and `FRAME_TIMING` at 720p.

720p renders 2.4 times as many pixels as 800 × 480, so it may be slower. The frame
rate target is a cap, not a speed guarantee. Native GL errors and visual artifacts
remain under investigation. The prior report also records an EGL cache / Scudo
allocator abort after the window and context closed; clean native shutdown is not
claimed fixed by this release. Imported profiles and new high-quality combinations
have not all been exercised on the device.

## Implementation and validation

The raw frame producer, native EGL size check and Android decoder accept widths
up to 1280 and heights up to the existing 1024 limit. Size, payload, pointer and
sequence checks remain bounded. Wurm still uses a fixed offscreen window; Android
owns fullscreen presentation and scales it with the existing pointer geometry.
System-bar behavior uses the [Android immersive view APIs](https://developer.android.com/develop/ui/views/layout/immersive).

Graphics commands carry a bounded, ordered vector over the existing input queue.
The JVM validates the entire vector and inspected option labels before mutation,
applies it on the game thread and rolls back prior changes if a setter fails.
No proprietary game classes/resources are included in this repository or release.

Regression tests cover real 720p launch dimensions, raw frame edges/metadata,
Android decoding, malformed graphics commands, inherited values, restoration,
late setter failure and Android/JVM wire-order agreement. Existing equal-timestamp
frame tests protect the 0.10.21 speed fix. CI must pass the full Android build,
unit tests, lint, native checks and packaged APK audit before publishing this tag.
Fullscreen, opacity gestures, focus return and 720p speed remain device checks.
