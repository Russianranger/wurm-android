Wurm Server 0.2.0 — import preview for Android 13+ ARM64.

Download and install **Wurm-Server.apk**. This preview imports a prepared,
legally obtained Wurm Unlimited server runtime ZIP without root or Termux,
installs the source-backed Java 17 POC JAR, discovers worlds, saves a world
selection and exports a diagnostic report. It also adds Client import-reference
and Settings groundwork, and retains the existing rooted server controls/logs.

**This APK does not contain an embedded JVM. It cannot yet start the imported
server without root/Termux.** Managed server controls and Client Start are
explicitly disabled. No proprietary Wurm files are included. Import preserves
the user's already-patched SQLite runtime files; it does not patch stock files.

Follow the [exact AYN Thor test steps](https://github.com/Russianranger/wurm-android/blob/main/docs/THOR_IMPORT_TEST.md)
and read the [implementation plan](https://github.com/Russianranger/wurm-android/blob/main/docs/IMPLEMENTATION_PLAN.md).

This is a CI-generated debug-signed development APK. Its signing key is not a
stable production/update key. If it conflicts with an earlier APK, retain your
external ZIP/backup before uninstalling the older app; uninstall erases private
imports and settings. A durable release signing key is required before managed
worlds become live data. `SHA256SUMS` is provided to check the downloaded APK.
