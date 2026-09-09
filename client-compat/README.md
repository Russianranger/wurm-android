# Handwritten local client compatibility asset

This directory contains independently authored compatibility implementations and
API-only signatures inspected from the user's legally supplied client. It does
not contain the game JAR, disassembly, decompiled method bodies, game assets or
Steam native libraries. No proprietary dependency is required to compile the APK.

`compileClientCompat` compiles src and stubs; `packageClientCompat` selects only:

- `SteamJni/Steam_api.class`
- `com/wurmonline/client/launcherfx/WurmMain.class` and its authored listener `$1`
- `wurm/android/compat/LocalSession.class`
- `wurm/android/compat/ClientHooks.class`
- `com/wurmonline/client/launcherfx/WurmSettingsFX.class`
- `wurm/android/compat/KeybindStore.class`

The APK verification script enforces that exact set. **Never package stubs.**
The real console, SteamHandler, its result enum and SteamAuthTicket implementations
must come from the user import. Typed ClientHooks calls avoid `getMethod` on the
original SteamHandler, which resolves its unused JavaFX browser parameter types.

Only client compat/entry subprocesses prepend this asset to the classpath. The
server POC and original server SteamServerApi shim are independent and unchanged.
`runtime-probe.jar` contains the reflection-based direct launch adapter, without
these shadow classes or a compile-time Wurm dependency.

LocalSession requires explicit offline=true and 127.0.0.1:3724. A synthetic identity
persists in Java user home's wurm-local-identity.txt, outside the import generation.
Ticket data is an explicit local marker plus identity, wrapped in the real
SteamAuthTicket via its observed (long,byte[],long) constructor. It is not a Steam
credential; whether the personal server accepts it needs a real connection test.
Invalid persisted identity fails visibly instead of replacing an account identity.
Browsing is unavailable and fails explicitly. Stats/achievements are not synced.

The WurmMain replacement does not extend JavaFX Application. It exposes only the
utility contract used by the engine: imported flags, console/logging, image path,
local address and port. It does not reproduce the launcher or modify gameplay.
The real profile/resources factories and game-thread lifetime remain in the
imported client. Native LWJGL can initialize during profile construction itself.

Run `python3 -m unittest discover -s tests -p 'test_client*.py' -v` with Java 17.
These are authored contract fixtures, including an intentionally missing desktop
parameter type, asynchronous native failure, local identity continuity, pack
validation and no-stub packaging. The CI APK check validates the actual Gradle
output. Real-JAR compatibility was also tested privately; no game file is an
input to the public test suite.

0.10.1 adds the headless WurmSettingsFX keybinding facade. The Thor reached
Profile.loadSettings, which otherwise loads WurmStage/JavaFX Stage. The new facade
retains file loading, lookup, remapping and atomic persistence without that UI
hierarchy. Typed Profile/Options/MultiOption signatures are compile-only and never
packaged. The real Profile, Options and game input/console remain imported.
Unchanged bindings retain identical bytes; malformed inputs and unsafe overwrites
fail visibly. JavaFX settings dialogs/restart callbacks remain outside this helper.
See [the real-JAR evidence and exact device test](../docs/CLIENT_SETTINGS_FIX.md).
