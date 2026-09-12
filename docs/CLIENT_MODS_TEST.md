# 0.10.38 — client loader and Live Map test

Branch: `mod-launcher-test`. Separate package:
`io.github.russianranger.wurmlauncher.clientmods`, versionCode 52.
Main and the preceding apps remain separate. No desktop launcher is needed.

## Device evidence leading to this release

The user reports server mods working. Reports `wurm-server-report (1)(7).txt`
and `wurm-client-report (1)(9).txt` show Announcer re-enabled at 19:22:11Z on
2026-09-12, followed by normal save/exit 0 after 15m47.8s. At 19:44:04Z both
Announcer and Survival emit `SERVER_MOD_READY`; loader count is 2. The server
is still running when exported at 19:48:21Z, so that last report does not yet
establish a normal shutdown of the combined session. The retained client
entry exits are 0. Survival replaces the earlier proposed CropMod test.

## Downloads

- [Test APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.38-client-mods/Wurm-Server.apk)
- [Ago client launcher 0.15 ZIP](https://github.com/ago1024/WurmClientModLauncher/releases/download/v0.15/client-modlauncher-0.15.zip)
- [Live Map 1.8 ZIP](https://github.com/ago1024/LiveHudMap/releases/download/v1.8/livemap-1.8.zip)

Import these ZIPs directly through the app. The loader import selects only its
core JAR; desktop patcher, old Javassist and bundled optional mods are not
installed. The app already supplies Javassist 3.30.2-GA. Live Map is a client
mod and requires no server-side Live Map component for this test.

## Device checklist

1. Normally stop the 0.10.37 client and server. Export the **working server
   runtime ZIP** so the current world, loader, Announcer, Survival and their
   configurations transfer together. Keep 0.10.37 installed.
2. Install 0.10.38 alongside it. Import that working server export and your
   normal client ZIP. New app preferences start at their defaults, including
   1280x720. Only start one local server on port 3724.
3. In **Mods → Client mods**, import `client-modlauncher-0.15.zip`. Its **Use
   client mod loader** switch starts on. With no client mods enabled, run the
   mod check, start the server and local client, enter the world, and stop the
   client normally. The report should include `CLIENT_LOADER_READY count=0`.
4. With the client stopped, import `livemap-1.8.zip` using the same Client mods
   import button. Turn on `livemap`, run the check, and start the client again.
   Expect `CLIENT_MOD_READY livemap`, loader count 1, and a visible map. The
   mod adds **Live map** to Wurm's in-game menu; its console command is
   `toggle livemap` if the window is hidden.
5. Check map movement, buttons, zoom and hide/show at 1280x720, then logout and
   re-enter. Use the default flat/lower-resolution map first. Test other map
   modes only after the first run works; watch rendering and memory overhead.
6. Stop the client, turn `livemap` off, and restart: its window should be
   absent. To return to the original client path, first turn off all client
   mods, then turn off **Use client mod loader**. No reinstall is required.
7. Export both Client and Server reports from **Diagnostics**, including any
   failed attempt. Mod tools stay in **Mods** for this test.

The launcher switch applies on the next client start. Imported mods start off;
enabled client mods require the loader to be installed and enabled. Client
operations use the existing stopped-session and native-process locks.
Disabling a mod preserves its owned config/data. It does not reverse gameplay
changes a server mod has already made to a world.

## Implementation and scope

`ModStore` now accepts the exact client loader independently of the server
loader and allows shared class loading on the client side. Import, integrity
checks, dependencies, journals, disabled folders and configuration retention
continue to apply. Server shared-classloader imports remain outside this pass.

`ClientSession` adds loader/Javassist only to the real game-entry process;
inventory, compatibility and graphics-preparation probes keep their original
classpaths. Android graphics and compatibility overlays precede imported game
JARs. `ClientModBootstrap` creates Ago's transforming loader before any game,
graphics bridge or LWJGL/JNI owner classes load. All of those stay together in
one loader; JDK classes and HookManager/Javassist use their normal shared owner.
This avoids a second game/graphics state in the app parent classloader.

`ClientModLaunch` verifies Android overlay selection, calls the unmodified
client ModLoader (including its ModComm, ModConsole, ModClient and ModPacks
hooks), initializes callbacks, records each initialized mod, and enters the
existing Android client bootstrap. It never invokes the upstream desktop
launcher or patches the imported client JAR. Mod initialization failure returns
42 and cannot silently enter an unmodded game.

Client/server mod selection and readiness markers now survive console rotation
in the bounded observation history. Client reports distinguish installed loader,
its switch and selected mods; startup stage markers identify where a failure
occurred. A `CLIENT_MOD_READY` marker alone does not prove visible map rendering.

Live Map 1.8 uses shared loading to add classes in Wurm's GUI package. Its JAR
also contains its button images and a resource-lookup fallback for the older
shared loader. The original JAR/config are retained. `hiResMap=false` is its
default. The client loader and Live Map have not yet been tested on the Thor
with this APK; other client mods remain unqualified.

## Public dependency identity

| File | SHA-256 |
| --- | --- |
| client-modlauncher-0.15.zip | `9d19936c8a35bc3c557caba0474b6945bb7bbadd126e23fc133a0accbf6fbb49` |
| Extracted client modlauncher.jar | `c82b57c119f7b73b57310b3a9ee1114d7eed6ea0b60b5978e35e2749f76d8a72` |
| livemap-1.8.zip | `fc3f964e26c173f88b9edc0c5714f0e46061431ed17a560f9223b37436c3ee19` |
| livemap.jar | `4211e55cc6c491914e95f519c02b81ca5554e3d3252039b352353eb967b32441` |

Upstream source: [client loader 0.15](https://github.com/ago1024/WurmClientModLauncher/tree/v0.15),
[Live Map 1.8](https://github.com/ago1024/LiveHudMap/tree/v1.8).
User-imported mod/loader binaries and proprietary game inputs are not committed.

## Validation

Four Java tests use the actual pinned client loader/Javassist and authored game
ABI fixtures. They exercise the loader's real support hooks, shared GUI-package
access, a second isolated mod, transformation order above an Android-like
overlay, single graphics/game class identity, failure without fallback, and
rejection of diagnostic stages. They do not simulate the complete Wurm engine
or demonstrate native Android rendering.

Kotlin tests cover the actual Live Map ZIP import, generated config retention,
disable/re-enable/copy, side-specific loader pins, ignoring bundled mods,
switch prerequisites, and retained report markers. Existing server-loader
regressions remain required. CI performs host tests, all Android variant
build/unit/lint gates, required native/actual-LWJGL checks and APK verification.
