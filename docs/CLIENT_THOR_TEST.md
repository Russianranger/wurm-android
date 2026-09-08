# AYN Thor: client import/bootstrap/controller test (0.7.0)

This is the next testable client milestone, not a playable Wurm release. It
attempts real client class initialization and LWJGL startup and reports the
precise blocker. It also tests physical controller mapping into a Java receiver.
No PC, root, Termux command or separate Java installation is needed for these tests.

## Install and prepare the exact input

1. Download `Wurm-Server.apk` from
   [v0.7.0-client-preview](https://github.com/Russianranger/wurm-android/releases/tag/v0.7.0-client-preview)
   on the Thor and install it. Its label remains **Wurm Server**; Android App info
   shows version `0.7.0-managed-preview`, package
   `io.github.russianranger.wurmlauncher.clientpreview`.
2. **Keep 0.6.0 installed with its world data.** The package is deliberately
   separate because CI uses a new debug signing key. This is not an in-place
   upgrade. The Server tab in 0.7.0 initially has no server import; the old app
   still owns Adventure and its reports.
3. Put one ZIP of your **legally obtained complete Wurm Unlimited client
   installation** in Android Downloads (or another document-picker location).
   A common installation folder is called `WurmLauncher`. The ZIP must contain
   `client.jar`, `common.jar`, the complete `lib/` directory and **all other client
   asset/resource directories and files**, preserving their relative paths.
   Select the entire client folder with an Android file manager's ZIP/Compress
   action; do not create a JAR-only archive. Both a flat ZIP and one outer
   `WurmLauncher/` folder are accepted. Nested installer/Steam library folders
   must be unpacked to the actual client first.
4. In typical versions `lib/` includes LWJGL2 (such as `lwjgl-2.9.1.jar`),
   `lwjgl_util`, `SteamClientJni.jar`, PNGDecoder and other game libraries. Keep
   the exact matching set from your client; do not rename JARs or download guessed
   replacements. Include `packs/`, textures, sounds, models or other resources
   wherever your version stores them. The importer checks class providers and
   lists resource candidates; startup must still establish asset completeness.
5. The server runtime ZIP previously used for Adventure is **not** the client ZIP.
   If you currently have only the server files, the controller/JVM/GLES test below
   can still run, but Wurm bootstrap needs the owned client files. This APK does
   not download Steam depots or supply game files. No `wurm-arm64-poc.jar`, desktop
   JRE, extra Android Java or downloaded graphics pack is required from you.

Allow enough internal space for the expanded ZIP and, on replacement, both old
and new client copies. The importer retains at least a 128 MiB free-space reserve
and limits expanded input to 32 GiB. The full ZIP remains your source backup.

## First run: controller and Android surface (works without client files)

1. Open **0.7.0 Wurm Server → Client tab** and allow notifications.
2. Open **Controller Settings**. Check the detected name. Keep defaults initially,
   or edit a binding/dead zone/mouse speed/invert-Y; tap **Save mappings**.
3. Open **Controller / JVM Input Test → Start JVM Input Receiver**.
   Wait for **JVM receiver: READY**. Runtime installation/checks can take a moment.
   A dark GLES background and cyan diagnostic cursor should appear. A blank
   background alone is not evidence of Wurm rendering.
4. Hold the left stick up/right: the diagnostic should show KEY 17/32 down and
   release (W/D). Hold the right stick: the cursor should move continuously and
   stop at neutral. A and RT map to left mouse; LT to right mouse. Hold A and RT
   together, release one, then the other: only the last release ends left-click.
   Test B, X, Y, shoulders, D-pad, Start and Select.
5. Switch apps while a control is held, then return with sticks neutral. No held
   input should remain. Unplug/reconnect an external controller if you use one;
   the built-in Thor controller does not need physical disconnection.
6. Tap **Stop Client Receiver**, then **Exit Input Test** using the touchscreen
   (controller Escape is intentionally captured in the test). Change one setting,
   save, close/reopen the app and verify it persists. Reopen the input test to
   apply a changed profile. Restore defaults if desired. The receiver stops
   automatically after ten minutes; that is the diagnostic limit.

Expected report markers: `ANDROID_SURFACE_CREATED`, Android GL vendor/renderer/
version, controller name and axis ranges, `TRANSLATE`, `INPUT_READY`,
`INPUT_RECEIVED`, held counts and focus `RESET`. `sink=diagnostic` means the
events reached our JVM receiver, **not Wurm's keyboard/mouse queues yet**.

## Client import and real startup attempt

1. In Client tab tap **Import Client ZIP**, select the complete client ZIP and
   wait for **Client import validated**. If it fails, export the client report
   immediately. A failed replacement preserves the previous accepted import.
2. Close/reopen 0.7.0 and confirm the client import generation is still displayed.
3. Tap **Start Client**. Leave the app open until the stages finish. The app uses
   its packaged Java to inspect your actual JARs, initialize `WurmClientBase`,
   resolve/attempt its supported direct launch method, and independently invoke
   imported LWJGL2 `Display.create()`. Each stage is capped at two minutes.
4. The expected current result is **Blocked**, with explicit method-signature,
   missing-class or native-library evidence. That is useful output for the next
   adapter implementation. No Steam shim/renderer is secretly substituted, and
   no Wurm login is claimed. Stop Client can cancel a stuck stage.
5. Tap **Export Client Report** and save `wurm-client-report.txt` in Downloads.
   This is the **Client Report**, not Storage, World or server Session Report.
   Export soon after the test because logs are bounded/rotated.

The report includes client file identity/classpath, exact launch and Steam JNI
method descriptors, JRE startup, linkage/graphics exceptions, child exit/native
crash evidence, controller profile and persisted session logs. It does not export
your proprietary JAR contents. Keep reports from different runs with distinct
filenames if needed.

## Local server + client flow on the same Thor

1. Stop any client diagnostic still running. Open the **existing 0.6.0** app and
   start Adventure normally. Wait for its proven **Running/TCP 3724** status.
2. Switch to **0.7.0 → Client tab → Start Local Game**. Keep the client import
   from above. It should record `TCP_PROBE target=127.0.0.1:3724 reachable=true`
   and run the client bootstrap. No PC or server migration is needed for this path.
3. Wait for the explicit client result and **Export Client Report again**, preferably
   as `wurm-client-report-local.txt`. Confirm the 0.6.0 server remains running.
   Stop it using its own normal Stop Server when finished.
4. To test 0.7.0 starting the server itself later: normal Stop in 0.6.0, export its
   working runtime ZIP, import that ZIP from **0.7.0's Server tab**, keep Adventure
   and port 3724, then use Start Local Game with no existing listener. The new app
   starts its own managed server, waits up to three minutes, then attempts client
   startup. Do not use a live server ZIP or erase the old app to do this test.

Return the exported **client report(s)** and whether the controller name, stick
motion, button translations and reopened settings matched expectations. Send a
server Session Report only if server startup/readiness failed. Reaching TCP 3724
is currently the local orchestration pass; authenticated Wurm connection and
entering the world remain separate, unpassed gates.

## What happens next with these reports

The next implementation can use your exact launch and Steam signatures to build
a version-specific direct launcher/offline adapter. The independent native
failure plus Thor GLES details guide the Pojav LWJGLX/native-window/GL4ES host
integration described in [CLIENT_INTEGRATION.md](CLIENT_INTEGRATION.md). No
external-PC test is needed to supply that evidence. If the report shows missing
client files instead, repair the ZIP first and repeat this APK's test.
