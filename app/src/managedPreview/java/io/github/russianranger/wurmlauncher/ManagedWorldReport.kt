package io.github.russianranger.wurmlauncher

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** One bounded report per launch, outside the world. Only recognized evidence is retained. */
class ManagedWorldReport(private val file: File, private val runtime: File, private val config: ManagedLaunch) {
    private val root = runtime.canonicalFile.toPath()
    private val id = UUID.randomUUID().toString()
    private val created = Instant.now()
    private val configuration = mutableListOf<String>()
    private val evidence = linkedSetOf<String>()
    private var lifecycle = "Preparing; no server evidence yet"
    private var updated = created
    private var truncated = false

    @Synchronized fun prepare() {
        // Known candidate filenames only. Presence and literal values do not prove Wurm loaded them.
        for (relative in listOf("wurm.ini", "${config.world}/wurm.ini", "localhost/wurm.ini").distinct()) {
            val path = root.resolve(relative).normalize()
            try {
                if (!Files.exists(path)) { configuration += "CONFIG_ABSENT $relative"; continue }
                require(path.toRealPath().startsWith(root) && !Files.isSymbolicLink(path) && Files.isRegularFile(path))
                val bytes = Files.newInputStream(path).use { it.readNBytes(65537) }
                if (bytes.size > 65536) { configuration += "CONFIG_SKIPPED $relative exceeds 64 KiB"; continue }
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                configuration += "CONFIG_FILE $relative bytes=${bytes.size} sha256=$sha"
                // Deliberately not a schema/parser/editor: exact single-line literals only.
                bytes.toString(Charsets.ISO_8859_1).lineSequence().forEachIndexed { index, line ->
                    val match = Regex("^\\s*([A-Z_]+)\\s*=\\s*(.*?)\\s*$").matchEntire(line) ?: return@forEachIndexed
                    val key = match.groupValues[1]; val value = match.groupValues[2]
                    if (key !in SAFE_KEYS || configuration.size >= 128) return@forEachIndexed
                    val safe = value.length <= 256 && value.matches(Regex("[A-Za-z0-9_./: -]+")) && !value.contains("..")
                    configuration += "CONFIG_LITERAL $relative:${index + 1} $key=${if (safe) value else "[not displayed: unsupported literal]"}"
                }
            } catch (failure: Exception) { configuration += "CONFIG_UNAVAILABLE $relative ${failure.javaClass.simpleName}" }
        }
        publish()
    }

    @Synchronized fun observe(line: String) {
        if (line.length > 3000 || line.any { it.isISOControl() }) return
        val item = when {
            line.startsWith("[WurmARM64] World: ") -> relative(line.substringAfter(": "))?.let { "POC_WORLD $it" }
            line.startsWith("[WurmARM64] GameFolder recognized: ") -> relative(line.substringAfter(": "))?.let { "GAME_FOLDER $it" }
            line == "[WurmARM64] Current GameFolder set." -> "GAME_FOLDER_SET confirmed by POC"
            line == "[WurmARM64] Personal-server mode forced: true" -> "PERSONAL_SERVER true (POC)"
            line == "[WurmARM64] Starting server in offline mode..." -> "OFFLINE_LAUNCH requested by POC"
            line == "[WurmARM64] runServer() returned." -> "POC_RETURNED"
            line.startsWith("INFO: Database: jdbc:sqlite:") -> {
                val match = Regex("^INFO: Database: jdbc:sqlite:(.+) \\(SQLite [0-9.]+\\)$").matchEntire(line)
                match?.groupValues?.get(1)?.let { relative(it) }?.let { "JDBC_OPEN $it (Wurm/Flyway log)" }
            }
            line.startsWith("[WurmARM64] Game port=") -> {
                val match = Regex("^\\[WurmARM64] Game port=(-?\\d+) query port=(-?\\d+) mode=(\\d+) version=[A-Za-z0-9_.-]+$").matchEntire(line)
                match?.let {
                    val game = it.groupValues[1].toIntOrNull(); val query = it.groupValues[2].toIntOrNull()
                    if (game == null || query == null || game !in -32768..65535 || query !in -32768..65535) null
                    else "SHIM_PORTS game=${game and 65535} query=${query and 65535} mode=${it.groupValues[3]} (configuration passed to synthetic Steam shim; not a query listener test)"
                }
            }
            line.startsWith("[world] ") -> line.removePrefix("[world] ").takeIf { value ->
                value.substringBefore(' ') in PROBE_MARKERS
            }
            else -> null
        }
        if (item != null) record(item)
    }

