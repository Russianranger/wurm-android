package io.github.russianranger.wurmlauncher

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/** Called only for the pinned stock recipe, inside unpublished import staging. */
object ServerDatabaseLayout {
    val DATABASES=listOf("creatures","deities","economy","items","login","logs","players","templates","zones")
        .map { "wurm$it.db" }

    fun prepare(root: File, progress: (String) -> Unit = {}) {
        val plans=root.listFiles().orEmpty().filter { it.isDirectory && File(it,"wurm.ini").isFile }.mapNotNull { world ->
            val ini=File(world,"wurm.ini")
            require(ini.length()<=65536) { "${world.name}/wurm.ini exceeds the configuration limit." }
            val before=ini.readBytes()
            val properties=Properties().apply { before.inputStream().use { load(it) } }
            val host=properties.getProperty("DB_HOST") ?: error("${world.name}: DB_HOST is missing.")
            val configured=File(host).let { if(it.isAbsolute) it else File(root,host) }
            require(configured.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
                "${world.name}: DB_HOST must refer to databases inside this runtime."
            }
            if(configured.exists()) {
                validateDatabases(configured)
                return@mapNotNull null // Preserve a real shared/previously used database directory.
            }
            require(host=="localhost") { "${world.name}: configured database directory is missing; no alternative was guessed." }
            validateDatabases(world)
            val text=before.toString(Charsets.ISO_8859_1)
            val literal=Regex("(?m)^DB_HOST=localhost(?=\\r?$)")
            require(literal.findAll(text).count()==1) { "${world.name}: unsupported DB_HOST syntax; configuration was not changed." }
            // Java Properties escaping keeps names with spaces/Unicode valid without rewriting other settings.
            val encoded=ByteArrayOutputStream().also { out ->
                Properties().apply { setProperty("DB_HOST",world.name) }.store(out,null)
            }.toString("ISO-8859-1").lineSequence().first { it.startsWith("DB_HOST=") }
            val after=literal.replace(text) { encoded }.toByteArray(Charsets.ISO_8859_1)
            val checked=Properties().apply { after.inputStream().use { load(it) } }
            require(checked.getProperty("DB_HOST")==world.name) { "${world.name}: ambiguous DB_HOST configuration; import was not activated." }
            ini to after
        }
        // Validate every world before editing any INI. The containing import remains unpublished.
        plans.forEach { (ini,bytes) ->
            if(Thread.currentThread().isInterrupted) throw InterruptedException("Preparation cancelled")
            val directory=requireNotNull(ini.parentFile)
            val pending=Files.createTempFile(directory.toPath(),"wurm-db-", ".pending")
            try {
                Files.write(pending,bytes)
                Files.move(pending,ini.toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
            } finally { Files.deleteIfExists(pending) }
            progress("Prepared ${directory.name} database path; database contents preserved")
        }
    }

    private fun validateDatabases(directory: File) {
        val sqlite=File(directory,"sqlite")
        DATABASES.forEach { name ->
            val file=File(sqlite,name)
            require(file.isFile && file.length()>=100 && !Files.isSymbolicLink(file.toPath())) {
                "Missing or incomplete database: ${directory.name}/sqlite/$name. Import the complete world; existing files were not replaced."
            }
            val header=file.inputStream().use { it.readNBytes(16) }
            require(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))) {
                "Invalid SQLite header: ${directory.name}/sqlite/$name."
            }
        }
    }
}
