# Wurm Server 0.7.0 — client bootstrap and controller preview

Download **Wurm-Server.apk** on the AYN Thor. No root, Termux or PC is required.
Keep the working 0.6.0 app installed: this preview uses the separate
`io.github.russianranger.wurmlauncher.clientpreview` package and does not migrate
or overwrite its world. The app label remains **Wurm Server**.

This release adds managed client ZIP import, actual Java client class/launch
linkage attempts, an independent LWJGL2 startup attempt, local TCP readiness,
editable handheld controller mappings, Android GLES surface diagnostics and a
JVM input receiver. Export **wurm-client-report.txt** from the Client tab.

**This is not yet a playable client.** The exact imported `launch(...)` ABI,
client Steam adapter and LWJGL/native graphics bridge still need qualification.
The preview logs these blockers rather than disabling Start. Controller events
currently reach a diagnostic JVM sink, not Wurm gameplay. A reachable local
server is not reported as a successful Wurm login.

Import a ZIP of your complete legally obtained client installation, including
`client.jar`, `common.jar`, all `lib/` JARs and every client asset/resource folder.
The prior server ZIP is not sufficient. No proprietary Wurm files are included.

Follow **CLIENT_THOR_TEST.md** in the release assets for the exact test, including
using your existing 0.6.0 server at `127.0.0.1:3724` without migrating it.
**CLIENT_INTEGRATION.md** records the gate status, reusable Pojav/LWJGLX/GL4ES
investigation and every changed file. SHA256SUMS and the corresponding runtime
source bundle accompany the APK. Publication requires all build/test/lint and
runtime/POC/APK checks to pass; physical client acceptance is still pending.
