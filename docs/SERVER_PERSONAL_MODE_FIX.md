# 0.10.15: retain personal-server mode for first-time character creation

## Confirmed 0.10.14 device result

The paired Thor reports use the `loginidentity` package and server session
`session-fd2f209b-89c3-4a29-91ac-a3677111f342`. The client supplies its persisted
credential, authenticates locally and renders the splash. At 23:07:54 UTC on
2026-09-09, the server's `DbPlayerInfo.load` reports `No such player - Thor`.
The client receives the website-registration rejection and stays in RETRY_WAIT
with `authenticated=true`, `loggedIn=false`. It reaches frame sequence 202.
The user stops the client (`cancelled=true`, exit 143), then requests server
shutdown, which exits zero. This run does not reproduce the earlier native abort.

## Cause verified against the exact imported JARs

The recovered client and server match the reports' SHA-256 values:

| Input | SHA-256 |
| --- | --- |
| client.jar | `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19` |
| server.jar | `9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06` |

The POC called `Server.getInstance().setIsPS(true)` and printed true, but then
called `launcher.runServer(false, true)`. The original ServerLauncher method
assigns its first argument to `Server.setIsPS` and its second to
`SteamHandler.setIsOfflienServer` before `Server.startRunning`. Consequently the
old log described an intermediate value: personal mode was false during login.

The original LoginHandler catches the missing-player lookup and tests whether
creation is permitted. Personal-server mode is one of its existing routes to
creation. With the current ordinary login and server settings, false reaches
the registration rejection. The creation branch constructs the player, sends
login acceptance and presents `SelectSpawnQuestion` with "Define your character"
and a gender prompt. That agrees with naming a character before entering and
finalizing it inside the game. No additional client creation packet is indicated
by this failure. The boolean passed to WurmClientBase.launch is unrelated.

## Correction and scope

The authored POC now calls `launcher.runServer(true, true)`. It removes the
misleading pre-start setter/log and observes the retained mode after runServer:

```text
[WurmARM64] SERVER_MODE_ACTIVE personal=true; observed after runServer; new-player login still needs testing
```

If the observed mode is false, startup fails explicitly before the keepalive.
The marker proves the mode only; creation, login and visible gameplay require
separate device evidence. Personal mode applies to this local server as intended
by the POC. The original name, identity, authentication, server-password,
maintenance and creation checks still execute. No player rows are inserted manually.

Both authored POC classes were rebuilt against the supplied server. Steam shim
source, imported game JARs, databases, encoder and SQLite overlays, client
credentials, Serial GC, native runtime, rendering and controllers are unchanged.
No proprietary classes or disassembly are committed or bundled.

Current authored POC SHA-256:
`82a39c9797a394b036785ad366e5c1a6ed0de935ab1f3b82e1fcc80f5181dfa4`.
Gradle and the importer require this artifact; Gradle also checks source hashes
and the exact two-class allowlist. The separate `.personalserver` package uses
version code 29 / 0.10.15 and preserves older installations and their data.

## Thor test: next milestone

1. Install **0.10.15 Wurm-Server.apk** from **v0.10.15-personal-server**. Keep
   older apps/checkpoints, but stop their servers and clients.
2. Import the same prepared server runtime ZIP and select **Adventure**. The
   app supplies the corrected POC. Keep the complete previously accepted layout.
3. Import the same complete client ZIP, including all its packs and dependencies.
   Keep the local player name **Thor**. No new game files, desktop launcher,
   root, Termux or account password is needed.
4. Use **Start Local Game in 0.10.15** so its corrected server is used. Allow
   up to five minutes for startup or capture the first failure.
5. If character setup appears, complete it using the available controls. Capture
   the setup/world screen and test movement/look/clicks for 60 seconds if possible.
   If a setup control cannot be operated, capture that screen and reports.
6. Stop Client if active and export **Client tab → Export Client Report**.
   Stop Server, wait, then export **Server tab → Export session report**.
   Send both 0.10.15 reports and the screenshot before Retry.
7. If creation/world entry and clean shutdown succeeded, start again with the
   same app, player and working world, without reimporting. Confirm the same
   character returns and report whether a movement/location change survived.

Both existing ZIPs need importing because the package has separate storage.
Keep exports/checkpoints; cross-package character identity migration is unqualified.
A creation log alone is insufficient to claim a saved character. World entry and
the same-character restart are the next acceptance gate. Audio and the earlier
EGL/Scudo corruption remain unresolved; no native stability claim is made.

## Validation

Public regressions run the compiled POC source and the actual packaged artifact
against authored APIs that apply the launch arguments at startup. The old
`false,true` call fails at that boundary. Tests verify personal/offline mode,
post-start reporting, mode-loss failure and reaching the original keepalive.

An optional private test retains the supplied ServerLauncher's entire original
`runServer(boolean,boolean)` method byte-for-byte, including exception table and
stack maps, and its fields. Other methods/static initialization are omitted;
an uninitialized instance avoids desktop logger setup. Authored dependencies stop
execution at `startRunning` before world access. All four argument combinations
confirm that the original method overwrites both flags, including a pre-set true.
This verifies argument semantics, not a full server/login/world simulation.

```sh
python3 -m unittest discover -s tests -v
# Optional private checks using owned game files and the pinned public JDBC JAR:
WURM_TEST_SERVER_JAR=/absolute/path/server.jar \
WURM_TEST_SQLITE_JAR=/absolute/path/sqlite-jdbc-3.53.2.1.jar \
  python3 -m unittest discover -s tests -v
```

The release workflow builds, unit-tests and lints all three Android variants,
verifies APK/runtime/POC contents and signing, and publishes the immutable release
only after these gates succeed. No device result is inferred from CI.

Local validation passed all **112 host tests**, including the optional supplied-server
checks. The rebuilt Steam shim class is byte-identical to 0.10.14. Artifact/source
and importer pins agree. Android build/device results are separate gates.

## Changed files

| File | Change |
| --- | --- |
| `poc/src/poc/AndroidServerMain.java` | Correct launch mode; observe/require retained mode. |
| `poc/artifacts/wurm-arm64-poc.jar.base64` | Rebuilt authored artifact. |
| `scripts/build-poc.py` | Deterministic two-class rebuild with owned compile dependencies. |
| `tests/test_poc_server_mode.py` | Source/artifact regressions and optional original-launcher check. |
| `app/build.gradle.kts` | POC/source pins; 0.10.15/code 29; separate package. |
| `app/src/main/java/io/github/russianranger/wurmlauncher/ManagedRuntimeStore.kt` | Matching import POC pin. |
| Managed `ClientActivity.kt`, `ClientSession.kt`, `ManagedActivity.kt`, `GraphicsTestActivity.kt` | Version and current test/report text. |
| `.github/workflows/android.yml` | Publish/checksum the new release and this guide. |
| `README.md`, `poc/README.md` | Current milestone and corrected POC/rebuild contract. |
| `docs/CLIENT_INTEGRATION.md`, `docs/IMPLEMENTATION_PLAN.md` | Current evidence and continuation gate; retain history. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md`, `docs/SERVER_PERSONAL_MODE_FIX.md` | Release notes, evidence, limits and device steps. |
