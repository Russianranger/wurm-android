# AYN Thor: 0.10.1 headless keybinding fix

## What the two 0.10.0 reports establish

The owner's `wurm-client-report (1)(1).txt` is the successful window/controller
run. It records Adreno 740 / GL4ES OpenGL 2.1, 366 640x360 frames, Android frame
display, LWJGL key codes 30/32/17 with releases, mouse motion, left/right button
presses and releases, clean EGL teardown and `WINDOW_PROBE_PASS`, child exit 0.
The owner also confirmed that the triangle/controller test succeeded. This is a
physical pass for that bounded Gate 4 substep. Wheel, every mapping, text entry
and full Wurm gameplay are not inferred from the report.

`wurm-client-report (2)(1).txt` retains the earlier test history and adds a
**separate local-client attempt**. That attempt reached TCP 127.0.0.1:3724, passed
inventory and Java Steam compatibility (`inventory=0`, `compat=0`), validated the
three resource packs, initialized the real client entry class and reached the
960x540 virtual display mode. It then exited 42 before login:

```
java.lang.NoClassDefFoundError: javafx/stage/Stage
    at com.wurmonline.client.settings.Profile.loadSettings(Profile.java:395)
```

Private inspection of the supplied client JAR (SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`)
identified this path: Profile calls `WurmSettingsFX.loadAllKeybinds(File)`;
WurmSettingsFX extends WurmStage, which extends JavaFX Stage. A non-UI keybinding
operation therefore loads the desktop settings window hierarchy. It was not a
failed server login: the client had not reached that stage. No server, import,
Steam shim or native graphics change is needed to address this exception.

## Narrow implementation

A separately authored `WurmSettingsFX` facade supplies the keybinding load/save,
lookup and update API without extending WurmStage or any JavaFX class. The real
Profile, Options, player profile and game binding/console implementations remain
in the imported client. `KeybindStore` reads bind entries without executing console
commands. It supports primary/secondary keys, duplicate-key reassignment and quoted
actions. Unchanged files are kept byte-for-byte intact. Changes to bind-only files
are saved atomically, retain comments, and keep a first-save `.android-backup`.
Malformed/oversized inputs and external file edits fail visibly; files containing
other console commands are preserved and cannot be rewritten by this helper.
These Wurm keyboard bindings are separate from Android's persistent controller
profile, which still translates the physical controls into desktop input events.

Direct startup verifies the headless helper's version and class provider. Client
reports add `SETTINGS_ADAPTER`, `KEYBINDS_LOADED`, `KEYBINDS_PRESERVED`/`SAVED`,
and the exception message in the first bootstrap-failure line. The compatibility
JAR's exact class allowlist includes only the authored facade/store additions.
Compile-only Profile/Options/MultiOption API stubs are **not packaged**. No fake
JavaFX classes or proprietary game implementation is included.

The desktop in-game settings window is not implemented. Attempts to show or
restart it are explicit unsupported operations; JavaFX-specific dialog callbacks
are not supplied. Android Controller Settings remain available. This fix removes
the observed startup dependency; it does not claim every desktop UI dependency,
OpenAL, later graphics feature, local authentication or world entry is solved.

## Verification

The original 0.10.0 compatibility JAR reproduced the exact Stage error using the
owner's actual client JAR on the host. The new helper then passed real Profile
initialization, player/config association, config save and PlayerProfile creation
in two fresh JVMs, loading **70 actions / 78 keys**, including W and UP for forward
movement. The binding-file hash remained unchanged across those runs.
This is actual profile bytecode, not a simulated Profile implementation. No Wurm
resource packs or game rendering/login were needed for this bounded host test.

The public test suite uses authored fixtures. New tests cover unchanged bytes,
primary/secondary/remapped bindings, reload/backup, quoted text/semicolon parsing,
malformed input, input bounds, external-edit protection and preserving unrelated
console commands. The direct-bootstrap fixture now invokes the same facade from
its Profile load/save path. Existing managed-server/POC/SQLite/graphics code is
unchanged. Android builds, unit tests, lint and release-APK allowlist/hash checks
remain required before publication.

For an optional private regression, compile `scripts/ProbeClientProfile.java`
against the built `client-compat.jar` and the supplied client JAR, then run it in a
disposable working directory with this classpath order: compiled probe, new
client-compat.jar, wurm-window.jar, graphics-probe.jar, pojav-wurm-api.jar, client.jar.
Use Java 17, `-Djava.awt.headless=true`, a private `-Duser.home`, the existing
`-Dwurm.client.offline=true`, host/port 127.0.0.1/3724, and the qualified host native
library path plus `LibraryNames`/system allocator settings described by
`scripts/test-window-host.py`. This does not require or execute a graphics context.
Run twice in the same scratch working directory to check persistence. The private
JAR is not part of CI, source control or the release.

## Exactly what to install and test next

1. Keep the working **0.6.0 server** and **0.10.0** installed. The window/controller
   test is already confirmed; it does not need to be the main test again.
2. Download [Wurm-Server.apk](https://github.com/Russianranger/wurm-android/releases/download/v0.10.1-client-settings/Wurm-Server.apk)
   from [v0.10.1-client-settings](https://github.com/Russianranger/wurm-android/releases/tag/v0.10.1-client-settings)
   on the Thor. Install it alongside the existing apps. Check **0.10.1-managed-preview**,
   version code **15**, package `io.github.russianranger.wurmlauncher.clientsettings`.
   This separate package avoids the changing CI debug-signing key; it does not
   overwrite the old app or Adventure world.
3. Open **0.10.1 → Client tab → Import Client ZIP** and import the **same complete
   client ZIP that already passed**. This new package needs its own import. Keep
   `client.jar`, `common.jar`, full `lib/`, full `packs/` and all other original
   assets together; the server ZIP or client.jar alone is insufficient. No new
   game files, JavaFX, Java, Steam, graphics libraries or Termux commands are needed.
4. Start Adventure in your working **0.6.0 server app** and wait for TCP 3724.
   Return to **0.10.1 → Client tab → Start Local Game**. The existing local-start
   flow checks that port and attempts the actual client entry with the fixed helper.
5. Watch for the next Wurm screen or startup error. This remains a **two-minute
   startup diagnostic**, with the server controlled separately. If a Wurm image
   appears, try the controls and describe whether it is a menu, login or world.
6. After the attempt finishes (or after Stop Client), return to Client and tap
   **Export Client Report**. Send **wurm-client-report.txt** and tell us what appeared.
   This is the **Client Report**, not the server Session, Storage or World report.

Expected progress markers: `SETTINGS_ADAPTER headless-keybinds-v1`,
`KEYBINDS_LOADED`, `KEYBINDS_PRESERVED`, `PROFILE_READY`, followed by resource/game
thread startup or its exact next failure. TCP readiness and a profile pass are not
Wurm authentication or world entry. Full Gate 4 rendering and Gate 5 login remain
open until confirmed on the Thor.

## Files changed

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish immutable 0.10.1 APK and attach the new diagnosis/test guide. |
| `README.md` | Point to the current local-client test. |
| `app/build.gradle.kts` | Package the headless facade; version 15 / 0.10.1 / clientsettings. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Identify 0.10.1 in the Client screen. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Report 0.10.1 and distinguish confirmed window input from pending client startup. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Identify the new version in the existing viewer; graphics behavior unchanged. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Identify the profile-fix APK on the first screen. |
| `client-compat/README.md` | Document the facade/store and compile-only signatures. |
| `client-compat/src/com/wurmonline/client/launcherfx/WurmSettingsFX.java` | Headless engine-facing keybinding helper, without JavaFX/WurmStage inheritance. |
| `client-compat/src/wurm/android/compat/KeybindStore.java` | Validated bind storage, no-op preservation, remapping and atomic saves with backup. |
| `client-compat/stubs/com/wurmonline/client/options/MultiOption.java` | Compile-only inherited value() signature; excluded from APK. |
| `client-compat/stubs/com/wurmonline/client/options/Options.java` | Compile-only keybindingsSource field; excluded from APK. |
| `client-compat/stubs/com/wurmonline/client/settings/Profile.java` | Compile-only profile/directory accessors; excluded from APK. |
| `docs/CLIENT_INTEGRATION.md` | Current failure diagnosis, fixed architecture and evidence. |
| `docs/CLIENT_SETTINGS_FIX.md` | Report analysis, implementation limits, exact install/import/test steps and this inventory. |
| `docs/CLIENT_THOR_TEST.md` | Redirect to the new local-client test. |
| `docs/GRAPHICS_THOR_TEST.md` | Mark the window/controller test confirmed and point to 0.10.1. |
| `docs/IMPLEMENTATION_PLAN.md` | Record the physical pass and narrow implemented JavaFX dependency fix. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | 0.10.1 release notes and next report request. |
| `runtime-probe/src/client/ClientBootstrap.java` | Include the exception message in the first failure/status line. |
| `runtime-probe/src/client/DirectClientLaunch.java` | Require/log the headless settings helper before real Profile startup. |
| `scripts/ProbeClientProfile.java` | Optional actual-client profile/persistence probe without rendering or login. |
| `scripts/verify-managed-apk.py` | Verify the exact seven-class compat JAR and exclude signature stubs. |
| `tests/test_client_compat.py` | Exercise the facade from the Profile fixture and enforce packaged classes. |
| `tests/test_client_keybinds.py` | Seven regression cases for binding preservation, remapping, parsing and safe persistence. |
