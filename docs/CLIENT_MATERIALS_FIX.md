# 0.10.6: material startup after the first Wurm splash

## Physical evidence

The Thor's `wurm-client-report(3).txt` is from **0.10.5**. The buffer preflight
and FBO checks pass. `WINDOW_CLOSED frames=3` confirms submitted game frames;
the supplied screenshot shows the actual Wurm Unlimited splash and “Setting up”.
This establishes splash rendering on Android, not a login or playable world.

Startup reaches terrain preparation. Both `shader.gaussBlur.vertex` and
`shader.gaussBlur.fragment` fail at modern `in` / `out` syntax. Consequently,
`material.gaussblur` cannot load, and `WaterTexture` dereferences the null material
while constructing `AdvancedWaterRenderer`. The final NullPointerException is a
consequence of the preceding shader compilation failures.

There is also a real LWJGL wrapper bug: hundreds of uniforms have type **0**.
The OpenAL library still fails and Wurm uses its existing silent fallback.

## Corrections

### Legacy uniform and attribute outputs

The pinned Pojav/LWJGLX `GL20.glGetActiveUniform(program,index,maxLength,sizeType)`
allocates a separate type buffer, then writes that type into the caller's current
size slot. This overwrites size, advances position and leaves the following type
slot empty. Wurm expects size at index 0 and type at index 1. The matching legacy
attribute convenience method has the same defect.

The build adapter corrects both methods in generated build copies of the verified
upstream source. Each delegates to the existing native overload using two buffer
views at adjacent positions, without moving the caller's position or limit. It
rejects insufficient, heap and read-only output buffers. The existing ARB uniform
wrapper already follows this contract and remains unchanged.

A new owned-context check compiles and links an authored GLSL 1.20 program. It
queries actual core/ARB native uniform outputs for a matrix, vector and sampler,
checks nonzero buffer positions and surrounding canaries, and verifies the
Position attribute is bound to location zero. `SHADER_QUERY_PASS` is logged after
the FBO check and before Wurm's material loading. Shader/program objects are
removed afterward. The window/controller diagnostic runs the same check.

### Two verified blur resources

