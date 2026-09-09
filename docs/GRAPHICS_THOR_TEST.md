# 0.9.1: native JVM graphics test on the AYN Thor

This APK adds a real **OpenJDK → LWJGL/Pojav native bindings → GL4ES → EGL/GLES**
test. It does not yet launch a Wurm game window or connect a player. The previous
0.8.0 reports passed controller transport, local Java Steam compatibility, resource
validation and TCP reachability, then failed at native LWJGL loading during Profile
setup. This test qualifies the next graphics layer without needing any game import.

## What report (3) established and what this build changes

The 0.9.0 Thor report reached the Adreno 740 EGL context, actual LWJGL native
loading, GL4ES desktop OpenGL 2.1 and successful GLSL 1.20 shader linking. It then
exited deliberately with code 42 at the single aggregate draw/readback error
check. That report contains neither the GL error number nor the failing operation;
it does **not** establish that pixel readback itself failed. No frame was published.

0.9.1 fixes a shader-log request bug reproduced with the same pinned compatibility
sources on host Mesa/llvmpipe: successful/cached shaders can have log length zero,
LWJGL's convenience method forwards zero, and GL4ES rejects that shader-log request
with `GL_INVALID_OPERATION`. The probe now requests at least one byte and bounds
unexpected log sizes. Compilation/link status checks remain mandatory. This is a
confirmed probe bug; the coarse Thor report cannot prove it caused the final error.

Every capability, shader, buffer setup, clear, draw, finish and readback operation
now has `GL_BEGIN` / `GL_OK` markers. An error records the operation, numeric value
and symbolic name, for example `GL_ERROR stage=frame1.drawArrays errors=0x0502(GL_INVALID_OPERATION)`.
Checks inspect GL4ES and the owned underlying GLES context; the latter is resolved
from the GLES library handle to avoid confusing it with the wrapper. Errors remain
failures, including initialization errors. No `LIBGL_NOERROR` or error suppression
is enabled. The app's final status includes the first Java/GL failure reason.

The Pojav fork's `OpenGL30` through `OpenGL33` missing-entry warnings also occur in
the passing host run. Its generated checks emit those messages even when the
corresponding version is absent from the extension set. They do not by themselves
prove GL4ES reported desktop OpenGL 3.x, and are not the attributed cause here.

The instrumented host run reproduced the empty-log error, then the corrected run
verified all three triangle/background frames, both resizes, swaps and teardown
with empty shader logs. Host software rendering is **not Adreno/device acceptance**.
The next Thor report determines whether the corrected probe passes or identifies
another driver/translation failure. Wurm's window, input and local login remain
subsequent gates.

