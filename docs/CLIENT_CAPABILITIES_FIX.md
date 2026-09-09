# 0.10.7: correct graphics capabilities and renderer selection

## What the Thor report establishes

`wurm-client-report(4).txt` is from **0.10.6**. It passes buffer cleanup, FBO
readback and native shader queries. The previous blur compilation failure is
absent; startup reaches the next material after three splash frames.

Both `shader.simple.vertex` and `shader.simple.fragment` fail at GLSL 3.30 `in`
syntax. `material.simple` consequently returns null in `Volume`, called by
`AdvancedWaterRenderer.WaterLod`. This is the cause of the reported Java exception.
The child then reports bootstrap exit 42 but terminates with process status 134;
the report does not establish the separate shutdown-status cause. Keep that value
visible if it recurs after the startup correction.

TCP **127.0.0.1:3724** is reachable. This still does not establish Wurm protocol
login, ticket acceptance or a player in the world. OpenAL still falls back to
silence after a missing native loader symbol.

## Underlying capability bug

The pinned Pojav/LWJGL fork contains an ANGLE workaround in `Checks.java`:

- Both function-table `checkFunctions` overloads compute availability but return
  true even when an address is missing.
- `reportMissing` also returns true. The OpenGL 3.0–3.3 capability expressions
  call it even when that desktop version was not advertised.

As a result, the compatibility API reports **OpenGL33=true** while the actual
backend reports **2.1 gl4es wrapper 1.1.7** and **GLSL 1.20**. Wurm's original
`GLHelper.initialize()` uses that flag to enable its deferred renderer and
instancing. `WorldRender.useAdvancedWater()` follows the deferred flag, leading
to the modern water/material path seen in the report.

