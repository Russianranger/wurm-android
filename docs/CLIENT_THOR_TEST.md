# AYN Thor: client test history

**Current test: 0.10.10 — [server login trace](CLIENT_LOGIN_TEST.md).** Local authentication passed on Thor; login is waiting. Run both server and client in this preview to capture the missing server evidence.

**Previous 0.10.9 milestone:** the Thor passed material/GUI/terrain setup and reached Connecting with at least 375 frames. Its final exit followed the two-minute app timeout. That milestone exports real authentication/login/retry messages and allows five minutes for startup. Follow the [local connection test](CLIENT_CONNECTION_TEST.md). Earlier instructions below are historical.

Follow [GRAPHICS_THOR_TEST.md](GRAPHICS_THOR_TEST.md) for the new APK, window/input
check, exact complete client ZIP contents, local launch and Client Report exports.
The client window integration is experimental; no PC, root or Termux is needed.

The 0.8.0 receiver/import instructions below are retained as historical evidence.

# AYN Thor: 0.8.0 client launch and automatic controller test

No PC, root, Termux command or separate Java installation is needed. This is a
startup/compatibility preview, not yet a playable Wurm client.

## Install

1. Download **Wurm-Server.apk** from
   [v0.8.0-client-launch](https://github.com/Russianranger/wurm-android/releases/tag/v0.8.0-client-launch)
   on the Thor and install it. Android App info must show **0.8.0-managed-preview**,
   package `io.github.russianranger.wurmlauncher.clientlaunch`. The Client tab
   heading shows **Wurm Client · 0.8.0**.
2. Keep your working **0.6.0** server app and Adventure data installed. Keep 0.7.0
   too if you want its old report/import. CI debug keys are not stable, so this
   release uses a new package and installs alongside them; it is not an in-place
   upgrade. Do not uninstall the working server to install this APK.

## Test the missing receiver control first — no import required

1. Open the **new 0.8.0 app → Client tab → Start Controller Test**.
   There is no separate Start JVM Receiver step. The test requests startup itself.
2. Wait for **JVM receiver: READY**. Runtime installation/checks may take a moment.
   If a client operation is already active, wait for it to finish or stop it,
   then tap **Retry Receiver** in the test's top row. Swipe that row horizontally
   if large fonts hide a control. This never replaces a running Wurm client.
3. Move the left stick up/right (W/D), move the right stick, press A, B, X, Y,
   LB/RB, LT/RT, D-pad, Start and Select. Default A/RT are left-click and LT is
   right-click. The cursor/background are Android diagnostics, not Wurm frames.
4. Switch apps and return with sticks neutral; then tap **Stop Receiver** and
   **Exit Test** using touch. Enter Controller Settings, change one mapping or
   sensitivity, tap Save mappings, reopen the app and confirm it persisted.
5. On the Client tab tap **Export Client Report**. Save and send
   **wurm-client-report.txt**. This is the Client Report, not the server Session,
   Storage or World report. For this run it must contain `INPUT_READY` and
   `INPUT_RECEIVED`; `TRANSLATE` or `queued=true` alone is insufficient.

The diagnostic receiver stops after ten minutes. Exit/focus loss releases held
input; exiting the screen does not itself stop the receiver. Use Stop Receiver
before starting a client operation. Input goes to a Java diagnostic sink; it
has not yet been attached to Wurm's keyboard/mouse queues.

## Exact client files to import

Use the **same complete client ZIP** that produced the successful 0.7.0 import.
It should already be accessible in Downloads. The new package needs its own
import; do not copy just the uploaded client.jar or use your server-runtime ZIP.

The ZIP must contain the legally obtained full client installation, preserving:

- `client.jar` and `common.jar` at the client root.
- The complete `lib/` directory, with the matching libraries from that install.
- The complete `packs/` directory, including its original resource JARs.
- All other original asset directories/files, including `nativelibs/` if present.

Flat ZIPs and one outer `WurmLauncher/` folder are accepted. An Android file
manager's ZIP/Compress action on the entire client folder is sufficient. The
known client bundles LWJGL/OpenAL/JInput classes in client.jar; do not download
or add guessed extra LWJGL JARs. No proprietary files are downloaded by the app.
No POC JAR, separate Steam shim, desktop JRE, graphics adapter download or JavaFX
installation is required from you; the app supplies its handwritten adapters.
Allow room for the roughly 1.64 GB expanded client in this separate package.

## Client compatibility and local startup

1. In **0.8.0 → Client tab**, import that complete client ZIP and wait for
   **Client import validated**. Close/reopen and check it remains present.
2. Keep the default local player **Thor**, or enter a 3–20 character name using
   letters/digits and tap **Save Player Name**. Passwords are blank in this
   preview; existing password-protected accounts/servers are not covered yet.
3. Open the existing **0.6.0** app and Start Adventure as usual. Wait for its
   Running/TCP 3724 status. Return to **0.8.0 → Client tab → Start Local Game**.
   No server reimport/migration is needed for this route. Stop any receiver first.
4. Leave the app open until the attempt finishes. It runs independent inventory,
   local compatibility, actual entry and graphics stages, each capped at two
   minutes. Start Client runs the same stages without ensuring server readiness.
5. Export Client Report again as **wurm-client-report-local.txt** and send it.
   Confirm the old server still runs; use its normal Stop Server when finished.

Look for these distinctions in the report:

| Marker | What it proves |
| --- | --- |
| `TCP_PROBE ... reachable=true` | A local listener answered; not Wurm login |
| `LAUNCHER_REPLACEMENT` / `COMPAT_CLASS` | The new source-built adapters are active |
| `STEAM_HANDLER_RESULT InitSuccess` / `STEAM_COMPAT_OK` | Real imported handler/ticket APIs work with the local shim; not server acceptance |
| `RESOURCE_PACKS_VALIDATED` | Local resource ZIPs are readable; not complete rendering assets |
| `PROFILE_PREPARE` / `PROFILE_READY` | Which side of actual player-profile construction was reached |
| `ENTRY_INVOKE` / `CLIENT_GAME_THREAD` | Actual game launch was invoked and its thread observed |
| `BOOTSTRAP_FAILED`, `CLIENT_THREAD_FAILED`, `CHILD_EXIT` | Exact failing stage, exception/stack and exit |
| `INPUT_READY` / `INPUT_RECEIVED` | Controller events reached the diagnostic JVM |

**Blocked is still an expected result.** The host's actual client reaches native
LWJGL loading while constructing its Profile, before the final launch call.
The Thor report must identify the next native/graphics failure after the AWT
mode correction. Rendering, local-ticket acceptance, login/world entry and
controller gameplay are not demonstrated by this release. Export soon after
running, because logs are bounded and rotate.

If local server startup fails, also export its normal server Session Report.
Otherwise only the **Client Report** is needed. No proprietary game JAR needs
to be sent again; the matching client.jar has already been inspected.
