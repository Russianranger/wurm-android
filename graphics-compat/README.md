# Pojav Java API compatibility candidate

0.10.30 corrects GL4ES shader-cache cleanup after the Thor's first actionable
ASan graphics report. Both splash-screen attempts read past the same 32-byte
uniform-map flags allocation during shader relinking. Cleanup now frees all
values before clearing each map, without using location keys as bucket indices.
The host ASan regression reproduces the old failure and passes with the patch.
The 0.10.29 native TLS correction passed the Thor startup test and is retained,
along with the PAC/BTI fixes and graphics instrumentation. See
[evidence and test](../docs/CLIENT_NATIVE_HEAP_TRACE.md).

0.10.24 disables Wurm's occlusion-query capability and option for GL4ES's
placeholder sample results. Distance and frustum culling still apply. See the
[visibility correction and test](../docs/CLIENT_VISIBILITY_FIX.md).

0.10.23 adds the checked VAO buffer-offset correction and an ARM64 OpenAL Soft
1.23.1 build using Android OpenSL ES. The legacy OpenAL context lifecycle is also
corrected. See [evidence and test steps](../docs/CLIENT_AUDIO_BUFFER_FIX.md).


**Current test: 0.10.10 — [server login trace](../docs/CLIENT_LOGIN_TEST.md).** Local authentication passed on Thor; login is waiting. Run both server and client in this preview to capture the missing server evidence.

**0.10.9:** the Thor passed material preload and terrain preparation in 0.10.8,
then rendered its connection screen until the app's timeout. Graphics sources,
trace settings, native pins and the private overlay stay unchanged. The next
work observes real client login/retry state and fixes the premature startup cutoff.
See [the local connection test](../docs/CLIENT_CONNECTION_TEST.md).

**0.10.8:** the Thor confirms GL2.1 capabilities and existing legacy renderer
selection, then exits 134 during builtin material preload. Bounded core GL20
breadcrumbs now identify unfinished calls and source hashes. Android gathers any
available current-app crash details. Native sources and the private overlay are
unchanged. See [the client crash diagnostic](../docs/CLIENT_CRASH_DIAGNOSTIC.md).

**0.10.7:** fix the pinned fork's false-positive capability returns. Wurm now
selects its existing legacy/basic-water path in the host probe. Core/legacy GL
capabilities and real Wurm renderer selection are logged and checked on startup.
Native sources and the five-entry private overlay remain unchanged; see
[the current capability test](../docs/CLIENT_CAPABILITIES_FIX.md).

**0.10.5:** FBO support is physically confirmed on Thor. The next correction is
the imported Java 8 buffer-cleanup ABI, using the existing private overlay and
two client-only module exports. Graphics/native sources are unchanged. See
[the current buffer startup test](../docs/CLIENT_BUFFERS_FIX.md).


**0.10.4:** the Thor created the real Wurm window in 0.10.3. The next correction
qualifies FBO offscreen drawing for one SHA-verified, obsolete engine Pbuffer
check; it does not advertise the unsupported public Pbuffer API. The adapter and
pixel/state tests live in `window/wurm/graphics/OffscreenSupport.java`; the private
runtime overlay generator is `runtime-probe/src/client/ClientGraphicsPatch.java`.
The documented GL4ES `LIBGL_NOPSA=1` option avoids the reproduced saved-shader
failure after FBO use. See [current evidence and test](../docs/CLIENT_OFFSCREEN_FIX.md).


**0.10.0:** the Thor passed the 0.9.1 graphics diagnostic twice. The new window
JAR adapts pinned Pojav Java GLFW to the owned EGL pbuffer and connects Android
controller events to actual LWJGL2 keyboard/mouse queues. Start Client now uses
this source-built window/API ahead of imported desktop bindings. See
[the current device test and limitations](../docs/GRAPHICS_THOR_TEST.md).
`window/` contains authored platform hooks; `scripts/build-window-api.py` adapts
verified upstream copies. Sources/licenses accompany the APK. The descriptions
below are historical; full Wurm rendering and login are still unqualified.


