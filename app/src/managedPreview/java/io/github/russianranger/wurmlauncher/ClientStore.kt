package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Client-only private import. Does not share pointers, locks or payloads with the server. */
class ClientStore(val home: File, private val limit: Long = 32L * 1024 * 1024 * 1024) {
    data class Installed(val id: String, val root: File, val jars: List<String>, val hashes: Map<String, String>, val inventory: String)
    val lock get() = File(home, "client.lock")
    fun current(): Installed? {
        val pointer = File(home, "current")
        if (!pointer.isFile) return null
        val id = pointer.readText().trim()
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val generation = File(home, id)
        val p = Properties().apply { File(generation, "identity.properties").inputStream().use { load(it) } }
        val root = File(generation, p.getProperty("root")).canonicalFile
        require(root.isDirectory && root.toPath().startsWith(generation.canonicalFile.toPath()))
        val jars = List(p.getProperty("jars").toInt().also { require(it in 1..512) }) { p.getProperty("jar.$it") }
        jars.forEach { require(!it.startsWith('/') && it.split('/').none { part -> part == ".." } && ':' !in it) }
        return Installed(id, root, jars, jars.associateWith { p.getProperty("sha.$it") }, File(generation, "inventory.txt").readText())
    }

    /** Caller owns client operation and lock. An interrupted/failed import cannot replace current. */
    fun importZip(input: InputStream, progress: (String) -> Unit): Installed {
        check(home.isDirectory || home.mkdirs())
        val active = current()?.id
        home.listFiles().orEmpty().filter { it.isDirectory && it.name != active && it.name.matches(Regex("[a-f0-9-]{36}(\\.pending)?")) }
            .forEach { it.deleteRecursively() }
        val id = UUID.randomUUID().toString()
        val stage = File(home, "$id.pending")
        val payload = File(stage, "payload").apply { check(mkdirs()) }
        val target = File(home, id)
        var committed = false
        try {
            var count = 0; var bytes = 0L; var next = 0L
            val names = hashSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                val buffer = ByteArray(65536)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check(!Thread.currentThread().isInterrupted) { "Client import cancelled" }
                    require(++count <= 100000) { "Client ZIP exceeds 100000 entries" }
                    val name = entry.name.removeSuffix("/")
                    require(name.isNotBlank() && name.length <= 1024 && !name.startsWith('/') && ':' !in name && '\\' !in name &&
                        name.none { it.isISOControl() } && name.split('/').none { it.isEmpty() || it == "." || it == ".." } && names.add(name)) { "Unsafe/duplicate client ZIP path" }
                    val file = File(payload, name)
                    require(file.canonicalFile.toPath().startsWith(payload.canonicalFile.toPath()))
                    if (entry.isDirectory) check(file.isDirectory || file.mkdirs()) else {
                        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                        Files.createFile(file.toPath())
                        file.outputStream().use { out ->
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException("Client import cancelled")
                                val n = zip.read(buffer); if (n < 0) break
                                bytes += n; require(bytes <= limit) { "Client ZIP exceeds import size limit" }
                                if (bytes >= next) {
                                    check(home.usableSpace > 128L * 1024 * 1024) { "Free more internal storage" }
                                    progress("Importing client: ${bytes / 1024 / 1024} MiB / $count entries")
                                    next = bytes + 8L * 1024 * 1024
                                }
                                out.write(buffer, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val root = if (File(payload, "client.jar").isFile) payload else {
                val children = payload.listFiles().orEmpty()
                require(children.size == 1 && children[0].isDirectory) { "ZIP the full Wurm client at root or in one wrapper folder" }
                children[0]
            }
            require(File(root, "client.jar").isFile && File(root, "common.jar").isFile && File(root, "lib").isDirectory) {
                "Missing client.jar, common.jar or lib/. Import the complete WurmLauncher/client installation, not server files."
            }
            val jarFiles = root.listFiles().orEmpty().filter { it.isFile && it.extension.equals("jar", true) } +
                File(root, "lib").walkTopDown().filter { it.isFile && it.extension.equals("jar", true) }.toList()
            require(jarFiles.size in 2..512)
            val owners = mutableMapOf<String, MutableList<String>>()
            val hashes = linkedMapOf<String, String>()
            for (jar in jarFiles.sortedBy { it.path }) {
                val relative = jar.relativeTo(root).invariantSeparatorsPath
                ZipFile(jar).use { z ->
                    require(z.size() in 1..100000) { "Empty/oversized JAR: $relative" }
                    CLASSES.forEach { cls -> if (z.getEntry(cls) != null) owners.getOrPut(cls) { mutableListOf() }.add(relative) }
                }
                hashes[relative] = ProbeInputs.sha256(jar)
            }
            val engine = owners[ENGINE].orEmpty()
            require(engine.size == 1) { "Need exactly one WurmClientBase provider; found ${engine.size}. Use the complete original client or one consistent patched installation." }
            require(owners["org/lwjgl/opengl/Display.class"].orEmpty().size == 1) { "Missing or duplicate LWJGL 2 Display class" }
            val jars = (engine + listOf("client.jar", "common.jar") + hashes.keys.sorted()).distinct()
            val resources = root.listFiles().orEmpty().filter { it.name != "lib" && (it.isDirectory || it.extension !in listOf("jar", "exe", "dll")) }.map { it.name }.sorted()
            val inventory = buildString {
                appendLine("CLIENT_IMPORT_OK generation=$id entries=$count bytes=$bytes")
                appendLine("Engine provider: ${engine.single()}")
                appendLine("Resource candidates: ${resources.joinToString(", ")}; completeness needs actual client startup")
                CLASSES.forEach { appendLine("CLASS $it -> ${owners[it]?.joinToString() ?: "MISSING"}") }
                appendLine("Classpath JAR SHA-256 (desktop JREs/native executables are not launched):")
                jars.forEach { appendLine("${hashes.getValue(it)}  $it") }
            }
            val p = Properties().apply {
                setProperty("root", root.relativeTo(stage).invariantSeparatorsPath); setProperty("jars", jars.size.toString())
                jars.forEachIndexed { i, name -> setProperty("jar.$i", name); setProperty("sha.$name", hashes.getValue(name)) }
            }
            File(stage, "identity.properties").outputStream().use { p.store(it, "Client import identity") }
            File(stage, "inventory.txt").writeText(inventory)
            Files.move(stage.toPath(), target.toPath(), ATOMIC_MOVE)
            val pending = File(home, "current.pending").apply { writeText(id) }
            Files.move(pending.toPath(), File(home, "current").toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            committed = true
            return requireNotNull(current())
        } finally { stage.deleteRecursively(); if (!committed) target.deleteRecursively() }
    }
    companion object {
        const val ENGINE = "com/wurmonline/client/WurmClientBase.class"
        val CLASSES = listOf(ENGINE, "com/wurmonline/client/launcherfx/WurmMain.class", "org/lwjgl/opengl/Display.class",
            "org/lwjgl/input/Keyboard.class", "org/lwjgl/input/Mouse.class", "org/lwjgl/openal/AL.class", "net/java/games/input/Controller.class")
    }
}
