# Wurm Server 0.8.0 — direct client launch and automatic controller test

Download **Wurm-Server.apk** on the AYN Thor. No root, Termux or PC is required.
Keep the working 0.6.0 server installed: this preview installs alongside it as
`io.github.russianranger.wurmlauncher.clientlaunch`, version **0.8.0-managed-preview**.
The label remains **Wurm Server**. The new package needs its own client import.

**Client tab → Start Controller Test** now starts the JVM receiver automatically.
Wait for READY, test the controls, stop the receiver and export Client Report.
This test needs no Wurm files and has visible touch Exit/Stop/Retry controls.

The new source-built adapter uses the verified real player-profile/resources
launch API, replaces the JavaFX launcher's required utility methods and supplies
a local-only Steam compatibility shim. The actual user's SteamHandler and
SteamAuthTicket passed the compatibility check on the development host. A new
compat stage and detailed entry/thread logs expose failures on the Thor.

**This is not yet playable.** Real profile preparation reaches LWJGL native
loading before game launch. An Android LWJGL/window/OpenGL bridge is still needed.
Synthetic-ticket acceptance by the local server, Wurm login/world entry and
controller gameplay remain unverified. Input currently reaches a JVM diagnostic.

For the client run, import the same complete legally obtained client ZIP including
client.jar, common.jar, lib, packs and every original resource folder. Use the
old app's running server at 127.0.0.1:3724; world migration is unnecessary.
No proprietary game files, compile-only API stubs or desktop native binaries are
bundled as client implementations. No server POC/SQL/runtime changes are included.

Follow **CLIENT_THOR_TEST.md** for exact steps and export **wurm-client-report.txt**.
**CLIENT_INTEGRATION.md** records every change, gate status and the Pojav reuse path.
APK publication requires the existing build/test/lint/runtime checks plus the new
compatibility contract tests and an exact adapter-class packaging check.
SHA256SUMS and corresponding runtime source accompany the APK.
