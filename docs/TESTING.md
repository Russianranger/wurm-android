# Milestone 1 verification

Automated compilation cannot establish root/SELinux compatibility or confirm
that the Wurm POC saves a world correctly. Test on a **backed-up test world**.

## Automated

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 -m unittest discover -s tests -v
```

Use `bash scripts/build-termux.sh ...` for Gradle on Termux. Python shell tests
are a Linux host harness with a fake Java executable, not a real server test.

## Rooted Android 13 ARM64 device

- [ ] With manual server stopped, correct paths and root granted: Start produces
  live output, a JVM PID, then TCP 3724 accepting connections.
- [ ] Compare command line/world load against the original Termux POC.
- [ ] Rapid repeated Start does not create duplicate JVMs.
- [ ] Launch while manual TCP 3724 server is listening: rejected; manual server
  remains untouched. Do not deliberately race starts on a live world.
- [ ] Rotate/reopen Activity, press Home, then return: same process/logs/status.
- [ ] Screen off briefly: foreground notification and process remain.
- [ ] Scroll up in logs: auto-scroll pauses until back at the bottom.
- [ ] Stop in app and notification: SIGTERM reaches only the managed child,
  logs report its exit, TCP 3724 closes, notification goes away, wake lock releases.
- [ ] Verify Wurm saves correctly on SIGTERM; reopen the world and inspect saves
  and SQLite health with the existing POC's tools. Do not assume signal delivery
  alone proves an application-level clean save.
- [ ] Start → Stop while root permission is pending; approve late: no persistent
  server starts. Denied root, no `su`, wrong Java, missing JAR, invalid path and
  locked runtime produce a useful error and leave the UI usable.
- [ ] A runtime path containing spaces/apostrophes works with a test copy.
- [ ] Ordinary app-process death: check whether root supervisor sees pipe EOF and
  terminates JVM. Separately test force-stop on disposable data; document any
  orphan. Never assume force-stop will run Activity/Service cleanup callbacks.
- [ ] JVM exits unexpectedly: state leaves Running and foreground service ends.
- [ ] Server that ignores SIGTERM: state remains Stopping; no automatic SIGKILL.
- [ ] Check ownership of new world/log files before returning to non-root Termux.

No physical-device or actual Wurm world-save checks have been performed by the
repository authoring environment. Record device/root-manager-specific results
before considering this ready for valuable worlds.
# Scope of this checklist

This is the retained **rooted server regression** checklist. Open it through
**Wurm Server → Server → Open rooted POC controls and live logs**. The new
no-root import-preview test is documented in [THOR_IMPORT_TEST.md](THOR_IMPORT_TEST.md).
The managed world selection does not affect the rooted screen's Adventure launch.
