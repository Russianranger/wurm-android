**0.3.1 fixes the launcher path that caused the Thor's `trying to exec .../bin/java`
failure in 0.3.0.** The JVM library directory now comes first in the child's
library search path, avoiding OpenJDK's environment-change re-exec. A real host
JDK 17 regression test covers both path orders. The next Thor run must establish
whether Android Java and SQLite now pass; that result is still pending.

Install **Wurm-Server-JVM-Test.apk** alongside the existing Wurm Server app.
This separate diagnostic package preserves your existing imported Adventure
world and tests an embedded Android ARM64 JVM plus SQLite without root/Termux.

Open **Wurm Server JVM Test**, select the same prepared runtime ZIP, keep the app
open, and export `wurm-jvm-probe-report.txt`. Only the two checksum-verified SQLite
JARs are extracted. The test uses a disposable database and a 90-second JVM
timeout. It does not start Wurm or touch world databases.

The candidate runtime is **Android OpenJDK 17.0.10**, distinct from the verified
Termux baseline **17.0.20**. It is an older compatibility-test candidate, not the
final server runtime. No proprietary Wurm files are bundled. Upstream runtime
notices are included in the APK; source archives accompany this release.

[Exact Thor steps and expected output](https://github.com/Russianranger/wurm-android/blob/main/docs/JVM_PROBE_TEST.md)
· [Runtime provenance](https://github.com/Russianranger/wurm-android/blob/main/docs/RUNTIME_PROVENANCE.md)

This APK is debug-signed and uses package ID
`io.github.russianranger.wurmlauncher.jvmprobe`. Keep the original Wurm Server
app installed. `SHA256SUMS` covers the APK and companion source archives.
If Android rejects the update due to a different signing key, uninstall
**Wurm Server JVM Test only**, then install this APK. The original Wurm Server
app and its Adventure import use a different package and should be kept.

The new report starts with app version **0.3.1-jvm-probe** and includes JLI
tracing. Look for `mustsetenv: FALSE`, then `JAVA_OK`, `SQLITE_OK`, `PROBE_OK`
and `RESULT: PASS`. Export the report even if another failure appears.