Primary code inspected: [GL4ES shader-log validation](https://github.com/ptitSeb/gl4es/blob/81547d986798e876de8b434193920b606a72363f/src/gl/shader.c),
[LWJGL log convenience overload](https://github.com/PojavLauncherTeam/lwjgl3/blob/39272d4d0ca119379024e3ca7207699fd3fce237/modules/lwjgl/opengl/src/generated/java/org/lwjgl/opengl/GL20C.java),
[Pojav capability checks](https://github.com/PojavLauncherTeam/lwjgl3/blob/39272d4d0ca119379024e3ca7207699fd3fce237/modules/lwjgl/opengl/src/generated/java/org/lwjgl/opengl/GLCapabilities.java).
No upstream source or proprietary game files were changed.

## Exactly what to install, copy and run

1. Keep the working **0.6.0 server**, its world, and the **0.8.0 / 0.9.0** previews installed.
   Download `Wurm-Server.apk` from
   [v0.9.1-graphics-diagnostics](https://github.com/Russianranger/wurm-android/releases/tag/v0.9.1-graphics-diagnostics)
   on the Thor and install it. It is a separate `.graphicsfix1` package. The first
   screen identifies **0.9.1** so you can distinguish the apps.
2. **Copy/import nothing for this test.** Java, the native graphics libraries and
   the small authored shader test are included. No PC, Termux, root, separate JRE,
   desktop native library, client ZIP or new server import is needed.
3. In 0.9.1, open **Client tab → JVM Graphics Test → Run Graphics Test**. Allow
   notifications if Android asks. Let it finish; first use verifies/installs the
   private runtime. The operation has a two-minute limit after child startup.
4. Expected image: an **orange triangle on a dark blue background**. The child
   renders/verifies three frames at **320×180 → 640×360 → 320×180**. The UI scales
   the image to fit, so inspect the logged sizes for resize evidence. Wait for
   **Graphics test passed** or **Graphics test failed**.
5. If it passes, tap **Run Graphics Test** once more. This creates a fresh JVM and
   fresh EGL context. Return to Client, close/reopen the app, and check that the
   last frame/report can still be viewed. An old retained image is not a new pass.
6. In **Client tab → Export Client Report**, export **`wurm-client-report.txt`**
   and return that file. This is the **Client Report**, not Storage Report or
   Server Session Report. Export it even if no image appears or the test fails.
   If Android closes the app, reopen 0.9.1 and export its retained report.

The server does not need to be running for this graphics test. Keeping your
existing server running is acceptable, but this test neither starts it nor logs
in. Stop Test/Stop Client affects only the client-owned process. Once the graphics
test has qualified on the Thor, repeat it with the working server running if that
was not already the case; the next integration must ultimately coexist with it.

## Acceptance markers and limits

| Marker | Establishes |
| --- | --- |
| `NATIVE_VERIFIED`, `ASSET_VERIFIED` | Packaged graphics identities match the manifest |
| `EGL_CONTEXT_READY` | APK-owned OpenJDK process created and made current a GLES2 pbuffer context; logs GPU/version |
| `LWJGL_CONTEXT_READY`, `GL_CAPABILITIES` | Actual native LWJGL calls resolved through GL4ES; desktop GL/GLSL strings and capability flags |
| `GLSL120_PROGRAM_READY` | Authored desktop GLSL 1.20 vertex/fragment shaders compiled and linked |
| Three `FRAME_VERIFY_OK` / `PBUFFER_SWAP_OK` pairs | Triangle/background readback passed at each size and EGL accepted pbuffer swaps |
| `FRAME_DISPLAYED` | Android decoded the child-produced ARGB frame, including its sequence and dimensions |
| `EGL_CONTEXT_CLOSED` | Explicit EGL teardown succeeded |
| `GRAPHICS_PROBE_PASS` then `GRAPHICS_PROBE_EXIT code=0` | Child completed pixel tests and teardown successfully |
| `CHILD_EXIT stage=render code=0 accepted=0` | Foreground owner observed normal exit and the success marker |

The success label requires the child's zero exit and final success marker. It
does not rely on a visible image alone. A previous frame may remain after a failed
later resize; the report retains the failure and does not turn that into success.
Requested Stop, timeout, Java/native-load exceptions, shader logs, pixel mismatch
and available HotSpot crash details are exported. System-level kills may only
provide an exit code and the last persisted startup stage, not a Java stack trace.

**Completed implementation:** Gate 4's native graphics diagnostic and JVM frame
presentation. **Thor native/context/shader initialization has passed; drawing/readback acceptance remains pending.** This is an EGL pbuffer,
not an Android native-window swapchain. It does not qualify GLFW Display creation,
dual-VM attachment, complete Wurm shaders/extensions, OpenAL/JInput, input into game
queues, local ticket acceptance, login, world entry or playable frame rate.

The frame copy is a bounded diagnostic path, not a production renderer: up to
1024×1024 pixels, three frames, atomic app-private replacement, strict decoder and
background reads. No raw window pointers cross processes. The Android UI never
draws a fake triangle in place of a missing JVM result. Existing controller tests
remain diagnostic and separate from this render test.

## Architecture decision and next engineering gate

Reuse the verified managed exec backend for this narrow experiment. Hosting ART
and OpenJDK together requires additional JVM image/library/signal and Surface
ownership work. Testing desktop GL translation in the existing child first isolates
that driver/native risk. This is an intentional smaller step than the earlier
proposed dual-VM host; it does not prove that a pbuffer alone replaces Wurm's window.

The source-pinned Pojav fork supplies Java compatibility and actual core/OpenGL
JNI bindings; GL4ES supplies desktop GL-to-GLES translation. A small authored JNI
bridge owns EGL context/surface lifecycle and connects the documented GL4ES init/
framebuffer-size callbacks. It uses public NDK APIs, without copying an opaque
launcher APK or changing upstream code. Original library names are isolated using
LWJGL's supported name mapper, so the new LWJGL3 natives cannot accidentally satisfy
the imported LWJGL2 `liblwjgl.so` lookup. Only the `render` child gets the candidate
Java classpath and graphics settings. Normal Wurm Start still needs window integration.

After the result: integrate a GLFW/window host suitable for Wurm's `Display`, then
connect the existing controller mapper to its keyboard/mouse queues and reattempt
Profile/game-thread startup. The preferred direct-Surface host remains an option;
real Surface IPC or a bounded frame transport must be evaluated explicitly if the
exec backend is retained. Do not pass process-local ANativeWindow pointers between
processes. Real Wurm login is still the only test of server acceptance of the local
identity/ticket. The server runtime/POC/SQLite work remains the regression baseline.

## Build and source provenance

Use the existing Linux x86_64 SDK/NDK build environment from
[MANAGED_SERVER_TEST.md](MANAGED_SERVER_TEST.md#build-and-verification), Java 17,
Python 3.11+, make and NDK **26.1.10909125**. From the repository root:

```bash
bash ./gradlew --no-daemon :app:assembleManagedPreview :app:testManagedPreviewUnitTest :app:lintManagedPreview
python3 scripts/verify-managed-apk.py app/build/outputs/apk/managedPreview/app-managedPreview.apk
```

Gradle runs `scripts/prepare-client-graphics.py`. The script downloads only the
four public inputs in `graphics-compat/native-sources.json`, verifies archive
hashes, compiles the existing Java candidate with the legacy adapters, compiles
libffi/core/OpenGL/GL4ES/our bridge for ARM64 API33, and checks ELF dependency closure.
The GL4ES source list comes from its Android.mk; flags enable GLES2, NOX11, NO_GBM
and explicit initialization. No GLFW native implementation is built in this gate.
Build logs remain under `app/build/generated/clientGraphics/work/`.

The APK includes library notices, Java assets and graphics hashes. The release
includes `Graphics-corresponding-source.tar.gz` with exact upstream source archives,
our native/Java code, adapter fragments, build scripts, pins and notices. The JDK
ancestor audit JAR and build-only JSR305 dependency do not enter the APK. No Wurm
client/server bytes are committed, downloaded by the build or published.

Local verification: **56 Python tests** and **17 managed Kotlin/JUnit tests**;
all managed Android Kotlin sources and the real ARM64 graphics candidates compile.
CI additionally builds/tests/lints the three Android variants, verifies APK runtime/
graphics identities and signing, and publishes the immutable preview only after
those gates. Device graphics results must come from the Thor report.

## Every changed file in 0.9.1

`managed/` means `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.

| File | Change |
| --- | --- |
| `graphics-compat/probe/wurm/graphics/GraphicsProbe.java` | Positive shader-log buffer size and strict checks after graphics operations |
| `graphics-compat/probe/wurm/graphics/GlChecks.java` | Bounded error collection with stage, code/name and failure retention |
| `graphics-compat/probe/wurm/graphics/NativeEgl.java` | Underlying GLES error-check JNI declaration |
| `graphics-compat/native/egl_probe.c` | Check the owned GLES context directly using the driver handle |
| `managed/ClientSession.kt` | Include the first graphics failure in status; identify 0.9.1 in reports |
| `managed/ClientActivity.kt` | Identify 0.9.1 |
| `managed/GraphicsTestActivity.kt` | Identify 0.9.1 |
| `managed/ManagedActivity.kt` | Identify 0.9.1 on the initial screen |
| `app/build.gradle.kts` | Version 0.9.1/code 13; separate `.graphicsfix1` package |
| `scripts/verify-managed-apk.py` | Require the new checked-GL helper in the APK |
| `tests/test_graphics_errors.py` | Five executable tests of attribution, multiple/unknown errors, startup failure and bounded draining |
| `.github/workflows/android.yml` | Publish immutable `v0.9.1-graphics-diagnostics` after existing gates |
| `README.md` | Current APK and test scope |
| `graphics-compat/README.md` | Current diagnostic implementation and evidence |
| `docs/IMPLEMENTATION_PLAN.md` | Thor findings, targeted correction and remaining graphics gate |
| `docs/CLIENT_INTEGRATION.md` | Current evidence and failure handling |
| `docs/GRAPHICS_THOR_TEST.md` | This diagnosis, source evidence and exact no-import retest |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Matching APK release notes |

## Every changed file in 0.9.0

`managed/` below means `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`.

| File | Change |
| --- | --- |
| `app/build.gradle.kts` | Graphics build/assets, 0.9.0/code 12 and separate `.graphicsprobe` package |
| `app/src/managedPreview/AndroidManifest.xml` | Register owned graphics test Activity |
| `managed/ClientActivity.kt` | Graphics test entry and current version |
| `managed/ClientService.kt` | Accept render operation through existing foreground ownership |
| `managed/ClientSession.kt` | Diagnostic-only runtime/classpath, stop/timeout, result handling, persistent frame and report metadata |
| `managed/ManagedActivity.kt` | Identify the new test APK on its initial screen |
| `managed/GraphicsFrame.kt` | Bounded ARGB decoder |
| `managed/GraphicsRuntime.kt` | Verify and install graphics assets for this child |
| `managed/GraphicsTestActivity.kt` | Display JVM frame and logs; run/stop/report navigation |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/GraphicsFrameTest.kt` | Decode, bounds, truncation and format tests |
| `graphics-compat/native-sources.json` | Exact public source/dependency pins |
| `graphics-compat/native/egl_probe.c` | EGL creation, current context, pbuffer resize/swap and teardown |
| `graphics-compat/probe/wurm/graphics/NativeEgl.java` | JVM JNI contract |
| `graphics-compat/probe/wurm/graphics/GraphicsProbe.java` | Real LWJGL/GLSL render, pixel verification and precise failure stages |
| `graphics-compat/probe/wurm/graphics/FrameFile.java` | Atomic RGBA-to-ARGB frame publication |
| `graphics-compat/probe/wurm/graphics/LibraryNames.java` | Separate new JNI library names from desktop LWJGL2 |
| `scripts/prepare-client-graphics.py` | Verified source build, notices, ELF closure and generated manifest |
| `scripts/build-lwjgl-api.py` | Reuse compilation with caller-verified source archives |
| `scripts/verify-managed-apk.py` | Verify packaged graphics classes/natives/pins and isolation |
| `tests/test_graphics_frames.py` | Executable frame producer and library naming tests |
| `.github/workflows/android.yml` | Package matching graphics sources and publish immutable 0.9.0 |
| `graphics-compat/README.md` | Current packaged diagnostic scope |
| `docs/IMPLEMENTATION_PLAN.md` | Smaller executable graphics gate and subsequent window work |
| `docs/CLIENT_INTEGRATION.md` | New architecture/evidence boundary |
| `docs/GRAPHICS_THOR_TEST.md` | This exact test, source/build guide and file accounting |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | APK release scope and instructions |
| `README.md` | Current release and test link |
