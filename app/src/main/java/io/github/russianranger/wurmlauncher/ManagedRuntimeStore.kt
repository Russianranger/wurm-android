package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Storage only. Never executes, patches or uploads imported game files. */
class ManagedRuntimeStore(
    private val home: File,
    private val maxBytes: Long = 32L * 1024 * 1024 * 1024,
    private val maxEntries: Int = 100_000,
    private val reserveBytes: Long = 64L * 1024 * 1024
) {
    data class Installed(
        val generation: String,
        val runtime: File,
        val worlds: List<String>,
        val fileCount: Int,
        val bytes: Long,
        val jarHashes: Map<String, String>
    ) {
        fun report(selectedWorld: String): String = buildString {
            appendLine("Wurm Server — import preview")
            appendLine("Generation: $generation")
            appendLine("Storage: app-private copy")
            appendLine("Files: $fileCount; uncompressed bytes: $bytes")
            appendLine("World candidates: ${worlds.joinToString(", ")}")
            appendLine("Selected managed world: $selectedWorld")
            appendLine("Embedded JVM: absent; managed server has NOT started.")
            appendLine("POC: source-backed Java 17; personal/offline entry point and Steam shim.")
            appendLine("SQLite SQL patch: imported bytes preserved; patch behavior not verified.")
            appendLine("Rooted launcher: separate Termux runtime, always Adventure.")
            appendLine("SHA-256 of imported JARs (plus packaged POC):")
            jarHashes.toSortedMap().forEach { (path, hash) -> appendLine("$hash  $path") }
            appendLine("No configuration contents, credentials or database contents in this report.")
        }
    }

    fun current(): Installed? {
        val pointer = File(home, "current")
        if (!pointer.isFile) return null
        val id = pointer.readText().trim()
        require(GENERATION.matches(id)) { "Invalid managed-runtime pointer." }
        val folder = File(home, id)
        val props = Properties().apply { File(folder, "import.properties").inputStream().use { load(it) } }
        val runtime = File(folder, props.getProperty("runtime"))
        require(runtime.isDirectory && runtime.canonicalPath.startsWith(folder.canonicalPath + File.separator))
        return Installed(id, runtime,
            List(props.getProperty("world.count").toInt()) { props.getProperty("world.$it") },
            props.getProperty("files").toInt(), props.getProperty("bytes").toLong(),
            props.stringPropertyNames().filter { it.startsWith("sha256.") }
                .associate { it.removePrefix("sha256.") to props.getProperty(it) })
    }

    /** Caller serializes imports. An interrupted process can leave only unselected staging data. */
    fun importZip(input: InputStream, pocJar: ByteArray, progress: (String) -> Unit = {}): Installed {
        require(sha256(pocJar) == POC_SHA256) { "Packaged POC checksum failed." }
        check(home.isDirectory || home.mkdirs()) { "Cannot create private import storage." }
        val previous = current()?.generation
        cleanUnused(previous)
        val id = UUID.randomUUID().toString()
        val stage = File(home, "$id.pending")
        val payload = File(stage, "payload")
        check(payload.mkdirs())
        val generation = File(home, id)
        val pointerTemp = File(home, "current.pending")
        var committed = false
        try {
            var total = 0L
            var fileCount = 0
            var entryCount = 0
            var nextProgress = 0L
            val names = HashSet<String>()
            val hashes = mutableMapOf<String, String>()
            val buffer = ByteArray(64 * 1024)
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entryCount <= maxEntries) { "ZIP exceeds $maxEntries entries." }
                    val name = entry.name.removeSuffix("/")
                    require(name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && ':' !in name &&
                        name.none { it.isISOControl() } &&
                        name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                        "ZIP contains an unsafe path. Re-export a normal runtime ZIP."
                    }
                    require(names.add(name)) { "ZIP contains a duplicate path: $name" }
                    val target = File(payload, name)
                    require(target.canonicalPath.startsWith(payload.canonicalPath + File.separator))
                    if (entry.isDirectory) {
                        check(target.isDirectory || target.mkdirs()) { "Conflicting ZIP directory: $name" }
                    } else {
                        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                        // Do not follow existing links or overwrite a file from another entry.
                        Files.createFile(target.toPath())
                        val digest = if (name.endsWith(".jar", ignoreCase = true)) MessageDigest.getInstance("SHA-256") else null
                        target.outputStream().use { output ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count == -1) break
                                total += count
                                require(total <= maxBytes) { "ZIP exceeds the ${maxBytes / 1024 / 1024} MiB import limit." }
                                if (total >= nextProgress) {
                                    check(home.usableSpace > reserveBytes + buffer.size) { "Not enough free internal storage." }
                                    progress("Copying ${total / 1024 / 1024} MiB · $fileCount files")
                                    nextProgress = total + 8L * 1024 * 1024
                                }
                                output.write(buffer, 0, count)
                                digest?.update(buffer, 0, count)
                            }
                        }
                        digest?.let { hashes[name] = hex(it.digest()) }
                        fileCount++
                    }
                    zip.closeEntry() // Also checks ZIP CRCs; a corrupt stream never commits.
                }
            }
            progress("Validating runtime and world candidates…")
            val root = if (File(payload, "server.jar").isFile) payload else {
                val children = payload.listFiles().orEmpty()
                require(children.size == 1 && children[0].isDirectory) {
                    "Put runtime files at ZIP root, or inside one runtime/ directory."
                }
                children[0]
            }
            REQUIRED_JARS.forEach { path ->
                val jar = File(root, path)
                require(jar.isFile) { "Missing $path. Import the complete prepared POC runtime." }
                ZipFile(jar).use { require(it.size() > 0) { "Empty JAR: $path" } }
            }
            require(File(root, "lib").isDirectory) { "Missing lib/ directory." }
            val worlds = root.listFiles().orEmpty().filter {
                it.isDirectory && (File(it, "wurm.ini").isFile || File(it, "sqlite").isDirectory)
            }.map { it.name }.sorted()
            require(worlds.isNotEmpty()) { "No world found. Include Adventure/ (wurm.ini or sqlite/), or another existing world." }
            val poc = File(root, "wurm-arm64-poc.jar")
            if (poc.exists()) {
                val importedHash = hashes[poc.relativeTo(payload).invariantSeparatorsPath]
                require(poc.isFile && (importedHash == POC_SHA256 || importedHash == LEGACY_POC_SHA256)) {
                    "Imported POC differs from this app's source-backed JAR. This bootstrap version is not recognized; keep your archive and export the session report."
                }
                if (importedHash == LEGACY_POC_SHA256) {
                    val upgradedTotal = total - poc.length() + pocJar.size
                    require(upgradedTotal <= maxBytes) { "ZIP plus upgraded POC exceeds the import limit." }
                    check(home.usableSpace - pocJar.size > reserveBytes) { "Not enough free internal storage to upgrade POC." }
                    // Only the exact historical authored bootstrap is replaced, in uncommitted staging.
                    poc.writeBytes(pocJar)
                    total = upgradedTotal
                    progress("POC_UPGRADED: known previous bootstrap replaced with packaged personal-server fix.")
                }
            } else {
                require(total + pocJar.size <= maxBytes) { "ZIP plus POC exceeds the import limit." }
                poc.writeBytes(pocJar)
                total += pocJar.size
                fileCount++
            }
            hashes[poc.relativeTo(payload).invariantSeparatorsPath] = POC_SHA256
            val prefix = if (root == payload) "" else root.name + "/"
            val runtimePath = root.relativeTo(stage).invariantSeparatorsPath
            val installed = Installed(id, File(generation, runtimePath), worlds, fileCount, total,
                hashes.mapKeys { it.key.removePrefix(prefix) })
            val properties = Properties().apply {
                setProperty("runtime", runtimePath)
                setProperty("files", fileCount.toString())
                setProperty("bytes", total.toString())
                setProperty("world.count", worlds.size.toString())
                worlds.forEachIndexed { index, world -> setProperty("world.$index", world) }
                installed.jarHashes.forEach { (path, hash) -> setProperty("sha256.$path", hash) }
            }
            File(stage, "import.properties").outputStream().use { properties.store(it, "Import identity; no Wurm configuration values") }
            Files.move(stage.toPath(), generation.toPath(), StandardCopyOption.ATOMIC_MOVE)
            pointerTemp.writeText(id)
            Files.move(pointerTemp.toPath(), File(home, "current").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            committed = true
            // Publication already succeeded. Cleanup failure must not report a failed import.
            runCatching { cleanUnused(id) }
            return installed
        } finally {
            if (!committed) {
                stage.deleteRecursively()
                generation.deleteRecursively()
                pointerTemp.delete()
            }
        }
    }

    private fun cleanUnused(active: String?) {
        home.listFiles().orEmpty().filter {
            it.isDirectory && it.name != active && GENERATION.matches(it.name.removeSuffix(".pending"))
        }.forEach { it.deleteRecursively() }
    }

    companion object {
        // Exact authored artifact shipped through 0.10.14; never accept arbitrary imported bootstrap code.
        private const val LEGACY_POC_SHA256 = "0fe4039a1a06afae93099b6eaf140e04fe7e0b1f1145323116468f8f78a884fe"
        const val POC_SHA256 = "82a39c9797a394b036785ad366e5c1a6ed0de935ab1f3b82e1fcc80f5181dfa4"
        val REQUIRED_JARS = listOf("server.jar", "common.jar",
            "poc-lib/sqlite-jdbc-3.53.2.1.jar", "poc-lib/sqlite-jdbc-3.53.2.1-natives-android.jar")
        private val GENERATION = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