[GL4ES documents an OpenGL 2.x translation path and shader limitations](https://github.com/ptitSeb/gl4es).
This release corrects the Java capability contract; it does not claim to add
OpenGL 3.3. The exact inspected source is
[Pojav/LWJGL Checks.java at the pinned revision](https://github.com/PojavLauncherTeam/lwjgl3/blob/39272d4d0ca119379024e3ca7207699fd3fce237/modules/lwjgl/core/src/main/java/org/lwjgl/system/Checks.java).

## Implementation

`scripts/build-lwjgl-api.py` changes only three returns in generated build copies:
two become the computed `available` value and `reportMissing` returns false.
Every function lookup, existing-address check and cache write remains intact.
The source revision and expected method signatures/counts are checked. Pojav and
GL4ES native sources and pins remain unchanged.

A new `CapabilityChecks` helper runs inside the owned graphics context. It checks
both LWJGL3 and legacy LWJGL2 capability views against the qualified GL4ES 2.1
backend. It requires GL2.1 and rejects any GL3+ capability claim. Isolated function
tables also prove missing addresses return false, later available addresses are
still cached, and existing entries do not trigger another lookup. Those synthetic
addresses are never called and never enter the real GL function table.

In the existing verified engine startup hook, the helper checks the real,
already-initialized Wurm `GLHelper`: deferred and instancing must both be false.
Wurm then uses its **existing legacy renderer/basic-water path**. The helper does
not modify those fields, settings, gameplay code or renderer methods.

The prior private overlay remains **exactly five entries**: one engine adapter,
two buffer adapters and the two blur shader resources from 0.10.6. Exploratory
simple/ocean shader conversions were tested locally, then excluded from the
release once the capability bug was found. No new proprietary resource is copied,
committed or bundled. Imports, controller mappings, working server, POC and SQLite
compatibility fixes remain unchanged.

New exportable report markers:

```text
CAPABILITY_LOOKUP_PASS missing=false cached=true laterAddressesPreserved=true
CAPABILITY_CHECK_PASS GL21=true GL30plus=false
WURM_RENDERER_SELECTION deferred=false instancing=false
WURM_RENDERER_SELECTION_PASS path=existing-legacy-renderer/basic-water
```

If the device disagrees, startup stops with the explicit capability or renderer
mismatch. Existing buffer/FBO/shader-query logs and the original error remain
available through **Export Client Report**.

## Verification and limits

- **79 automated tests pass.** The new build-adapter fixture checks exact method
  selection, unchanged surrounding lookup content and rejection of source drift.
- A native Mesa/GL4ES regression verifies both capability views, actual compiled
  function-check behavior, FBO and shader queries, triangle/frame readback,
  keyboard/mouse/wheel delivery, RESET and teardown.
- The rebuilt API still satisfies **all 317 required members** from the supplied
  client. No original bundled LWJGL implementation fills missing adapter members.
- The private real-client probe now calls `GLHelper.initialize()` in the same
  order as game startup. The old API reproduces erroneous GL3.3 renderer selection;
  the corrected API passes the real legacy/basic-water selection and the real
  Volume constructor. The existing actual blur program still loads and draws
  **RGBA 64,128,192,255 (tolerance 1)**.
- CI builds, unit-tests and lints all Android variants, and checks APK signing,
  runtime/native/graphics hashes, required helper classes and exact POC bytes.

Developer probe: `scripts/ProbeClientMaterials.java`, main class
`wurm.graphics.ProbeClientMaterials`. Use the build's runtime helper, private
five-entry overlay, window/graphics/API JARs, original client JAR and local/offline
properties described in [the previous material guide](CLIENT_MATERIALS_FIX.md).
Generate the overlay with `client.ClientBootstrap prepare-graphics`, retain the
two client module exports, set `LIBGL_NOPSA=1`, and use a disposable working/home
directory. Expect `WURM_LEGACY_SELECTION_PASS` alongside the existing blur markers.
No external proprietary packs or full world are simulated in this bounded probe.

**Gate status:** Gate 4 remains in progress. Splash rendering is physically
confirmed; the corrected capability/legacy selection is host-verified and awaits
Thor acceptance. Legacy terrain, additional materials, textures, audio and scene
performance still need testing. GL4ES matrix warnings and desktop-function/proxy
texture warnings are not claimed fixed. Gate 5 still needs actual local login and
world-entry evidence. The preview retains its two-minute diagnostic limit and
bounded frame readback; it is not yet a qualified gameplay build.

## Exact next AYN Thor test

1. Keep **0.6.0 with Adventure** installed. Download
   [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.7-client-capabilities/Wurm-Server.apk)
   from [release 0.10.7](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.7-client-capabilities).
   Install it alongside the server. Confirm **0.10.7-managed-preview**, code **21**,
   package `io.github.russianranger.wurmlauncher.clientcapabilities`.
2. In **0.10.7 → Client → Import Client ZIP**, import your same complete, legally
   obtained client ZIP: `client.jar`, `common.jar`, full `lib/`, full `packs/`
   including `graphics.jar`, `pmk.jar`, `sound.jar`, and remaining original assets.
   This separate preview needs its own import because CI debug signing currently
   changes between releases. No new Wurm files are required.
3. Start **Adventure** in the working server app and wait for its listening game
   port. Return to **0.10.7 → Client → Start Local Game**, **127.0.0.1:3724**.
4. Watch whether startup passes **Preparing terrain**. Capture any new screen;
   try the controls if a login or game interface appears. No repeat triangle test,
   PC, root, Termux commands or manual graphics-setting edits are needed.
5. After failure, timeout or **Stop Client**, choose **Export Client Report**.
   Send **wurm-client-report.txt** and a screenshot. Export **Client Report**,
   not a server Session, Storage or World report.

We need the four new capability/renderer markers and the first subsequent failure
or login screen. Audio may remain silent. A splash or reachable TCP port alone
is not evidence that the player reached the world.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.7 and attach/checksum this guide. |
| `README.md` | Current diagnosis and test link. |
| `app/build.gradle.kts` | Version 0.10.7, code 21, separate clientcapabilities package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Current version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Current report version and confirmed/remaining gates. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Current version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the capability correction. |
| `docs/CLIENT_CAPABILITIES_FIX.md` | Diagnosis, verification, limits, files and exact Thor steps. |
| `docs/CLIENT_INTEGRATION.md` | Corrected capability and renderer architecture. |
| `docs/CLIENT_THOR_TEST.md` | Current test link. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test link. |
| `docs/IMPLEMENTATION_PLAN.md` | Current architecture and gate evidence. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and physical-device steps. |
| `graphics-compat/README.md` | Capability fix and current scope. |
| `graphics-compat/window/wurm/graphics/CapabilityChecks.java` | Native capability/lookup checks and real Wurm renderer diagnostics. |
| `graphics-compat/window/wurm/graphics/OffscreenSupport.java` | Run the new checks before the existing FBO/shader checks. |
| `graphics-compat/window/wurm/graphics/WindowProbe.java` | Check the same capabilities in the window/controller regression. |
| `scripts/ProbeClientMaterials.java` | Match real GLHelper initialization and verify existing legacy/basic-water selection. |
| `scripts/build-lwjgl-api.py` | Restore three capability return values in verified generated source. |
| `scripts/test-window-host.py` | Require capability and lookup markers. |
| `scripts/verify-managed-apk.py` | Require the packaged capability helper. |
| `tests/test_lwjgl_capabilities.py` | Exact adapter scope and source-drift rejection. |
