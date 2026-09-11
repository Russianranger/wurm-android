package io.github.russianranger.wurmlauncher

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Best-effort OS evidence for this app/attempt; no root, READ_LOGS request or signal hooks. */
object ClientCrashCapture {
    private fun readLimited(input: InputStream, limit: Int, truncate: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        if (out.size() > limit && truncate) return out.toByteArray().copyOf(limit) + "\nLOGCAT_TRUNCATED size limit\n".toByteArray()
        require(out.size() <= limit) { "Crash data exceeded $limit bytes" }
        return out.toByteArray()
    }
    fun collect(context: Context, evidence: ClientCrashEvidence, log: (String) -> Unit) {
        val emit: (String) -> Unit = { log("[client-crash] $it") }
        emit("CAPTURE_BEGIN pid=${evidence.pid} uid=${evidence.uid}; current attempt only")
        var process: Process? = null
        try {
            val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(evidence.startedAt))
            // UID/time scope includes this child's crash dumper when Android permits it.
            // Never retry without the UID filter or request access to other apps' logs.
            val command = listOf("/system/bin/logcat", "-d", "-b", "main", "-b", "crash", "-v", "threadtime",
                "--uid=${evidence.uid}", "-T", since, "libc:F", "DEBUG:F", "AndroidRuntime:E", "*:S")
            process = ProcessBuilder(command).redirectErrorStream(true).start()
            val running = process
            val result = AtomicReference<String>("")
            val reader = Thread({
                result.set(runCatching { running.inputStream.use { readLimited(it, 64 * 1024, truncate = true).toString(Charsets.UTF_8) } }
                    .getOrElse { "LOGCAT_READ_UNAVAILABLE ${it.javaClass.simpleName}: ${it.message}" })
            }, "wurm-crash-logcat").apply { isDaemon = true; start() }
            if (!running.waitFor(3, TimeUnit.SECONDS)) { running.destroyForcibly(); emit("LOGCAT_TIMEOUT") }
            reader.join(500)
            val text = result.get()
            if (text.isBlank()) emit("LOGCAT_UNAVAILABLE no readable current-app crash lines")
            else text.lineSequence().take(240).forEach { emit("LOGCAT " + it.take(1000)) }
        } catch (error: Exception) { emit("LOGCAT_UNAVAILABLE ${error.javaClass.simpleName}: ${error.message}") }
        finally { process?.let { if (it.isAlive) it.destroyForcibly(); runCatching { it.inputStream.close(); it.waitFor(500, TimeUnit.MILLISECONDS) } } }
        try {
            if (evidence.pid == 0) { emit("EXIT_INFO_UNAVAILABLE child PID was not observed"); return }
            val manager = context.getSystemService(ActivityManager::class.java)
            // A pre-main failure can exit before Android finishes its tombstone.
            // Briefly retry only this package/PID and the same UID/time scope.
            fun matchingRecord() = manager.getHistoricalProcessExitReasons(context.packageName, evidence.pid, 8)
                .firstOrNull { it.pid == evidence.pid && it.realUid == evidence.uid && it.timestamp >= evidence.startedAt }
            var record = matchingRecord()
            var attempts = 0
            while (record == null && attempts++ < 5) {
                Thread.sleep(200)
                record = matchingRecord()
            }
            if (record == null) { emit("EXIT_INFO_UNAVAILABLE no matching exec-child record; Android may not track this child"); return }
            emit("EXIT_INFO pid=${record.pid} reason=${record.reason} status=${record.status} timestamp=${record.timestamp} description=${record.description?.take(1000)}")
            if (record.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) { emit("TOMBSTONE_UNAVAILABLE reason is not native crash"); return }
            val stream = record.traceInputStream
            if (stream == null) emit("TOMBSTONE_UNAVAILABLE Android supplied no trace")
            else stream.use { ClientTombstone.summarize(readLimited(it, ClientTombstone.LIMIT), evidence.pid, evidence.uid).forEach(emit) }
        } catch (error: Exception) { emit("EXIT_INFO_UNAVAILABLE ${error.javaClass.simpleName}: ${error.message}") }
        finally { emit("CAPTURE_END") }
    }
}
