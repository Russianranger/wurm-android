# Wurm Server 0.9.1 — JVM graphics test

0.9.0 on the Thor successfully loaded the native libraries, created the Adreno
context and linked the shaders, then failed at an aggregate draw/readback check.
This release fixes an empty-shader-log request rejected by GL4ES, reproduced on
host Mesa, and adds operation/error-code diagnostics plus the failure reason in
app status. The corrected host test passes; the Thor must confirm its outcome.
It is not yet established that the log bug explains the Thor's final error.

Test the actual LWJGL/Pojav ARM64 JNI bindings and GL4ES desktop OpenGL translation
inside the APK's managed Java 17 process. Draw and verify a GLSL 1.20 triangle,
resize the EGL pbuffer, display the child-produced frame and export diagnostics.
**Wurm window creation, gameplay input, login and world entry are not working yet.**

Install `Wurm-Server.apk` alongside 0.6.0, 0.8.0 and 0.9.0; it uses `.graphicsfix1`.
No game import, PC, Termux, root or separate Java install is needed for this test.

1. Open **0.9.1 → Client tab → JVM Graphics Test → Run Graphics Test**.
2. Expect an orange triangle on blue and **Graphics test passed**, or an explicit
   failure. Wait for the operation to end.
3. If it passes, run it once more to test a fresh JVM/context. Close/reopen the app.
4. **Client tab → Export Client Report → wurm-client-report.txt**. Return that
   report even if no image appears. Keep the working server and its world.

See bundled `GRAPHICS_THOR_TEST.md` for exact acceptance markers and scope. A pbuffer
pass is not a Wurm frame or proof of an Android game-window integration. The current
Start Client/Start Local Game path remains instrumented but still needs that bridge.

The build uses pinned public LWJGL, GL4ES and libffi sources. Notices, hashes and
`Graphics-corresponding-source.tar.gz` accompany the APK, alongside the unchanged
managed Java runtime's corresponding source. No proprietary Wurm files are included.
The preview is debug-signed and installed under a distinct package; earlier app
data is not migrated or replaced. GitHub publishes this tag once after required
build, tests, lint, package identity and signing verification.
