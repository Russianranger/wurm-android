# Wurm Unlimited ARM64 Android POC

This directory contains the small Java proof-of-concept layer used to launch the Wurm Unlimited dedicated server on ARM64 Android/Termux without the original native Steam server library.

## What is here

- `src/poc/AndroidServerMain.java` — Android/Termux launcher entry point. It selects the Wurm world directory, configures Wurm's `GameFolder`, starts the server with personal and offline mode enabled, and keeps the JVM alive for the server threads.
- `src/SteamJni/SteamServerApi.java` — Java shim that replaces the native Steam server JNI entry points used by Wurm Unlimited. For the offline ARM64 POC it simulates a successful Steam game-server connection/authentication path.
- `artifacts/wurm-arm64-poc.jar.base64` — base64 representation of the current compiled `wurm-arm64-poc.jar`. It is stored this way because the GitHub connector used to add these files can write UTF-8 text but not raw binary files.

## Reconstruct the current JAR

From the repository root on Linux/Termux:

```bash
base64 -d poc/artifacts/wurm-arm64-poc.jar.base64 > wurm-arm64-poc.jar
```

Verify the expected classes:

```bash
jar tf wurm-arm64-poc.jar | grep -E 'AndroidServerMain|SteamServerApi'
```

Expected entries include:

```text
SteamJni/SteamServerApi.class
poc/AndroidServerMain.class
```

## Rebuild the JAR from source

The POC source compiles against Wurm Unlimited's existing server classes. The proprietary Wurm files are not included in this repository; provide your own legally obtained `server.jar` and `common.jar` in the working directory.

A deterministic rebuild packages exactly the two authored classes and a manifest:

```bash
python3 scripts/build-poc.py --classpath /absolute/path/server.jar \
  --output poc/artifacts/wurm-arm64-poc.jar.base64
```

The inspected server JAR contains the compile-time types required here. A different
layout may need `server.jar:common.jar` as its classpath. After a deliberate source
change, update the printed artifact/source pins in `app/build.gradle.kts` and the
artifact pin in `ManagedRuntimeStore.kt`. Public CI decodes and verifies the artifact
without importing proprietary dependencies. The builder never packages them.

## Original Termux runtime invocation

The working POC was launched with a classpath shaped like this:

```bash
java \
  -Xms512m \
  -Xmx4g \
  -Djava.awt.headless=true \
  -cp "wurm-arm64-poc.jar:poc-lib/sqlite-jdbc-3.53.2.1.jar:poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar:server.jar:common.jar:lib/*" \
  poc.AndroidServerMain Adventure
```

The exact bundled runtime should be treated as an implementation detail of the future Android app. The app should ultimately manage the required runtime files, world directory, JVM launch arguments, server lifecycle, logs, and configuration itself rather than requiring the user to type this command.

## Personal/offline startup contract (corrected in 0.10.15)

```java
launcher.runServer(true, true);
```

The first argument sets personal-server mode and the second enables offline mode.
The original launcher overwrites both flags before starting the server. Earlier
versions set personal mode true first but then passed `false, true`, disabling it
and preventing first-time character creation. That pre-start log was misleading.
The POC now reports `SERVER_MODE_ACTIVE personal=true` after runServer returns and
fails if the mode is false. The original Wurm server handles player creation and
the in-game setup prompt; device login/world/persistence acceptance remains pending.
See [the investigation and Thor procedure](../docs/SERVER_PERSONAL_MODE_FIX.md).

The Steam shim reports a synthetic successful game-server connection rather than loading the desktop Steam native server library.

During the ARM64/Android bring-up, MySQL-specific `ON DUPLICATE KEY UPDATE` SQL in Wurm's item database string implementations also had to be replaced with SQLite-compatible update paths. Those patches are separate from this POC JAR and should be preserved/integrated by the Android packaging work rather than regressed.

## Goal for the Android project

The purpose of this repository is to turn the proven Termux POC into a normal Android application that can set up and launch a local Wurm Unlimited server with minimal manual work. The intended direction is:

1. Android UI for selecting/importing the legally obtained Wurm Unlimited server files and a world.
2. App-managed ARM64 Java runtime/server process.
3. Automatic installation or generation of the POC/shim classes and SQLite compatibility patches.
4. Start, stop, restart, status, and log viewing from the Android UI.
5. Configuration editing for world/server settings without requiring Termux.
6. Local/LAN connectivity to the server, preserving the already demonstrated TCP listener behavior.
7. Keep proprietary Wurm assets and JARs out of this public repository; import them from the user's own installation at runtime.

## Notes for Codex

Do not treat `wurm-arm64-poc.jar` as an opaque missing dependency. Its complete handwritten source is in `poc/src/`, and the current compiled POC artifact can be reconstructed from `poc/artifacts/wurm-arm64-poc.jar.base64`.

Before changing the architecture, preserve the behavior demonstrated by the POC: Android ARM64/Termux can start the Wurm server far enough to create/load its SQLite databases, initialize the Steam shim, run offline (personal mode was corrected in 0.10.15), keep the JVM/server threads alive, and bind the game TCP listener. Future work should move that behavior behind the Android application's lifecycle and UI rather than replacing the proven path unnecessarily.
