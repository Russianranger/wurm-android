package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** All callers hold exclusive() while copying/exporting, and serialize child launches. */
class ManagedWorkspace(val home: File) {
    val imports = ManagedRuntimeStore(File(home, "original"))
    val lockFile get() = File(home, "server.lock")
    val checkpoint get() = File(home, "before-start.zip")
    val recoveryRequired get() = File(home, "recovery-required")
    val auditDirectory get() = File(home, "storage-audit")
    val auditReport get() = File(auditDirectory, "report.txt")
    val worldReport get() = File(home, "world-report.txt")

    fun storageReport(): String = if (auditReport.isFile) auditReport.readText() else "No storage audit completed yet."

    fun <T> exclusive(action: () -> T): T {
        check(home.isDirectory || home.mkdirs())
        RandomAccessFile(lockFile, "rw").channel.use { channel ->
            val lock = channel.tryLock() ?: error("A server still owns this workspace. Wait for it to stop.")
            lock.use {
                // Recover storage left by process death before atomic publication.
                val selected = working()?.name
                home.listFiles().orEmpty().filter { file ->
                    file.name != selected && (file.name.matches(Regex("work-[a-f0-9-]{36}(\\.pending)?")) ||
                        file.name.matches(Regex("restore-[a-f0-9-]{36}")))
                }.forEach { it.deleteRecursively() }
                File(home, "before-start.pending").delete()
                return action()
            }
        }
    }

    fun working(): File? {
        val pointer = File(home, "working")
        if (!pointer.isFile) return null
        val name = pointer.readText().trim()
        require(name.matches(Regex("work-[a-f0-9-]{36}")))
        return File(home, name).also { check(it.isDirectory) }
    }

    fun ensureWorking(progress: (String) -> Unit): File = working() ?: restoreOriginal(progress)

    /** Publish a complete independent copy; failure leaves the old working pointer intact. */
    fun restoreOriginal(progress: (String) -> Unit): File {
        val original = requireNotNull(imports.current()) { "Import your stopped runtime ZIP first." }
        return replaceWorking(original, progress)
    }

    fun restoreCheckpoint(poc: ByteArray, progress: (String) -> Unit): File {
        check(checkpoint.isFile) { "No before-start checkpoint exists yet." }
        val stage = File(home, "restore-${UUID.randomUUID()}")
        try {
            val restored = checkpoint.inputStream().use { ManagedRuntimeStore(stage).importZip(it, poc, progress) }
            return replaceWorking(restored, progress)
        } finally { stage.deleteRecursively() }
    }

    private fun replaceWorking(original: ManagedRuntimeStore.Installed, progress: (String) -> Unit): File {
        val name = "work-${UUID.randomUUID()}"
        val stage = File(home, "$name.pending")
        val target = File(home, name)
        val old = working()
        var committed = false
        try {
            check(stage.mkdirs())
            var bytes = 0L
            var next = 0L
            visit(original.runtime) { source, relative ->
                val dest = File(stage, relative)
                if (source.isDirectory) check(dest.mkdirs() || dest.isDirectory) else {
                    check(home.usableSpace > source.length() + RESERVE) { "Not enough space for a protected working copy." }
                    val digest = MessageDigest.getInstance("SHA-256")
                    source.inputStream().use { input -> dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            interrupted()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); bytes += n
                        }
                    } }
                    original.jarHashes[relative]?.let { expected ->
                        check(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
                            "Original JAR changed: $relative. Working copy was not replaced."
                        }
                    }
                    if (bytes >= next) { progress("Preparing working copy: ${bytes / 1024 / 1024} MiB"); next = bytes + 16L * 1024 * 1024 }
                }
            }
            Files.move(stage.toPath(), target.toPath(), ATOMIC_MOVE)
            val pending = File(home, "working.pending")
            pending.writeText(name)
            Files.move(pending.toPath(), File(home, "working").toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            committed = true
            recoveryRequired.delete()
            // Only our previous generated tree is removed, after publication.
            runCatching { old?.deleteRecursively() }
            return target
        } finally {
            stage.deleteRecursively()
            if (!committed) target.deleteRecursively()
        }
    }

    fun saveCheckpoint(progress: (String) -> Unit) {
        val root = requireNotNull(working())
        var size = 0L
        visit(root) { file, _ -> if (file.isFile) size += file.length() }
        check(home.usableSpace > size + RESERVE) { "Free more space for the before-start checkpoint ($size bytes plus reserve)." }
        val pending = File(home, "before-start.pending")
        try {
            pending.outputStream().use { export(root, it, progress) }
            Files.move(pending.toPath(), checkpoint.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            progress("Before-start checkpoint complete. Original import is also retained.")
        } finally { pending.delete() }
    }

    fun export(root: File, output: OutputStream, progress: (String) -> Unit = {}) {
        var count = 0
        ZipOutputStream(output.buffered()).use { zip ->
            visit(root) { file, relative ->
                zip.putNextEntry(ZipEntry(relative + if (file.isDirectory) "/" else ""))
                if (file.isFile) file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { interrupted(); val n = input.read(buffer); if (n < 0) break; zip.write(buffer, 0, n) }
                }
                zip.closeEntry()
                if (++count % 500 == 0) progress("Archiving $count entries…")
            }
        }
    }

    private fun visit(root: File, action: (File, String) -> Unit) {
        require(!Files.isSymbolicLink(root.toPath()) && root.isDirectory)
        fun walk(dir: File) {
            for (file in requireNotNull(dir.listFiles()) { "Cannot read ${dir.name}" }.sortedBy { it.name }) {
                interrupted()
                require(!Files.isSymbolicLink(file.toPath())) { "Refusing a symbolic link in runtime: ${file.name}" }
                require(file.isFile || file.isDirectory) { "Unsupported runtime entry: ${file.name}" }
                action(file, file.relativeTo(root).invariantSeparatorsPath)
                if (file.isDirectory) walk(file)
            }
        }
        walk(root)
    }

    private fun interrupted() { if (Thread.currentThread().isInterrupted) throw InterruptedException("Cancelled") }
    companion object { private const val RESERVE = 128L * 1024 * 1024 }
}
