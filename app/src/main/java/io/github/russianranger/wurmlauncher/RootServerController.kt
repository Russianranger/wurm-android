package io.github.russianranger.wurmlauncher

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owns one foreground su session. Stop travels through that session, never pkill/killall. */
class RootServerController(
    private val config: LaunchConfig,
    private val script: String,
    private val onStatus: (Phase, String) -> Unit,
    private val onExit: (Boolean, String) -> Unit
) {
    private val guard = Any()
    private val watcher = Executors.newSingleThreadScheduledExecutor()
    private var process: Process? = null
    @Volatile private var stopping = false
    @Volatile private var pid: Long? = null
    @Volatile private var finished = false
    private var timedOut = false
    private val token = UUID.randomUUID().toString()
    private val marker = "WURM_LAUNCHER_$token:"

    fun start() {
        Thread({ runServer() }, "wurm-root-session").start()
    }

    fun stop() {
        synchronized(guard) {
            if (finished || stopping) return
            stopping = true
            // EOF is the supervisor's stop signal, including after app process death.
            runCatching { process?.outputStream?.close() }
        }
        onStatus(Phase.STOPPING, "Waiting for the managed JVM to exit; no forced kill.")
    }

    private fun runServer() {
        var error: String? = null
        var exitCode = -1
        try {
            check(!portOpen()) { "TCP 3724 is already in use. Stop your manually launched server first." }
            synchronized(guard) {
                if (stopping) return@synchronized
                process = ProcessBuilder("su", "-c", config.command(script, token))
                    .redirectErrorStream(true).start()
            }
            val running = synchronized(guard) { process } ?: return
            watcher.schedule({
                if (!finished && pid == null) {
                    timedOut = true
                    ServerState.append("[launcher] Root/start handshake timed out after 60 seconds.")
                    stop()
                    // No Java has been acknowledged as started. Closing stdin prevents
                    // a subsequently approved root prompt from starting a new server.
                    running.destroy()
                }
            }, 60, TimeUnit.SECONDS)
            watcher.scheduleWithFixedDelay({
                if (!finished && !stopping && pid != null) {
                    val ready = portOpen()
                    if (!stopping && !finished) onStatus(
                        Phase.RUNNING,
                        "JVM PID $pid · TCP 3724 ${if (ready) "accepting connections" else "not ready"}"
                    )
                }
            }, 1, 2, TimeUnit.SECONDS)
            consumeLines(running.inputStream) { line ->
                when {
                    line == "${marker}READY" -> synchronized(guard) {
                        if (!stopping) {
                            running.outputStream.write("start\n".toByteArray())
                            running.outputStream.flush()
                        }
                    }
                    line.startsWith("${marker}PID:") -> {
                        pid = line.substringAfter("${marker}PID:").toLongOrNull()
                        ServerState.append("[launcher] Managed JVM started: PID $pid")
                        if (!stopping) onStatus(Phase.RUNNING, "JVM PID $pid · waiting for TCP 3724")
                    }
                    line.startsWith("${marker}EXIT:") ->
                        ServerState.append("[launcher] JVM exit code: ${line.substringAfter("${marker}EXIT:")}")
                    else -> ServerState.append(line)
                }
            }
            exitCode = running.waitFor()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            ServerState.append("[launcher] $error")
        } finally {
            synchronized(guard) {
                finished = true
                runCatching { process?.outputStream?.close() }
            }
            watcher.shutdownNow()
            val failed = timedOut || (!stopping && (error != null || exitCode != 0))
            onExit(failed, when {
                timedOut -> "Root/start timed out. Check the root manager and try again."
                error != null -> error
                stopping -> "Managed server stopped (exit $exitCode)."
                else -> "Managed server exited (code $exitCode)."
            })
        }
    }

    companion object {
        fun portOpen(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", 3724), 400) }
            true
        }.getOrDefault(false)

        /** Bounded even if a native library produces megabytes without a newline. */
        fun consumeLines(input: InputStream, emit: (String) -> Unit) {
            input.bufferedReader().use { reader ->
                val buffer = CharArray(2048)
                val line = StringBuilder()
                var truncated = false
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    for (i in 0 until count) {
                        val c = buffer[i]
                        if (c == '\n') {
                            emit(line.toString().trimEnd('\r') + if (truncated) " [truncated]" else "")
                            line.setLength(0)
                            truncated = false
                        } else if (line.length < 4000) line.append(c)
                        else truncated = true
                    }
                }
                if (line.isNotEmpty()) emit(line.toString() + if (truncated) " [truncated]" else "")
            }
        }
    }
}
