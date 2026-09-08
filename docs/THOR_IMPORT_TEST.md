# AYN Thor: import-preview test

This APK tests **no-root import and storage**, world selection, POC installation
and the client UI groundwork. It does not yet include an embedded JVM and cannot
start the imported server. The existing rooted launcher is available separately.

## Exactly what to install and copy

1. Download `Wurm-Server.apk` from the repository's
   [v0.2.0 import preview release](https://github.com/Russianranger/wurm-android/releases/tag/v0.2.0-import-preview)
   and install it on the Thor. Enable Android's "Install unknown apps" permission
   for the browser/file manager if prompted. The app is named **Wurm Server**;
   package ID remains `io.github.russianranger.wurmlauncher`, minimum Android 13.
   This is a CI debug-signed preview. If Android reports a signature conflict with
   an older CI APK, keep your external runtime backup, uninstall that older app
   and install this APK. Uninstalling erases that app's private imports/settings.
2. Prepare `wurm-runtime.zip`, a complete copy of the **stopped, previously working
   and SQLite-patched** runtime. Put this ZIP in the Thor's **Downloads** folder.
   Keep your original runtime and backup. Do not create a ZIP of live databases.
   Copying this existing Termux-private runtime is a one-time preparation step;
   the APK's import itself needs neither Termux nor root. You may instead make
   the ZIP on a computer from a stopped copy you already have.
3. No Java executable, root grant, client files or separate POC installation is
   needed for this import test. Do not copy files into Android/data manually.

The ZIP must contain these paths at its root, or all inside one `runtime/` folder:

| Path | What to include |
| --- | --- |
| `server.jar`, `common.jar` | Your legally obtained, working POC versions, including existing patches |
| `lib/` | The complete original directory, including any compatibility JARs |
| `poc-lib/sqlite-jdbc-3.53.2.1.jar` | The working JDBC JAR |
| `poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar` | The working Android SQLite native JAR |
| `Adventure/` (and other worlds) | Complete stopped world/configuration/database directories |
| Other runtime resources/overlays | Everything else from the working runtime; do not trim the copy |
| `wurm-arm64-poc.jar` | Optional: app supplies it; if present it must match the tracked POC |

Expected POC SHA-256:
`0fe4039a1a06afae93099b6eaf140e04fe7e0b1f1145323116468f8f78a884fe`.
If import reports a different POC, do not replace files in your working runtime.
Compare the versions first. Only omit the older POC from a separate export copy
if you intend to use the source-backed version included in this app.

Example one-time export **after stopping the manual/rooted server**, in Termux:

```bash
termux-setup-storage
pkg install zip
cd /data/data/com.termux/files/home/wurm-arm64-poc/runtime
archive="$HOME/storage/downloads/wurm-runtime-$(date +%Y%m%d-%H%M%S).zip"
zip -r "$archive" .
sha256sum server.jar common.jar poc-lib/*.jar
```

This command reads your existing runtime. If it fails with unreadable root-owned
files, do not import the incomplete ZIP; use a complete readable stopped backup
or your existing root file manager to export a copy. Never change ownership of
the only working copy merely to complete this test. Ensure free **internal**
storage exceeds the extracted runtime plus 64 MiB. Replacing an import temporarily
needs space for both old and new extracted copies, in addition to the source ZIP.
The preview caps an import at 32 GiB / 100,000 ZIP entries.

## What to run and observe

1. Open **Wurm Server → Server → Import Server ZIP → Choose ZIP**. Select the ZIP
   from Downloads. No root dialog or broad storage permission should appear.
2. Keep the app visible until **Import complete**. Rotating it should retain the
   import progress. World candidates should include Adventure. If copying fails,
   record the error; the prior successfully imported copy remains selected.
3. Select Adventure (or another listed world). Export `wurm-import-report.txt`
   to Downloads. Compare its server/common/SQLite hashes with the source hashes.
   Close/reopen the app and verify the import and selected world remain.
4. Confirm the managed Start/Stop/Restart buttons are disabled with the embedded
   Java explanation. This is the expected result, not a failed server startup.
5. Open **Client**. Select a client ZIP if you already have one, open **Settings**,
   save host/port, and reopen Settings to check persistence. Client Start is
   disabled; selection stores a document reference without extracting the ZIP.
6. Optional failure tests on copies: import a ZIP missing the Android SQLite
   natives JAR, then try a truncated ZIP. Both should report failure and preserve
   the previous import. For interruption testing, begin a second large import,
   force-stop the app via Android Settings, reopen it and verify the prior import
   remains. Retry the import to clean incomplete staging data.

Send back the exported report, Thor Android version, approximate ZIP/extracted
size, any import error and whether selection survives reopening. The report
contains file/world names and JAR hashes, but no INI values or database contents.
Do not upload your proprietary runtime ZIP to this repository.

## Optional existing server regression test

Use **Open rooted POC controls and live logs** only if you want to rerun the
already-supported root/Termux route. That screen uses its saved Termux runtime
and Java path, always starts Adventure, and does not read the new managed import.
Follow [TESTING.md](TESTING.md), including stopped backups, root authorization,
TCP 3724 checks and shutdown verification. This preview preserves the original
command and SQLite-patched files. A physical server run under the ordinary app
UID will follow the embedded JVM milestone; no shell command can enable it here.

## Developer checks

```bash
python3 -m unittest discover -s tests -v
bash ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

CI checks import byte preservation, unsafe paths, truncated imports, required
files, differing POC versions, world discovery, storage limits and atomic
replacement, plus the existing launcher/supervisor tests. These use synthetic
game-file fixtures, not proprietary game code. Gradle verifies and packages the
actual tracked POC. JVM tests cannot establish Android picker/provider behavior,
Wurm SQL correctness, native JVM compatibility or physical device performance.
