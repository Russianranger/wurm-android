# Wurm Server 0.4.1 — native heap compatibility test

The Thor's 0.4.0 report passed Java 17.0.20/SQLite preflight, created a checkpoint,
selected Adventure, initialized the Steam shim and opened nine SQLite databases.
Wurm then aborted at `Loading servers` with `Pointer tag ... was truncated`
(exit 134), before TCP readiness. The exact offending native call is still unknown.

This correction opts out of Android heap-pointer tagging **only in the app-owned
Java child**, before loading Java. The child verifies the allocator accepted the
setting and fails before Java if it did not. This is a compatibility experiment;
it removes that child's heap-tag checks, not the underlying invalid-pointer bug.
It changes no global Android settings. A new preflight exercises native networking,
localhost resolution and a TCP loopback exchange before opening Wurm.

The source-built JRE, handwritten POC, imported Wurm files and SQLite fixes are
unchanged. No root, Termux or separate Java installation is needed. Physical
startup beyond the abort and Wurm save/reopen remain unproven in this version.

1. **Keep 0.4.0 installed**, including its report and checkpoint. Install this APK
   alongside it; the new package is `io.github.russianranger.wurmlauncher.managedfix1`.
   Choose the Wurm Server screen displaying **0.4.1**.
2. Import the same **wurm-runtime-20260908-052948.zip** from Downloads. Nothing new
   needs to be copied from Termux. Allow at least **4 GiB free internal storage**.
3. Select **Adventure**, heap **4096 MiB**, expected TCP **3724**. Stop any other
   server using that port. Tap **Start Server**, allow notifications, and keep the
   app open for this first attempt.
4. Look for `HEAP_TAGGING_OFF` with `after=0x00`, `NETWORK_OK`, `PREFLIGHT_PASS`,
   progress beyond `Loading servers`, and ideally `TCP_READY` / Running.
5. **Export and share `wurm-server-report.txt` after the first attempt**, even if
   startup fails. If Running, use normal **Stop Server** and export again after
   exit. Do not use Force Stop to test saving.
6. If recovery is required, export the working runtime and before-start checkpoint
   before restoring. Do not repeatedly retry the old 0.4.0 build.

`THOR_NATIVE_HEAP_FIX.md` contains the diagnosis, exact device procedure and every
changed file. `MANAGED_SERVER_TEST.md` covers lifecycle, recovery and building.
The Client tab keeps its import/Settings groundwork; client execution is pending.

Development builds use ephemeral CI debug signing. The separate package preserves
earlier installs but does not migrate their private data. The release retains the
matching OpenJDK/Android-port/FreeType/CUPS sources, patch, recipe and APK notices.
`SHA256SUMS` identifies the downloadable APK and its companion files.
