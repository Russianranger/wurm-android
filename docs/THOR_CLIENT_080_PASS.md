# Thor 0.8.0 controller and client startup results

The user identified `wurm-client-report (1).txt` as the controller test and
`wurm-client-report (2).txt` as the failed client attempt. The second export also
contains earlier controller history; it is not an independent second input run.
The reports were inspected privately; only findings and identities are committed.

| Input | SHA-256 |
| --- | --- |
| Controller report (1) | `7ac93be9119002a57b82718704fed3a60d52f38b7d7eb9ea6853587e8977bbc7` |
| Client report (2) | `be858f1878bebfd26668a0dad306bf99f40eae9140eab3bd29b5e73e0358bbee` |

Both came from 0.8.0 `.clientlaunch` on Android 13/API 33, ordinary app UID 10202.
The existing 0.6.0 managed server and its world remain the working server baseline.

## Controller transport passed

The app automatically requested its JVM receiver (`source=controller-test`) and
received `INPUT_READY protocol=1 sink=diagnostic`. The export contains **73 logged
INPUT_RECEIVED samples**: 54 key, 14 mouse-button and 5 mouse-movement lines.
Movement is sampled by the logger, so these are not a count of every input sent.
Samples cover WASD, Space, E/I, Escape, Shift/Ctrl, hotbar 1–4, Tab, both mouse
buttons and right-stick X/Y movement. Held state returns to zero after releases.

Android identifies the controller as Xbox Wireless Controller, device 10,
vendor 8224/product 274, with stick, hat and trigger axes. The GLES probe reports
Qualcomm Adreno 740 and OpenGL ES 3.2, including surface sizes 1896×804 and
1896×766. Those are diagnostic GLES surfaces, not Wurm frames.

`OWNED_CHILD_REAPED code=143 cancelled=true` accompanies the requested Stop.
Interrupted output/InterruptedException in that cancellation sequence is not
evidence of an unexpected game crash. This report does not prove graceful EOF
reset, edited-profile persistence, or gameplay input; the receiver explicitly uses
a diagnostic sink. The controller-preference section is empty and contains no
PROFILE_SAVED evidence. Those limits do not hold up native graphics development.

## Import, local compatibility and listener checks passed

Client generation `ea874198-600b-48e6-b6b6-89e308d04c1b` contains 22 imported entries
and 1,635,657,345 bytes. The client SHA-256 remains
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
LWJGL, OpenAL and JInput Java classes are bundled inside this client JAR; the
short classpath is not evidence that a separate Java dependency is missing.

The actual imported SteamHandler reports `InitSuccess`, and the local shim reports
`STEAM_COMPAT_OK importedHandler=true importedTicket=true bytes=39`, exit 0.
This qualifies Java initialization/ticket construction on the Thor. It does not
prove external Steam authentication or personal-server acceptance of that ticket.

| Resource | Bytes | ZIP entries |
| --- | --- | --- |
| sound.jar | 284,380,956 | 274 |
| pmk.jar | 28,935,680 | 83 |
| graphics.jar | 1,288,725,536 | 10,675 |

The resource JAR checks passed their structure/readability checks, not full
gameplay resource completeness. `TCP_PROBE 127.0.0.1:3724 reachable=true` confirms
that the local listener answered. It is not an authenticated Wurm login.

## Exact startup blocker: native LWJGL before login

The helper reaches `PROFILE_PREPARE player=Thor` and creates the default config.
It then fails while Profile initializes its display options, before PROFILE_READY
or ENTRY_INVOKE:

```text
java.lang.UnsatisfiedLinkError: no lwjgl in java.library.path
  org.lwjgl.Sys -> org.lwjgl.opengl.Display.<clinit>
  DisplayDevice -> DisplayOption -> Options.<clinit>
  Profile.loadSettings/loadConfig/init/getProfile
  client.DirectClientLaunch.launch
```

The independent graphics attempt fails at the same native loading boundary.
`GATE_RESULTS {inventory=0, compat=0, entry=42, graphics=42}` accurately preserves
the failure. Headless AWT is enabled and the headless/JAWT libraries load; the
previous forced X11 AWT error is no longer the first failure.

This is an app graphics integration gap, before any Wurm login packet can be
tested. It does not implicate the server port, the client ZIP or a rejected login.
Copying desktop liblwjgl, renaming an unrelated library, or changing to root/
Termux would not provide the missing Android LWJGL/window/OpenGL implementation.

## Work completed after these reports

The pinned public Pojav Java candidate now compiles. Its scoped audit against the
supplied client initially found four missing member descriptors and two missing
class names. Five small source/fragment files now supply them; the audit covers
all 38 referenced classes and 317 members. Seven authored tests cover the audit's
failure boundaries and wrapper output/delegation contracts. See
[the measured baseline, source pins and reproduction commands](../graphics-compat/README.md).

This is a development-host graphics preparation milestone. The candidate is not
packaged in 0.8.0, and passing symbol coverage does not establish native linkage,
rendering or game input. No new APK is released for this source-only step.

**Next Thor test:** wait for the native render-host build; retain the current app,
owned client ZIP and working 0.6.0 server. Nothing new needs copying, importing or
running for these reports. The next build must test a real JVM LWJGL context and
surface before retrying the Wurm launch. Its release will provide the exact new
install and report procedure. No PC, Termux or root is required for that device test.

## Every changed file in this follow-up

| File | Purpose |
| --- | --- |
| `scripts/audit-lwjgl-api.py` | Read-only constant-pool/provider audit, exact descriptors, ancestry, hashes and explicit failure results |
| `scripts/build-lwjgl-api.py` | Compile pinned public Java sources and optional handwritten adapters in a fresh output directory |
| `scripts/ExportAuditPlatform.java` | Generate local JDK ancestor metadata for the audit only |
| `graphics-compat/src/org/lwjgl/opengl/ARBProgram.java` | Restore the legacy program-query owner |
| `graphics-compat/src/org/lwjgl/opengl/SGISGenerateMipmap.java` | Restore the legacy extension constant owner |
| `graphics-compat/methods/ARBShaderObjects.java.inc` | Adapt the legacy combined size/type output buffer |
| `graphics-compat/methods/ARBVertexShader.java.inc` | Restore the name-only ARB attribute overload |
| `graphics-compat/methods/GL20.java.inc` | Restore the name-only core attribute overload |
| `graphics-compat/README.md` | Reproduction, measured coverage, source/license scope and next native work |
| `tests/test_lwjgl_api.py` | Seven executable tests using authored fixtures |
| `docs/THOR_CLIENT_080_PASS.md` | These physical findings, limits and file accounting |
| `docs/IMPLEMENTATION_PLAN.md` | Advance current priority to native graphics using the qualified Java API candidate |
| `docs/CLIENT_INTEGRATION.md` | Update gate evidence and upstream integration findings |
| `README.md` | Point to current device results and next graphics work |

No Android application/service, manifest, runtime pin, POC, world/SQL handling or
release workflow changes are part of this follow-up. Full Python suite: 48 tests.
