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
