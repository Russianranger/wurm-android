package io.github.russianranger.wurmlauncher

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.time.Instant
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded disposable test, not the long-running Wurm service. */
object JvmProbeCoordinator {
    data class Snapshot(val busy: Boolean, val detail: String, val log: String)
    private var busy = false
    private var detail = "Select the same prepared runtime ZIP to begin."
    private var loaded = false
    private val lines = ArrayDeque<String>()
    @Volatile private var worker: Thread? = null
    @Volatile private var child: Process? = null
    @Volatile private var cancelled = false
    @Volatile private var timedOut = false

    @Synchronized fun snapshot(context: Context): Snapshot {
        if (!loaded) {
            val saved = reportFile(context)
            if (saved.isFile) {
                saved.useLines { old -> old.take(400).forEach { append(it) } }
                detail = "Previous report loaded. No diagnostic is running."
            }
            loaded = true
        }
        return Snapshot(busy, detail, lines.joinToString("\n"))
    }

    @Synchronized private fun append(line: String) {
        if (lines.size == 400) lines.removeFirst()
        lines.addLast(line.take(3000))
    }
    @Synchronized private fun status(message: String) { detail = message }

    @Synchronized fun start(context: Context, uri: Uri) {
        if (busy) return
        val app = context.applicationContext
        loaded = true
        busy = true
        cancelled = false
        timedOut = false
        lines.clear()
        detail = "Preparing diagnostic… Keep this app open."
        val version = app.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        append("Wurm Server JVM Test $version · ${Instant.now()}")
        append("[app] Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}, ABIs ${Build.SUPPORTED_ABIS.joinToString()}")
        append("[app] uid=${android.os.Process.myUid()}; no Wurm world is opened")
        save(app)
        worker = Thread({ run(app, uri) }, "wurm-jvm-diagnostic").also { it.start() }
    }

    private fun run(app: Context, uri: Uri) {
        var runDir: File? = null
        val timer = Executors.newSingleThreadScheduledExecutor()
        try {
            require("arm64-v8a" in Build.SUPPORTED_64_BIT_ABIS) { "ARM64 device required" }
            val work = File(app.filesDir, "jvm-probe-work").apply { mkdirs() }
            // Only disposable runs in this separate test package, never imported worlds.
            work.listFiles().orEmpty().filter { it.name.startsWith("run-") }.forEach { it.deleteRecursively() }
            runDir = File(work, "run-${UUID.randomUUID()}").apply { check(mkdirs()) }
            check(runDir.usableSpace > 400L * 1024 * 1024) { "At least 400 MiB free internal storage is needed for this test." }
            status("Reading runtime ZIP; extracting only the two SQLite JARs…")
            val jars = requireNotNull(app.contentResolver.openInputStream(uri)) { "Cannot open selected ZIP" }.use { input ->
                ProbeInputs().install(input, runDir) { bytes -> status("Reading ZIP: ${bytes / 1024 / 1024} MiB. Keep app open…") }
            }
            jars.forEach { append("[input] ${it.name}: ${ProbeInputs.sha256(it)}") }
            append("[input] Both SQLite JARs match the verified Thor baseline")
            status("Installing and verifying embedded Java…")
            val home = ProbeRuntime.install(app, ::append)
            val probe = File(runDir, "runtime-probe.jar")
            app.assets.open("runtime-probe.jar").use { input -> probe.outputStream().use { input.copyTo(it) } }
            val tmp = File(runDir, "tmp").apply { check(mkdirs()) }
            val native = File(app.applicationInfo.nativeLibraryDir)
            val runner = File(native, "libwurmjvm_runner.so")
            require(runner.canExecute()) { "APK-packaged native runner is not executable: $runner" }
            val classpath = (listOf(probe) + jars).joinToString(":") { it.absolutePath }
            val args = listOf(runner.absolutePath, "-Xms32m", "-Xmx256m", "-Djava.awt.headless=true",
                "-Djava.home=${home.absolutePath}", "-Djava.io.tmpdir=${tmp.absolutePath}",
                "-Dorg.sqlite.tmpdir=${tmp.absolutePath}", "-Duser.home=${runDir.absolutePath}",
                "-Djava.library.path=${home.absolutePath}/lib:${home.absolutePath}/lib/server:${native.absolutePath}",
                "-Dsun.boot.library.path=${home.absolutePath}/lib:${native.absolutePath}",
                "-XX:ErrorFile=${runDir.absolutePath}/hs_err_pid%p.log", "-XX:-CreateCoredumpOnCrash",
                "-cp", classpath, "probe.RuntimeProbe", runDir.absolutePath)
            val builder = ProcessBuilder(args).directory(runDir).redirectErrorStream(true)
            builder.environment().apply {
                clear()
                putAll(ProbeEnvironment.create(home, native, tmp))
            }
            append("[app] JLI tracing enabled; JVM directory is first in LD_LIBRARY_PATH")
            synchronized(this) {
                if (cancelled) throw InterruptedException("Cancelled")
                child = builder.start()
                child!!.outputStream.close()
            }
            val process = child!!
            append("[app] Child started; 90-second timeout, maximum heap 256 MiB")
            status("Running Java and SQLite diagnostic…")
            timer.schedule({
                synchronized(this) {
                    if (child === process && process.isAlive) { timedOut = true; process.destroyForcibly() }
                }
            }, 90, TimeUnit.SECONDS)
            var javaOk = false
            var sqliteOk = false
            var probeOk = false
            RootServerController.consumeLines(process.inputStream) { line ->
                if (line == "[probe] JAVA_OK") javaOk = true
                if (line.startsWith("[probe] SQLITE_OK:")) sqliteOk = true
                if (line == "[probe] PROBE_OK") probeOk = true
                append(line)
            }
            val exit = process.waitFor()
            append("[app] Child exit=$exit")
            when {
                cancelled -> finish("CANCELLED", "Diagnostic cancelled; test process stopped.")
                timedOut -> finish("TIMEOUT", "Diagnostic timed out. Export the report.")
                exit == 0 && javaOk && sqliteOk && probeOk -> finish("PASS", "Java and SQLite passed. Export the report.")
                else -> finish("FAIL", "Diagnostic failed (exit $exit). Export the report.")
            }
        } catch (e: Exception) {
            append("[app] ${e.javaClass.simpleName}: ${e.message}")
            finish(if (cancelled) "CANCELLED" else "FAIL", if (cancelled) "Diagnostic cancelled." else "Diagnostic failed. Export the report.")
        } finally {
            child?.let { if (it.isAlive) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) } }
            child = null
            timer.shutdownNow()
            // Include the beginning of a JVM fatal-error report without uploading any files.
            runDir?.listFiles()?.filter { it.name.startsWith("hs_err_pid") }?.forEach { crash ->
                append("[crash] ${crash.name}")
                runCatching { crash.useLines { it.take(60).forEach(::append) } }
            }
            synchronized(this) { save(app); busy = false; worker = null }
        }
    }

    @Synchronized fun cancel() {
        if (!busy) return
        cancelled = true
        status("Cancelling diagnostic…")
        // Only the disposable diagnostic child, never a Wurm server process.
        child?.destroyForcibly()
        if (child == null) worker?.interrupt()
    }
    private fun finish(result: String, message: String) {
        append("[app] RESULT: $result")
        status(message)
    }
    @Synchronized private fun save(context: Context) {
        runCatching { reportFile(context).writeText(lines.joinToString("\n") + "\n") }
    }
    private fun reportFile(context: Context) = File(context.filesDir, "last-jvm-probe-report.txt")
}
