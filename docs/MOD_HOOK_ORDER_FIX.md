# 0.10.37 — preserve mod hook installation order

Branch: `mod-launcher-test`. Package: `io.github.russianranger.wurmlauncher.modhookfix`, versionCode 51. Stable main remains separate.

## Device report and cause

`wurm-server-report(20260912-183958).txt` reports 0.10.36 with Ago 0.47 and Announcer enabled. SQLite preflight passes. At 18:38:53.013Z Announcer's JAR is selected, its configuration is read, and the loader creates mod callbacks. At 18:38:53.314Z `ProxyServerHook.registerOnMessageHook` fails with `com.wurmonline.server.creatures.Communicator class is frozen`. The app records exit code 1 after 604 ms of the server child. There is no normal Android game-entry/readiness marker, out-of-memory report, or native crash signature for this attempt.

The fault is in our bridge's reflective `ServerHook.getMethod("createServerHook")` call. Java's reflective method search resolves unrelated method signatures on that class. Its public event methods mention Wurm's Communicator and other game classes. Ago's transforming classloader consequently defines/freezes Communicator before the factory gets to finish instrumenting it. Upstream's directly linked launcher does not perform that reflective scan.

0.10.37 uses `MethodHandles` with the exact factory/listener method descriptors. This lets hook installation finish before the game types resolve. It does not defrost an already defined class, bypass hook failures, or alter the imported mod JAR. Added bounded `SERVER_LOADER_STAGE` lines distinguish mod initialization, server hook installation, callback initialization and Android entry.

## Regression evidence

The Java 17 integration fixture now includes an unrelated public ServerHook method whose parameter is Communicator, plus a hook that modifies that class. The original 0.10.36 bridge reproduces the same frozen-class exception. The corrected bridge passes and the test verifies the hook's actual effect, alongside both mod transformations and dependency ordering. The initialization-failure/no-vanilla-fallback test still passes. The pinned real Ago classloader/discovery/resolver and Javassist are used; Wurm game classes and lifecycle code remain authored fixtures. Actual device startup must still be tested.

## Retest on Thor

1. In **0.10.36 → Server**, choose **Export before-start checkpoint ZIP**. This report confirms that a checkpoint was retained. It contains the world, installed loader, Announcer and its enabled manifest state from before the failed attempt. Keep 0.10.36 installed for now.
2. Install **0.10.37** alongside it. This uses a separate package because these test APKs have independently generated debug signing keys; it cannot safely replace the previous installation in place.
3. In **0.10.37 → Server → Import Server ZIP**, select that checkpoint ZIP. Import your normal client ZIP if you want to test login afterward.
4. In **Mods**, confirm Announcer is listed and enabled, then use **Check server mod files / dependencies**. The checkpoint carries loader/mod files; they do not need separate reimports. If you instead use a clean stable runtime export, import the same loader 0.47 and Announcer 0.47 ZIPs as before.
5. Start Server with Announcer alone. If it reaches Running, start the local client, test login, then normally Stop Server. Export the Server report from **Diagnostics**; include the Client report if you started it. If startup fails, export the Server report without restoring/retrying repeatedly.

Do not add CropMod until this startup/clean-stop check passes. Client mod execution remains deferred. Mod storage, toggles, native graphics/audio, Java runtime, memory policies and server SQLite/login compatibility patches are retained.

[Mod import layout and original checklist](MOD_LAUNCHER_TEST.md). [Maintained handoff](HANDOFF.md).
