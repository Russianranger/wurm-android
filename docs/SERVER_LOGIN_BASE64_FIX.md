# 0.10.13: Java 17 server login compatibility

## Thor evidence

The 0.10.12 memory report passes both isolated children: G1 observes 32
collections and Serial 48, with 126 unloaded classes in each. Passing this bounded
test does not rule out a runtime bug under another workload or explain the
previous native heap corruption.

The real client confirms Serial (`Copy`, `MarkSweepCompact`), completes graphics
initialization and remains in LOGIN_WAIT for about 60 seconds with 196 retained
frames. Its local authentication flag is true, with 109 payload bytes queued
and 6 bytes read. The screenshot shows the actual Connecting splash. The final
exit 134 accompanies `cancelled=true` and the app's Stop; it does not establish
a recurrence of the earlier unsolicited GC-thread abort during startup.

The accompanying **0.10.11 server** was ready at 17:06:07Z. At
**2026-09-09T17:06:35.223Z**, handling the login request throws:

```text
java.lang.NoClassDefFoundError: sun/misc/BASE64Encoder
  at com.wurmonline.server.LoginHandler.encrypt(LoginHandler.java:4479)
  at com.wurmonline.server.LoginHandler.login(LoginHandler.java:422)
```

It shuts down without a Stop request and exits zero at 17:06:36Z. Zero therefore
does not confirm a successful session/save. The external client cannot read that
other app's server exit/log, explaining its continued login wait. Use the new
version for both sides of the next test.

The client also catches an incompatible 32-bit OpenAL library error and continues
to login. Audio remains unqualified; this release addresses the fatal server
login dependency first. No new graphics or audio success is claimed.

## Inspected ABI and replacement

The supplied server JAR SHA-256 is
`9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06`.
Its `com/wurmonline/server/LoginHandler.class` SHA-256 is
`00fb374c584cf66311057967c6e0c9fe7bd17932e5aee218100a520fdfd4a73e`.
The inspected `encrypt(String)` calculates SHA-1 over UTF-8, constructs the old
encoder and invokes `encode(byte[])`. Its 20-byte digest becomes 28 padded Base64
characters without a newline. No password value is needed to inspect this path.

