# 0.10.36 — isolated mod launcher test

Branch: `mod-launcher-test`, based on stable main `2e41fb091ee75a76b9116e934abecff90bc735d9`.
Package: `io.github.russianranger.wurmlauncher.modtest`. Main and the stable app remain separate.

## First device test

1. Normally stop the stable server. Export its **working runtime ZIP**. Install this test APK alongside it; import that export and your existing client ZIP into the test app. Do not run both servers on the same port.
2. First start/stop once with no mods. This retains the stable entry point.
3. In **Mods → Server**, import [Ago's server-modlauncher-0.47.zip](https://github.com/ago1024/WurmServerModLauncher/releases/download/v0.47/server-modlauncher-0.47.zip). The app takes only its pinned core loader. It does not install desktop scripts, old Javassist, or enable the bundled optional mods.
4. Import [announcer-0.47.zip](https://github.com/ago1024/WurmServerModLauncher/releases/download/v0.47/announcer-0.47.zip). It appears disabled. Turn it on and use **Check server mod files / dependencies**. Start Server, then the ordinary local Client. Check login/logout announcements and normally stop the server.
5. Turn Announcer off while stopped, then restart. Its folder, descriptor and generated config should be in the disabled location; baseline launch resumes when no mods are enabled. Turn it back on to verify config retention.
6. Only after that passes, import [cropmod-0.47.zip](https://github.com/ago1024/WurmServerModLauncher/releases/download/v0.47/cropmod-0.47.zip), enable it alongside Announcer, check, start, and verify both names in the report. Check clean STOP and restart. Farm behavior takes a longer observation than a successful loader message.
7. Export the existing **Server and Client reports from Diagnostics**. They include the mod manifests and startup markers. Mod-only file/dependency checks and manifest viewing are in Mods for now.

The server ZIP export/checkpoint includes active and disabled mods, manifests, configuration, and mod-created files within the runtime. Keep the pre-mod export: disabling code does not reverse database/content changes made by a mod. Ordinary before-start checkpoints advance each run; they are not a permanent pre-mod backup.

Client ZIP imports and toggles are available as a **staging framework only**. Client mod execution is deliberately deferred until server testing passes. The app does not claim a staged client mod is running. Baseline client startup and controls are retained.

## Storage and activation

Each side has its own manifest inside its imported runtime:

- `android-mods/manifest.properties`: side, mod name/version, state, effective startup metadata, owned file paths and SHA-256 values.
- `mods/name.properties`, `mods/name.config`, `mods/name/**`: active/staged files in the standard upstream layout.
- `android-mods/disabled/name/`: the same relative files when off, outside Ago's discovery path. Renaming just a descriptor would not reliably disable JAR-discovered mods.
- `android-mods/transaction.properties`: durable move journal. A stopped check/import/toggle/start completes an interrupted transaction before another operation. Ambiguous source/destination state produces an error.
- `android-mods/loader/modlauncher.jar`: server loader core, isolated from game files.

Import accepts a `mods/` ZIP, its contents, and enclosing release directories. Initial supported mods have a root `name.properties`, JAR classpath under `name/`, and an entry class declaring the corresponding WurmServerMod/WurmClientMod interface. Multiple mod directories can be imported together; each gets its own switch. Duplicate names/unmanaged destinations are rejected without replacing files. Updates/removal/config editing UI are deferred; an existing name is preserved, not overwritten by a second ZIP.

Effective metadata follows upstream precedence: JAR META-INF defaults, root `.properties`, then `.config` (or the JAR's generated config template). Required/imported dependencies and declared conflicts are checked before toggles/start. Ago performs its complete version/order resolution during startup. On-demand support modules may only initialize when another selected mod needs them. `SERVER_MOD_READY` identifies actually loaded mods, rather than only selected files.

Each side uses its existing session ownership and file/native-process lock. Server recovery must be resolved before mod edits. Checks hash code JARs and startup descriptors; ordinary generated settings/data inside the owned directory are preserved and inventoried at the next toggle. Changes to structural keys (entry point, classpath, loader mode, dependency rules) need a reviewed reimport/migration rather than silently changing selected code.

Initial import limits: 100 mods per side, 10,000 ZIP entries and 512 MiB expanded content, with 64 MiB free-space reserve. Desktop native libraries/executables, shared-classloader mods, legacy/indirect-only interfaces and ScriptRunner are deferred for compatibility work. Some upstream support modules are only distributed inside the complete loader bundle; those need an individually prepared mod ZIP/review before this importer can accept them. This is a tested starting framework, not a claim that arbitrary community mods work on Android.

## Runtime integration

Server launcher 0.47 is user-imported, SHA-256 `44f7c9adc2dfbe2de45d7bc22a6ef8448549cde3f1f164b5c59a094948e6bacf`.
The APK includes unmodified Javassist 3.30.2-GA as a child-JVM asset, SHA-256 `eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf`. It is not an Android/D8 dependency. Its MPL 1.1 notice/license and corresponding Maven source JAR accompany the release.

With server mods enabled, `ServerModBootstrap` obtains Ago's transforming loader before any Wurm classes resolve. `ServerModLaunch` performs mod discovery/initialization, attaches lifecycle listeners, initializes callbacks, then invokes the existing Android `ManagedServerMain` and unchanged POC. SQLite drivers and console evidence stay parent-loaded; Wurm classes, POC and Android game entry remain in the transforming loader. The existing SQLite/login overlays precede the originals. Failed mod initialization exits with an error and never silently opens a modded world without its selected mods.

Readiness markers: `SERVER_SELECTION`, `SERVER_LOADER_BEGIN`, `SERVER_MOD_READY <name>`, `SERVER_LOADER_READY`, followed by normal Android/server readiness. `SERVER_LOADER_FAILED` is a failed attempt. `CLIENT_LOADER_DEFERRED` identifies staged client mods on an ordinary client run.

No changes are made to native libraries, graphics/audio settings, memory collectors or default resolution.

## Validation and limits

Host tests cover file movement and interrupted transactions, generated config/data preservation, side checks, metadata precedence, dependency/conflict handling, duplicate imports, modified/untracked code, loader requirements and export/restore of manifests. A Java 17 test uses the actual pinned Ago discovery/resolver/classloader and Javassist to load two authored mods, apply both bytecode changes before game-fixture loading, retain driver/diagnostic class identity, and stop on initialization failure. The Wurm-specific lifecycle implementation is an authored fixture in that test: it does not prove real server hooks, real mod behavior or Android compatibility. Physical-device testing is required above.

Client execution, live hot reload, automatic downloads, mod updates/removal, mod-specific configuration editors, native mods, paired server/client assets and database migration rollback are outside this initial pass.
