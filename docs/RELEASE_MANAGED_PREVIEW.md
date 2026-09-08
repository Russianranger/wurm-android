# Wurm Server 0.4.0 — managed server preview

This install-alongside APK adds no-root Wurm server startup from your prepared
runtime ZIP, a foreground service, Start/Stop/Restart, TCP readiness, live logs,
world/heap settings, working-copy export and recovery checkpoints. It bundles
source-built Android OpenJDK **17.0.20** and the unchanged handwritten POC JAR.
It includes no proprietary Wurm files and requires no Termux installation.

This is the **next physical-device test**, not a claim that Wurm has already
started or saved successfully inside this APK. The earlier 0.3.2 diagnostic
proved Java/SQLite with a different JRE build. Start repeats Java/SQLite preflight
with 17.0.20 before it opens Wurm. A failed preflight leaves the world unopened.

Install `Wurm-Server.apk` alongside your existing apps; its application ID is
`io.github.russianranger.wurmlauncher.managed`. Keep the earlier import preview,
JVM Test app and original ZIP. Choose the Wurm Server icon showing **0.4.0**.
This development APK uses a CI debug key. Export any working data before a later
upgrade/uninstall; durable signing and live-world migration are future work.

1. Stop the existing Termux server to free TCP 3724. Keep the source runtime stopped.
2. Keep `wurm-runtime-20260908-052948.zip` in Downloads; no new archive is needed.
3. Open the new app, **Import Server ZIP**, select that ZIP, then **Adventure**.
   Allow at least **4 GiB free internal storage** for this approximately 661 MiB runtime.
4. Leave heap **4096 MiB** and expected TCP **3724**. Tap **Start Server** and allow notifications.
5. Look for `PREFLIGHT_PASS`, the POC GameFolder/personal/offline messages, then
   `TCP_READY`. Keep the app open for the first startup. Export the session report.
6. If Running, tap **Stop Server** and wait for exit before testing Start/Restart.
   Do not use Force Stop as a save test. Export the working runtime after stopping.
7. If startup fails, export the report. If recovery is required, export the
   working copy and before-start checkpoint before restoring and retrying.

Stop invokes the detected `Server.shutDown()V` through the handwritten adapter;
missing APIs block startup. Requested exit 0 does **not** independently prove
that Wurm saved correctly. Cancelling incomplete startup sends SIGTERM; Force
Stop kills only the owned child. A crash, forced stop or app-process death keeps
the checkpoint and requires explicit restore before a new Start can overwrite it.

The Client tab retains its import-reference and Settings groundwork. Client
execution is not implemented. Full test/recovery/build instructions and the
complete file-change inventory are in `MANAGED_SERVER_TEST.md`.

The release includes the exact OpenJDK/Android-port/FreeType/CUPS source archives,
Android patch and recipe used for its JRE. License notices also remain in the APK's
JRE data. `SHA256SUMS` identifies all downloadable files.