The supplied blur shaders use **GLSL 3.30**, while the current [GL4ES backend](https://github.com/ptitSeb/gl4es)
provides an OpenGL 2.x/GLES2 translation path with limited shader conversion.
The inspected blur needs only a small syntax adaptation:

| Original syntax | Adapted syntax |
| --- | --- |
| `#version 330` | `#version 120` |
| Position input with explicit location 0 | `attribute vec3 Position` |
| Declared fragment output `diffuseOut` | Macro mapping to `gl_FragColor` |
| Seven `texture(...)` calls | Seven `texture2D(...)` calls |

The real Wurm program already binds Position to location zero by name. Blur
weights, calculations, coordinate logic and uniform names are unchanged. This is
an adaptation of two inspected resources, not a general GLSL 3.30 translator or
a claim that desktop OpenGL 3 is implemented.

| Resource under `com/wurmonline/client/resources/programs/` | Original SHA-256 | Adapted SHA-256 |
| --- | --- | --- |
| `gaussBlur.vertex.shader` | `b0506b81c61958bf599068766a139f5a5fafe7f8536c268c53a492d6cee33bda` | `233041091b893b690829a378123cdac1abd371bddbf397571dbea7d2cc174ecd` |
| `gaussBlur.fragment.shader` | `2ab15620aef10c2fb6e778ec1265e0df00a40508c6fdda7d763a43fb8fd69b9b` | `0c70bf5413043e6dd87bf64c5858d9bdb9e088006e74604f1d6512a5bf964e19` |

The existing `prepare-graphics` JVM generates the resources inside the private
session overlay alongside the three previously verified engine/buffer classes.
Every substitution requires its exact expected count. Original hashes are pinned;
reverse conversion must recover identical original bytes, including line endings.
Entry verifies all five items and their classloader-selected content. Unknown or
tampered resources fail before game startup. The raw import remains unchanged.

The overlay is never committed, bundled in an APK or exported in reports. It is
removed by existing session cleanup. Public builds/tests use authored fixtures,
not proprietary classes, shaders or packs. The working server, POC/SQLite fix,
native graphics sources, module exports, Steam shim and controller profile remain
unchanged. Pojav and GL4ES are reused; no new renderer or native upstream patch is
introduced.

## Verification

- **78 automated tests pass.** New fixtures verify both core output slots at zero
  and nonzero positions, unchanged limits, invalid buffers, exact resource rules,
  byte-identical reversal and rejection of unknown/tampered input.
- The rebuilt Java graphics API still satisfies **all 317 required members** in
  the supplied client, without falling back to its bundled LWJGL implementation.
- The native Mesa/GL4ES window regression passes shader reflection, FBO readback,
  triangle rendering, cursor, keyboard/mouse/wheel delivery, reset and teardown.
- A private probe uses the real Wurm resource manager, builtin materials and blur
  program. The original shaders reproduce compilation/material failure. The
  adapted resources load/link, retain Position=0 and all four blur uniforms, and
  draw a constant input to **RGBA 64,128,192,255 (tolerance 1)** through the actual
  imported shader calculations. No replacement material or fake renderer is used.
- CI builds/tests/lints all Android variants and verifies signing, runtime/native/
  graphics hashes, required helpers and exact source-backed POC bytes.

Optional private developer probe: `scripts/ProbeClientMaterials.java`, main class
`wurm.graphics.ProbeClientMaterials`, compiled against the built runtime helper
and graphics JARs. Generate the private overlay with
`client.ClientBootstrap prepare-graphics`, then run with that overlay ahead of
the supplied client, the same `wurm.client.offscreenOverlay` property, existing
client module exports and local/offline/headless properties. Use host environment
and library mapping from `scripts/test-window-host.py`, including `LIBGL_NOPSA=1`,
and a disposable working/user-home directory. Only builtin resources are selected
for this probe; proprietary external packs are not simulated. Expect
`WURM_GAUSS_MATERIAL_PASS`, `WURM_GAUSS_DRAW_PASS`, `WURM_MATERIAL_PROBE_PASS`.

**Gate status:** Gate 4 now includes physically confirmed Wurm splash rendering.
These material corrections are host-tested and await Thor acceptance. Full terrain
rendering, additional shaders, audio, local ticket/login acceptance and world entry
remain unqualified. Wurm still reports GL4ES-internal mat3 uniforms as unsupported;
those warnings remain visible and are not claimed fixed. Desktop-only function
warnings and texture-size proxy downscaling also need later scene-level evidence.
The preview retains its two-minute startup limit and bounded frame readback.

## Exact next AYN Thor test

1. Keep the working **0.6.0 server app** with Adventure installed. Download
   [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.6-client-materials/Wurm-Server.apk)
   from [release 0.10.6](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.6-client-materials)
   and install alongside it. Confirm **0.10.6-managed-preview**, code **20**,
   package `io.github.russianranger.wurmlauncher.clientmaterials`.
2. In **0.10.6 → Client → Import Client ZIP**, import the same complete ZIP:
   `client.jar`, `common.jar`, full `lib/`, full `packs/` including `graphics.jar`,
   `pmk.jar` and `sound.jar`, and remaining original assets. This separate preview
   package needs its own import because CI debug signing currently changes.
3. Start **Adventure** in the server app and wait for its listening game port.
   Return to **0.10.6 → Client → Start Local Game**, targeting **127.0.0.1:3724**.
4. Watch whether startup gets beyond “Preparing terrain”. Capture any new screen
   and try controls if the client progresses. You do not need to repeat the
   triangle/controller test. No PC, root, Termux commands or new game files are
   required. Audio may remain silent.
5. After failure, timeout or **Stop Client**, choose **Export Client Report**.
   Send **wurm-client-report.txt** and a screenshot. Use **Client Report**, rather
   than the server Session, Storage or World reports.

New report markers are `SHADER_RESOURCE_PREPARED`, `SHADER_RESOURCES_READY`,
`SHADER_RESOURCE_ACTIVE` and `SHADER_QUERY_PASS`. Buffer/FBO markers should still
pass. We need the first subsequent shader/material error or evidence of progress
past terrain preparation. A splash screen or reachable port alone does not prove
successful login/world entry.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish 0.10.6 and attach the new guide/checksum. |
| `README.md` | Point to the current test and confirmed splash rendering. |
| `app/build.gradle.kts` | 0.10.6, code 20, separate clientmaterials package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Version and physically confirmed gate status in reports. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the material startup fix. |
| `docs/CLIENT_INTEGRATION.md` | Current material integration and remaining gates. |
| `docs/CLIENT_MATERIALS_FIX.md` | Diagnosis, hashes, tests, limits and file inventory. |
| `docs/CLIENT_THOR_TEST.md` | Current test pointer. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test pointer. |
| `docs/IMPLEMENTATION_PLAN.md` | Confirmed splash rendering and material architecture. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and device steps. |
| `graphics-compat/README.md` | Current gate status and scope. |
| `graphics-compat/window/wurm/graphics/OffscreenSupport.java` | Run native shader-query validation before material loading. |
| `graphics-compat/window/wurm/graphics/ShaderQueries.java` | Compile/link and verify native core/ARB size/type/attribute outputs. |
| `graphics-compat/window/wurm/graphics/WindowProbe.java` | Use the same shader-query validation in the window regression. |
| `runtime-probe/src/client/ClientGraphicsPatch.java` | Generate and verify two resources alongside the three overlay classes. |
| `runtime-probe/src/client/ClientShaderResources.java` | Pinned, reversible two-resource syntax conversion. |
| `scripts/ProbeClientMaterials.java` | Optional real-client builtin material, blur link and pixel probe. |
| `scripts/build-lwjgl-api.py` | Correct pinned GL20 legacy output slots in generated build copies. |
| `scripts/test-window-host.py` | Require shader reflection success in the native regression. |
| `scripts/verify-managed-apk.py` | Require new authored runtime and graphics helpers. |
| `tests/test_client_shader_resources.py` | Exact conversion, reversal and hash rejection fixtures. |
| `tests/test_lwjgl_api.py` | Core output-slot, position and invalid-buffer regression. |
