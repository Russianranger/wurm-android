# 0.10.4: qualify the real offscreen path

## What the new Thor report establishes

`wurm-client-report (1)(2).txt` is from **0.10.3**. Font/profile/resource startup
passes, followed by `WINDOW_CREATE requested=960x540` and `WINDOW_READY` with
**GL4ES using Adreno 740**. Wurm reaches `firstRender → checkSupportLevels`.

The fatal line is **`MISSING SUPPORT: Pbuffers`**, followed by Wurm's generic
graphics-card error. The previous icon/window/cleanup fixes are physically
confirmed. There are zero submitted game frames and no login/world-entry evidence.

OpenAL also fails to load the imported desktop `libopenal.so`, with a dynamic
loader/JNI error. Wurm catches this and logs **`Failed to start sound engine,
using silent`**, then continues to the graphics check. Audio remains a separate
unimplemented dependency; this release does not claim sound support.

## Why this correction uses FBOs

Inspection of the user's supplied `client.jar` finds one runtime Pbuffer API
reference: `Pbuffer.getCapabilities()` in the engine's startup check. The JAR also
contains a redundant `target/classes/` copy; the loader uses the canonical class.
No client-side Pbuffer construction was found. The real `Offscreen` renderer uses
the real `FBO` implementation or a back-buffer fallback instead.