The old encoder was [removed from the JDK](https://docs.oracle.com/en/java/javase/22/migrate/removed-apis.html).
The supported replacement is [java.util.Base64](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Base64.html).
The legacy [CharacterEncoder encode implementation](https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/sun/misc/CharacterEncoder.java)
terminates full lines but not a final partial line;
[BASE64Encoder](https://github.com/openjdk/jdk8u/blob/master/jdk/src/share/classes/sun/misc/BASE64Encoder.java)
uses 57 input bytes per line. The authored adapter preserves those semantics,
including the 55/56/57-byte boundary and final newline on exact full lines. It
implements only the constructor and encode(byte[]) ABI required here.

Before opening a world, preflight now:

1. Prepares the existing position overlay, `server-sqlite.jar`, unchanged.
2. Checks the inspected login-class hash and redirects exactly one UTF8 owner
   constant from `sun/misc/BASE64Encoder` to `server/LegacyBase64Encoder`, using
   the tested constant-pool writer. Every method body, stack map, exception table
   and remaining byte stays intact. SHA/UTF-8/password comparisons, authentication,
   player creation and login decisions remain Wurm's original code.
3. Exercises the adapter with the public SHA-1 `abc` vector, then atomically
   writes `server-login.jar` into the disposable private session. It contains
   only the transformed login class. The imported JAR is never modified.
4. Places both overlays before server.jar. Bootstrap reverses the login overlay
   to verify the pinned original hash, then checks the classloader's selected
   resource before invoking the POC. Unknown classes, tampering and wrong
   classpath order fail explicitly.

The standalone batch password tool also references the removed encoder; it is
outside the launch path and is not patched. No JDK module is replaced or class
injected into sun.misc. Authentication checks are preserved. POC, item fixes,
position overlay, imported worlds, client Serial GC, graphics/window/controller/
Steam components and ARM64 runtime pins remain. No proprietary classes, reports,
JARs or decompiled code are committed or bundled.

Expected server-report markers:

```text
[server-login] LOGIN_PATCH_READY ... encoderOwners=1
[server-login] BASE64_SELF_TEST_OK
[server-sqlite] POSITION_PATCH_ACTIVE ...
[server-login] LOGIN_PATCH_ACTIVE ...
[server-login] BASE64_ENCODE_ACTIVE inputBytes=20 outputChars=28
```

The last marker appears once when the actual server child calls the replacement;
it logs lengths only, never passwords, digests or tickets. These markers do not
prove login acceptance. That requires the existing LOGIN_ACCEPTED / GAME_LOOP
observations plus a visible world and controller test on the Thor.

## Exact next Thor test

1. Install **Wurm-Server.apk** from **v0.10.13-server-login**. Confirm **0.10.13**.
   Code 27 uses separate package
   `io.github.russianranger.wurmlauncher.loginbase64`; keep older apps/data.
2. Stop the 0.10.12 client and any previous servers. The next test must use the
   new server patch, not an older external TCP listener.
3. In **0.10.13 → Import Server ZIP**, select the same prepared runtime ZIP that
   previously imported successfully, including the item SQLite fixes and Adventure.
   The original POC ZIP is suitable. If you need recent saved world changes, use
   an older app's before-start checkpoint ZIP; the last unrequested server exit
   did not confirm a save. Keep the older data intact.
4. Select **Adventure**. In **Client tab → Import Client ZIP**, select the same
   complete client ZIP used in 0.10.12. Keep player name **Thor** initially.
5. Choose **Start Local Game in 0.10.13**. It starts this app's server, waits for
   TCP 3724, then starts the Serial-GC client at **127.0.0.1:3724**. Both reports
   must identify this version. The server should be owned by this app, not external.
   No repeat memory/controller/triangle test is required.
6. If the world appears, screenshot it and try movement/look/buttons for 60 seconds.
   If it stays Connecting for 60 seconds, capture and export. If it fails sooner,
   export immediately **before Retry**.
7. Use **Client tab → Export Client Report**: `wurm-client-0.10.13.txt`.
   Use **Server tab → Export session report**: `wurm-server-0.10.13.txt`.
   Send both and the screenshot. This is the **Server Session Report**, not
   Storage Report or World Report.
8. Stop the client and server with their controls. If server Stop reports an
   error, export its session report again. Keep the before-start checkpoint and
   original import. Gameplay persistence still needs separate verification.

Only the APK and your **two existing ZIPs** are needed. No PC, root, Termux,
manual patching or new proprietary download is required. The server ZIP retains
server.jar, common.jar, lib/, Adventure/world data, wurm-arm64-poc.jar and the
same two poc-lib/sqlite-jdbc-3.53.2.1*.jar inputs. The full client ZIP retains
client.jar, common.jar, lib/, packs/ and its existing assets; do not import just
the individual client JAR.

## Validation and limitations

**106 host tests and six targeted Kotlin tests pass.** Five new tests cover
padding/line boundaries, Java 17 linkage, unknown-input rejection, private-overlay
integrity/classpath selection, and the owner's original encrypt method bytecode.
The private method test reproduces the missing class before the fix and verifies
matching SHA-1/Base64 results for four public/authored inputs afterward. It isolates
the supplied method with its original Code, exception table and stack maps plus
an authored exception stub to avoid initializing unrelated game systems. It is
not a full Wurm login simulation; production retains the complete login class.

```sh
python3 -m unittest discover -s tests -v
# Optional local-only checks against legally owned files:
WURM_TEST_SERVER_JAR=/absolute/path/server.jar \
WURM_TEST_SQLITE_JAR=/absolute/path/sqlite-jdbc-3.53.2.1.jar \
  python3 -m unittest discover -s tests -v
```

Four private checks are skipped in CI. ManagedLaunch Kotlin tests require both
overlays before the imported server and retain game-free preflight/audit. Existing
client collector-policy tests still apply. Android variants must build, test and
lint before publishing. APK verification requires the new authored helpers,
source-backed POC, pinned runtime and absence of proprietary login/position/game
classes from bundled JARs.

**Completed:** testable Gate 5 server login dependency fix and the 0.10.12 bounded
memory/startup device gates. **Pending:** the complete patched login handler on
Android, acceptance/world entry, remaining rendering/input/audio compatibility
and gameplay saves. The earlier native corruption's origin remains unresolved.

## Every changed file

| File | Change |
| --- | --- |
| `.github/workflows/android.yml` | Publish immutable 0.10.13; attach/checksum this guide. |
| `README.md` | Current evidence, release link and two-import test. |
| `app/build.gradle.kts` | Version 27 / 0.10.13, separate loginbase64 package. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientActivity.kt` | Current guidance; existing client controls retained. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ClientSession.kt` | Report version/evidence; client execution unchanged. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/GraphicsTestActivity.kt` | Display version; window implementation retained. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedActivity.kt` | Display version and server login milestone. |
| `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/ManagedLaunch.kt` | Add private login overlay path/property before server.jar. |
| `app/src/testManagedPreview/java/io/github/russianranger/wurmlauncher/ManagedLaunchTest.kt` | Require both overlays; preserve launch order. |
| `docs/CLIENT_INTEGRATION.md` | Login failure and compatibility architecture. |
| `docs/IMPLEMENTATION_PLAN.md` | Current Gate 5 fix and physical test. |
| `docs/RELEASE_GRAPHICS_PREVIEW.md` | Release notes and two-report test. |
| `docs/SERVER_LOGIN_BASE64_FIX.md` | Evidence, ABI scope, tests, limits and changed files. |
| `runtime-probe/src/server/LegacyBase64Encoder.java` | Authored Java 17 adapter preserving encoding behavior. |
| `runtime-probe/src/server/ServerLoginPatch.java` | Generate/hash/reverse/selection-check the private overlay. |
| `runtime-probe/src/server/ServerPreflight.java` | Prepare login alongside the retained position patch. |
| `runtime-probe/src/server/ManagedServerMain.java` | Verify selected login overlay before POC/world startup. |
| `scripts/verify-managed-apk.py` | Require helpers; forbid proprietary LoginHandler. |
| `tests/test_server_login_patch.py` | Encoding/linkage/overlay and private-bytecode checks. |