**0.9.1 update:** the candidate is now packaged **only for the JVM Graphics Test**,
along with source-built ARM64 core/OpenGL JNI bindings, GL4ES and an authored EGL
pbuffer bridge. The Thor passed native/context/shader startup; drawing failed at
an aggregate check. The probe now avoids zero-size shader-log requests and checks
each GL operation plus the owned GLES context. See [the executable test and exact Thor steps](../docs/GRAPHICS_THOR_TEST.md).
It is not yet the Wurm window/input backend. The API-audit findings and commands
below record the preceding source-only milestone; its statements about no APK
packaging apply to 0.8.0. The new sources are pinned in `native-sources.json`.

This developer milestone builds the public Pojav LWJGL sources and supplies the
legacy signatures found missing by inspecting the owner's matching client JAR.
**It is not included in the APK and contains no native graphics backend.** The
current Thor failure still requires an Android window/context/runtime bridge.
See [the device findings](../docs/THOR_CLIENT_080_PASS.md).

## Measured coverage

The candidate uses PojavLauncherTeam/lwjgl3 at
[`39272d4d0ca119379024e3ca7207699fd3fce237`](https://github.com/PojavLauncherTeam/lwjgl3/tree/39272d4d0ca119379024e3ca7207699fd3fce237).
This fork already exposes its LWJGL2 compatibility API under `org.lwjgl`; the
current experiment does not need to rename Wurm's references to `org.lwjglx`.
The audit reads constant-pool references and class declarations without executing
the client. It scans 1,286 `com/wurmonline/` classes in client.jar SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
The game's bundled desktop LWJGL providers are deliberately excluded from the
candidate provider set, so they cannot conceal missing replacement methods.

| Candidate | Required classes present | Member signatures matched | Missing members | Unresolved ancestors |
| --- | --- | --- | --- | --- |
| Pinned public source, unmodified | 36 / 38 | 313 / 317 | 4 | 0 |
| Same source with this directory's adapters | 38 / 38 | 317 / 317 | 0 | 0 |

The four missing descriptors belong to the shader program API:

| Owner under `org.lwjgl.opengl` | Missing signature | Adapter |
| --- | --- | --- |
| `ARBProgram` (class also absent) | `glGetProgramiARB(int,int): int` | Delegate to the existing ARBVertexProgram query |
| `ARBShaderObjects` | `glGetActiveUniformARB(int,int,int,IntBuffer): String` | Supply the two size/type output slots to the existing overload without advancing the caller's buffer |
| `ARBVertexShader` | `glGetActiveAttribARB(int,int,int): String` | Allocate temporary size/type outputs and delegate |
| `GL20` | `glGetActiveAttrib(int,int,int): String` | Allocate temporary size/type outputs and delegate |

The other missing class, `SGISGenerateMipmap`, supplies the two constants from the
[Khronos extension specification](https://registry.khronos.org/OpenGL/extensions/SGIS/SGIS_generate_mipmap.txt).
Legacy overloads are documented in the
[LWJGL2 API](https://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/ARBShaderObjects.html).
These are small handwritten adapters to public APIs; no Wurm implementation is
copied into this repository. The source fragments are inserted into temporary
copies of the pinned provider classes, avoiding duplicate definitions in an
overlay JAR. The upstream checkout and imported client remain unchanged.

**Scope:** name/descriptor coverage is not JVM verification, complete linkage,
static/instance or access-rule validation, reflective-call coverage, transitive
dependency coverage, behavioral equivalence, or a native/rendering test. References
from other packages/JARs and native procedures still need qualification. A passing
audit must never be exposed as a successful game launch. The direct shader output
buffer contracts are tested with authored fixtures; no GL context is used there.

## Reproduce on a development host

These commands are for repository development/CI, **not required on the Thor**.
Use Linux, Python 3.10+, Git, curl and a Java 17 JDK. From the repository root,
choose a new temporary directory. The caller supplies their own legally obtained
client.jar only for the private audit; do not upload it to CI or commit its bytes.

```bash
graphics_work=$(mktemp -d)
git clone https://github.com/PojavLauncherTeam/lwjgl3.git "$graphics_work/lwjgl3"
git -C "$graphics_work/lwjgl3" checkout --detach 39272d4d0ca119379024e3ca7207699fd3fce237
curl -fL https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar \
  -o "$graphics_work/jsr305.jar"
python3 scripts/build-lwjgl-api.py \
  --source "$graphics_work/lwjgl3" --annotations "$graphics_work/jsr305.jar" \
  --output "$graphics_work/candidate"
python3 scripts/audit-lwjgl-api.py \
  --client /absolute/private/path/client.jar \
  --adapter "$graphics_work/candidate/pojav-wurm-api.jar" \
  --platform "$graphics_work/candidate/jdk-platform-api.jar" \
  --output "$graphics_work/audit.json"
python3 -m unittest discover -s tests -p test_lwjgl_api.py -v
```

The builder verifies the source commit, tracked source cleanliness and JSR305
SHA-256 `766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7`.
It compiles `core`, `lwjglx`, `opengl`, `opengles`, `openal`, `glfw` and `egl` main
and generated Java sources. Module/package descriptors and `GLFWVulkan.java` are
excluded; Vulkan is not part of this OpenGL/LWJGL2 candidate. Legacy MemoryUtil is
compiled last to avoid ambiguity with the generated system.MemoryUtil imports.
Java 17 with source/target 8 is used because upstream uses `sun.misc.Unsafe`;
this is not a Java 8 runtime qualification. Logs, source arguments and `build.json`
record the build and artifact identity. The candidate has 1,571 provider classes.

Use `--without-wurm-compat` and a different new output directory to reproduce the
baseline; its audit intentionally exits 2. The audit exits 0 only when no scoped
classes/members are missing or unresolved. Each explicitly supplied provider JAR
is hashed, and duplicate definitions fail. Missing parent metadata remains
unresolved instead of being assumed successful. `ExportAuditPlatform.java` exports
four JDK ancestor classes for this static audit only; its generated metadata JAR
must never enter the APK, release or runtime classpath.

The upstream BSD 3-clause notice is retained in the candidate JAR. No upstream
binary or source checkout is committed here. Future packaging must retain all
applicable dependency notices and provide corresponding sources where required,
including any incorporated LGPL Pojav native host code. See the source/license
table in [CLIENT_INTEGRATION.md](../docs/CLIENT_INTEGRATION.md).

## Next executable graphics gate

1. Build matching ARM64 native LWJGL/GLFW libraries from pinned sources. Integrate
   an isolated Android render host that owns both the Surface and the runtime
   bridge; validate ART/OpenJDK attachment and native loading. The working server
   exec backend remains separate. Passing another process's raw ANativeWindow
   pointer to the current client exec child is not a valid surface bridge.
2. Pin/build the [GL4ES](https://github.com/ptitSeb/gl4es) backend and prove an
   actual LWJGL context, clear/swap, resize, surface recreation and clean teardown
   on Thor. The existing Android GLES probe does not establish this.
3. Put the qualified adapter ahead of the imported client, recording provider
   identity. Address desktop display/AWT queries, GL extensions/shaders,
   OpenAL/JInput dependencies and controller delivery into the renderer's actual
   keyboard/mouse queues. Preserve the owner's imported bytes.
4. Reattempt profile/resources, game-thread startup and local login. Only a real
   Wurm connection can establish server acceptance of the local ticket and world
   entry. Measure memory/lifecycle with the local server still running.

The native host and these executable graphics checks are not implemented by this
directory. There is no additional install/import/retry needed for the current
0.8.0 device report; the next device APK must add the native graphics behavior.
