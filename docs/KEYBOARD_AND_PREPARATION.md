# 0.10.40 — keyboard submission and offline preparation dependencies

Continue on `mod-launcher-test`; main remains unchanged. The user reports
0.10.39 functional except that keyboard Enter fills the Wurm chat field without
sending. This release fixes the input path and completes the dependency portion
of automatic server preparation. **Conversion of clean desktop server JARs is
not yet qualified.**

## Recovered device evidence

Recovered the latest `wurm-support.zip` (179,237 bytes), SHA-256
`683d0cc10978f97b12966a98e9a1fa9123ad85442706d31b930a93b3044bafa6`.
Its session header identifies `.launcherpreview` and export time
2026-09-13T11:19:48.647520Z; it contains client, server, session and storage reports.
Both client entry processes exit 0. Both servers exit 0 after requested normal
stops, with recorded process durations of 251,369 and 133,764 ms. Client and
server loaders each reach ready count 1. The 56 retained presented-FPS samples
have median 30.0. These short sessions do not establish long-term stability.
No fatal ASan report, OOM, fatal signal or frame-readback failure marker was
found. Previously tracked startup graphics/content warnings remain.
The keyboard report contains Enter key-code events; text content is not logged.

## Keyboard cause and change

Two independent problems were verified:

1. The Android editor requested Done, whose listener called only `insertDraft`.
   It never queued a game submission.
2. The separate Enter button emitted KEY_RETURN with a zero character. Private
   inspection of the supplied client shows `WurmInputField.keyTyped` submits
   on LF/CR; its `keyPressed` switch does not submit KEY_RETURN.

The composer now requests **Send**. Send, Done, Go and hardware Enter in that
editor call the same batch submission as the **Send / Enter** button. The batch
contains all draft UTF-16 text units, then `KEYCHAR 28 13 0` and `KEY 28 0`.
Insert remains insertion only. Empty Send / Enter submits text already inserted
in the selected game field. Failed queue admission retains the draft and queues
no part of the batch. Hardware release/repeat callbacks are consumed without
sending duplicates. Hiding the keyboard still retains the draft without sending.

A third issue appeared during verification: each text unit makes two LWJGL
events, while the pinned queue holds 200 events. A 240-unit paste could lose its
tail and Enter. The generated Java adapter now exposes remaining event capacity;
the game thread leaves pending input in its IPC queue until the game drains
LWJGL. Ordering, releases and the existing bounded queues are retained. Native
renderer/audio code is unchanged. This is queue admission, not proof that the
game field had focus or a network chat message was delivered.

## Automatic dependency preparation

The earlier handoff's Android SQLite provenance gap is resolved. Both files are
unmodified public Maven artifacts and exactly match the working device pins:

| Artifact | SHA-256 |
| --- | --- |
| `sqlite-jdbc-3.53.2.1.jar` | `f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1` |
| `sqlite-jdbc-3.53.2.1-natives-android.jar` | `011d4edb8d06012ced78d6aa675ffc85bf339d3cd640845684b80873ec5a6e97` |

[Public artifacts](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.2.1/),
[upstream source](https://github.com/xerial/sqlite-jdbc),
[corresponding Java sources](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.2.1/sqlite-jdbc-3.53.2.1-sources.jar).
The Android classifier includes `Linux-Android/aarch64/libsqlitejdbc.so`.
The APK now bundles both complete, checksum-verified JARs as child-JVM assets,
with their license notices; they are not Android/D8 dependencies. Setup is offline.
No replacement native build or dependency-version change is needed.

**Server → Setup & runtime → Prepare / import server ZIP** applies recipe
`thor-prepared-sqlite-1`. It accepts the same verified patched `server.jar` and
`common.jar` as the previous startup gate, together with `lib/` and an existing
world. It supplies absent SQLite JARs and the existing app POC, verifies present
dependencies, records `wurm-preparation.properties`, and inventories the final
files and hashes before atomically selecting the import. Unknown game JARs or
changed dependencies fail in staging. Input JARs, world databases, mod files and
configurations retain their bytes. Imports with a top-level wrapping folder
are supported. Publication failure/cancellation retains the previous selection.

The game JVM is not run during import. Existing Java/SQLite/network, position
and login preflight still runs before Wurm opens the working world; existing
checkpoint, mod order, G1 server and Serial client policies remain unchanged.
The source ZIP on the user's device is never modified. The preview retains its
existing rule that a selected original import cannot be replaced through this
button; full backup restore remains the supported migration route.

## Remaining clean-server prerequisite

The recovered `server.jar` exactly matches the prepared baseline
`9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06`.
Its older reference item classes are not evidence of a pristine vendor archive.
An **untouched, user-owned Wurm Unlimited dedicated-server ZIP** (or its
original `server.jar` and `common.jar` for the initial patch comparison) is still needed
for comparing and qualifying the item SQLite transformation, class identity and
SQL parameter/update/rollback semantics. Include `server.jar`, `common.jar`,
`lib/`, configuration/resources and an included world. No SQLite dependency
upload or compiler is needed. Do not remove pins or claim this build converts
arbitrary clean stock files. No proprietary files/disassembly are committed.

## Installation and focused Thor checks

This is versionCode 54, package `io.github.russianranger.wurmlauncher.keyboardprep`.
Preview CI signing still uses a per-run key, so keep 0.10.39 installed.

1. Normally stop both runtimes in 0.10.39. Use **Backups & migration → Export
   complete backup**. Install 0.10.40 alongside it, then restore that backup
   through the same screen. This preserves both imports, worlds, mods, bindings
   and settings; no separate client reimport should be necessary.
2. Start Play. Select Wurm chat, gear → Show keyboard. Type a short message and
   press the Android keyboard's Send key. Confirm it appears once in chat.
3. Try Insert followed by Send / Enter with an empty composer. Try a longer
   draft (up to 240 units), Backspace, Hide/reopen, and another game text field.
   Keep the intended game field selected; the composer does not infer focus.
4. Automatic dependency preparation can be tested in a fresh app installation
   using a **copy** of the already-prepared server ZIP with the two `poc-lib`
   SQLite JARs and POC omitted. Keep the original archive and complete backup.
   Prepare / import should supply these files offline; export the resulting
   working runtime and verify play/save/reentry. Clean desktop ZIP testing
   awaits the recipe above.
5. Export the support bundle after the keyboard test. Native/GC tuning and a
   persistent release signing key remain separate later work.

## Validation

Local Android API compilation passed (resource identifiers supplied by an
API-compilation stub, not an APK build). 28 focused Kotlin/JUnit tests passed:
composer ordering/empty submission/queue rejection, preparation and dependency
validation, unchanged game/world/mod content, cancellation/corruption and
publication rollback, import accounting, and existing checkpoint behavior.
The actual pinned LWJGL queue regression passes maximum-length text followed by
CR and release across multiple drains; the authored queue fixture also passes.
The local full host suite passed 168 tests with 16 unavailable fixture/platform
skips. [Release CI 34755650835](https://github.com/Russianranger/wurm-android/actions/runs/34755650835)
passed all three Android variants and required native/input gates; its initial
host suite ran 168 tests with 17 expected skips. Published APK checksum,
package/version, signature and maintained packaging verification passed.
Exact release identity and native comparison are recorded in HANDOFF.
Device confirmation of the new behavior remains pending.