The pinned Pojav adapter reports no LWJGL Pbuffer support. Merely changing its
global capability bit would falsely advertise constructors and separate/shared
contexts that the app does not implement. The [LWJGL Pbuffer contract](https://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/Pbuffer.html)
includes separate OpenGL state/context behavior. An EGL pbuffer used for the main
Android window does not by itself implement that Java API.

FBOs are the existing client's actual offscreen mechanism. The
[EXT framebuffer specification](https://registry.khronos.org/OpenGL/extensions/EXT/EXT_framebuffer_object.txt)
defines texture/renderbuffer attachments and completeness checks. We qualify those
operations rather than implement an unused desktop context API or replace gameplay.

## Runtime architecture and scope

1. The existing inventory and Steam compatibility stages run first. A new
   **prepare-graphics** JVM stage reads the imported engine class without loading
   or executing it. It accepts only the inspected original engine SHA-256:
   `db689422e7195271d7e395adfe87ab63cb26805ace86add1f936eb71c2fab1e6`.
2. The authored transformer relocates its single constant-pool class reference
   from `org/lwjgl/opengl/Pbuffer` to `wurm/graphics/OffscreenSupport`. Method bytes,
   branches, stack maps and other constants remain identical. The resulting class
   hash is `03d63071b2c4973d6c59d89779dddb678746def72ec7822cbd022e33822e239c`.
3. It writes a one-class overlay JAR inside the existing private session directory.
   Only entry prepends that overlay. Before engine initialization, the bootstrap
   reverses the relocation, requires the exact original hash, and checks the class
   resource selected by the classloader. Unknown engines, tampering, missing output
   and wrong classpath order fail visibly. A failed preparation never starts entry.
4. At the relocated call, the helper requires Wurm's FBO option to be enabled and
   an owned context with `GL_EXT_framebuffer_object`. It allocates a **64x64 RGBA8
   color texture plus depth16 renderbuffer**, checks completeness, clears it and
   verifies an exact magenta pixel through the real GL4ES/GLES path. It restores
   affected state and deletes its resources before returning the legacy test's
   success bit. Errors abort startup with their operation and GL/GLES error codes.
5. The public `Pbuffer.getCapabilities()` remains **0**. Other Wurm capability
   checks remain active. This is a specific, logged adaptation of an obsolete
   requirement in one inspected engine, not a global capability spoof or a new
   Pbuffer implementation.

The raw imported JAR and import hashes remain unchanged. The overlay contains
user-derived code only at runtime, stays in private app storage, and is removed by
the existing session cleanup. It is never committed, packaged in the APK or
exported with reports. No proprietary input is required by public builds/tests.

The existing Pojav Java/LWJGL adapter and GL4ES native backend are reused. No
upstream native source is modified. A host regression exposed two integration
details: the pinned GL4ES rejects a direct nonzero-to-zero renderbuffer bind, so
cleanup first deletes its owned bound object (which legally unbinds it); saved
shader programs can also fail after the FBO check. The latter is resolved with
the upstream [documented `LIBGL_NOPSA=1` setting](https://github.com/ptitSeb/gl4es/blob/81547d986798e876de8b434193920b606a72363f/USAGE.md#libgl_nopsa).
All client graphics stages log this setting and compile shaders per attempt.

The working server process/runtime/import, POC artifact, SQLite SQL fix, local
Steam identity/ticket and controller mappings retain their existing behavior.

## Verification

- **74 automated tests pass.** New authored fixtures execute the redirected call,
  check unrelated dispatch/long/Unicode constants, reverse to byte-identical input,
  reject unknown engines without output, and reject invalid overlays before entry.
- With the actual supplied client, the unmodified support method reproduces
  `MISSING SUPPORT: Pbuffers`. With the private overlay, the **same real method
  passes**, while the public Pbuffer capability still reads zero.
- The actual Wurm **FBO** class allocates and yields the expected green pixel
  both with and without its depth texture. This uses the real client implementation,
  GL4ES and host Mesa; it does not simulate those methods with test stubs.
- The native window regression passes the FBO pixel/state checks, then normal
  triangle/cursor rendering, keyboard/mouse/wheel delivery, reset and teardown.
  It uses restrictive color/depth/scissor state to verify restoration. Shader
  archive reuse reproduced the later drawing error; disabling it passes this test.
- Release CI builds, tests and lints all Android variants and checks APK signing,
  exact POC bytes, runtime/graphics hashes and required authored helper classes.

Optional private developer probe: compile `scripts/ProbeClientOffscreen.java` with
the built runtime helper and graphics JARs, and the user's client. Its main class
is `wurm.graphics.ProbeClientOffscreen`. Use the native/environment settings in
`scripts/test-window-host.py`, including `LIBGL_NOPSA=1`, in a disposable directory.
First run `client.ClientBootstrap prepare-graphics` with the original client on
the classpath and `-Dwurm.client.offscreenOverlay=/absolute/scratch/overlay.jar`.
Then put that overlay ahead of the client, retain the same property, and run the
probe with `client-compat.jar` and the graphics/helper JARs. Set headless=true,
offline=true, host=127.0.0.1, port=3724 and a disposable user.home as in earlier
private probes. Expect `WURM_SUPPORT_LEVELS_PASS`, two `WURM_FBO_PASS` markers and
`WURM_OFFSCREEN_PROBE_PASS`. The probe allocates an uninitialized engine object
only to call its inspected, instance-field-independent support method; it does
not launch jobs, gameplay or networking. No private JAR is an input to CI.

**Gate status:** the host-tested offscreen requirement correction within Gate 4 is
complete. Thor acceptance of this FBO test, full Wurm rendering, audio, local
authentication and world entry remain pending. The reported texture-size proxy
downscaling, actual shaders/terrain, additional native APIs and ticket acceptance
still need physical evidence. This remains a two-minute startup diagnostic with
bounded frame readback, not a playable/performance-qualified release.

## Exact next AYN Thor test

1. Keep the working **0.6.0 server app** installed with Adventure. Download
   [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.4-client-offscreen/Wurm-Server.apk)
   from [release 0.10.4](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.4-client-offscreen)
   and install alongside it. Verify **0.10.4-managed-preview**, code **18**, package
   `io.github.russianranger.wurmlauncher.clientoffscreen`. The separate package
   accommodates current CI debug signing and preserves older app data.
2. Open **0.10.4 → Client → Import Client ZIP** and import the same complete ZIP:
   `client.jar`, `common.jar`, full `lib/`, full `packs/` including `graphics.jar`,
   `pmk.jar` and `sound.jar`, plus the remaining original assets. This new package
   needs its own import. No additional game files, JavaFX, fonts, Java installation,
   root, PC or Termux commands are required.
3. Start Adventure in the working **0.6.0 server app** and wait for its game port.
   Return to **0.10.4 → Client → Start Local Game**. It checks **127.0.0.1:3724** and
   opens the frame viewer. You do not need to repeat the triangle/controller test.
4. Watch for a Wurm screen. If one appears, capture a screenshot and try controls
   briefly. The sample FBO check is not shown as a fake game image. Audio may
   remain silent. A window/open port alone does not prove login or world entry.
5. After failure, timeout or **Stop Client**, return to Client and tap
   **Export Client Report**. Send **wurm-client-report.txt** and any screenshot.
   Use **Client Report**, not server Session, Storage or World reports.

New markers: `OFFSCREEN_PATCH_VERIFIED`, `OFFSCREEN_PATCH_READY`,
`OFFSCREEN_PATCH_ACTIVE`, `SHADER_CACHE disabled`, `OFFSCREEN_FBO_BEGIN`,
`OFFSCREEN_FBO_PASS` and `OFFSCREEN_REQUIREMENT_PASS`. After those, look for game
frames or the next original exception. `CLIENT_GRAPHICS_PATCH_UNSUPPORTED` means
the engine differs from the inspected version; send the report before retrying.

## Every changed file

| File | Change |
| --- | --- |
| `runtime-probe/src/client/ClientGraphicsPatch.java` | Pinned one-reference relocation, private atomic overlay and integrity/classpath verification. |
| `runtime-probe/src/client/ClientBootstrap.java` | Preparation stage and verification before entry. |
| `graphics-compat/window/wurm/graphics/OffscreenSupport.java` | Measured FBO requirement and resource/state cleanup. |
| `graphics-compat/window/wurm/graphics/WindowProbe.java` | FBO state regression and per-draw error attribution. |
| `scripts/ProbeClientOffscreen.java` | Optional real-client support/FBO regression. |
| `tests/test_client_graphics_patch.py` | Executable relocation and rejection fixtures. |
| `scripts/test-window-host.py` | FBO pass requirement and GL4ES shader-cache setting. |
| `scripts/verify-managed-apk.py` | Require new authored graphics/runtime helpers. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Preparation/overlay order, failure gate, shader-cache setting and report status. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Version label. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify offscreen correction. |
| `app/build.gradle.kts` | 0.10.4/code 18/clientoffscreen package. |
| `.github/workflows/android.yml` | Publish release and attach guide/checksum. |
| `docs/CLIENT_OFFSCREEN_FIX.md` | Evidence, architecture, tests, limitations and file inventory. |
| `docs/IMPLEMENTATION_PLAN.md` | Current architecture and remaining gates. |
| `docs/CLIENT_INTEGRATION.md` | Current integration and qualification. |
| `graphics-compat/README.md` | Graphics scope and test pointer. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | APK release notes and device steps. |
| `docs/CLIENT_THOR_TEST.md` | Current test pointer. |
| `docs/GRAPHICS_THOR_TEST.md` | Current test pointer. |
| `README.md` | Current release/test pointer. |