    private fun relative(value: String): String? = runCatching {
        if (value.contains('?') || value.contains('#') || value.startsWith("file:")) return@runCatching null
        val path = root.resolve(value).normalize()
        if (!path.startsWith(root)) null else root.relativize(path).toString()
    }.getOrNull()

    @Synchronized fun event(value: String) { record(value.take(3000)) }
    @Synchronized fun state(value: String) { lifecycle = value.take(1000); publish() }

    private fun record(item: String) {
        if (item in evidence) return
        if (evidence.size < 384) evidence += item else truncated = true
        publish()
    }

    private fun publish() {
        updated = Instant.now()
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val pending = File(file.parentFile, file.name + ".pending")
        pending.writeText(buildString {
            append("Wurm Server 0.6.0 — world/configuration observation\n")
            append("Recorded launch snapshot; this is not live server status.\n")
            append("Launch: $id\nCreated: $created\nUpdated: $updated\n")
            append("Working copy: ${runtime.name}\nRuntime: $root\nWorld: ${config.world}\n")
            append("Requested heap: ${config.heapMiB} MiB\nExpected readiness TCP: ${config.port} (app setting only)\n")
            append("Last recorded lifecycle: $lifecycle\n\n")
            append("Configuration candidates BEFORE startup (presence/literals do not establish which file Wurm loaded):\n")
            append(configuration.joinToString("\n")); append("\n\n")
            append("Observed evidence from this launch:\n")
            append(if (evidence.isEmpty()) "None yet." else evidence.joinToString("\n"))
            if (truncated) append("\nREPORT_LIMIT reached; some evidence omitted")
            append("\n\nInterpretation:\n")
            append("GAME_FOLDER/JDBC_OPEN come from Wurm startup logs; OPEN_FILE/MAPPED_FILE are child process snapshots.\n")
            append("TCP_LISTEN identifies a socket owned by this child at inspection time. TCP_UNAVAILABLE is expected on Android versions that restrict /proc/net.\n")
            append("LOOPBACK_READY only establishes a local TCP connection, not LAN reachability or Wurm login.\n")
            append("SHIM_PORTS does not prove Steam/query networking. Missing evidence is unknown, not a default or proof of absence.\n")
            append("No configuration values or game files are changed by this reporter. Database contents/passwords/raw configuration are omitted.\n")
        })
        Files.move(pending.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    companion object {
        private val SAFE_KEYS = setOf("DB_HOST", "DB_PATH", "DB_PORT", "USE_SQLITE", "INTERNAL_IP", "INTERNAL_PORT",
            "EXTERNAL_IP", "EXTERNAL_PORT", "QUERY_PORT")
        private val PROBE_MARKERS = setOf("SNAPSHOT_BEGIN", "SNAPSHOT_END", "SNAPSHOT_UNAVAILABLE", "OPEN_FILE", "MAPPED_FILE",
            "FD_LIMIT", "FD_UNAVAILABLE", "FD_SCAN", "MAPS_LIMIT", "MAPS_UNAVAILABLE", "FILE_LIMIT", "FILES_NOT_OBSERVED",
            "TCP_LISTEN", "TCP_SCAN", "TCP_UNAVAILABLE", "TCP_LIMIT", "LISTENER_LIMIT")

        fun read(file: File, current: File?, selectedWorld: String?): String {
            if (!file.isFile) return "No world report yet. Start Server to capture this launch's paths and ports."
            val saved = file.readText()
            val matches = current != null && saved.lineSequence().any { it == "Working copy: ${current.name}" } &&
                saved.lineSequence().any { it == "World: $selectedWorld" }
            return (if (matches) "Saved report for the selected working copy/world.\n\n"
                else "HISTORICAL REPORT: working copy or selected world differs. Start captures a new report.\n\n") + saved
        }
    }
}
