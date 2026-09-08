package io.github.russianranger.wurmlauncher

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Extract only the two checksum-pinned SQLite inputs. Never extract a Wurm world. */
class ProbeInputs(
    private val expected: Map<String, String> = BASELINE,
    private val archiveLimit: Long = 1024L * 1024 * 1024,
    private val jarLimit: Long = 64L * 1024 * 1024
) {
    fun install(input: InputStream, directory: File, progress: (Long) -> Unit = {}): List<File> {
        check(directory.isDirectory || directory.mkdirs())
        val archive = File(directory, "selected-runtime.zip")
        try {
            archive.outputStream().use { boundedCopy(input, it, archiveLimit, progress) }
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size <= 100_000) { "ZIP has too many entries." }
                return expected.map { (name, checksum) ->
                    val suffix = "poc-lib/$name"
                    val matches = entries.filter { entry ->
                        val path = entry.name
                        val prefix = path.removeSuffix(suffix)
                        !entry.isDirectory && path.endsWith(suffix) &&
                            (prefix.isEmpty() || (prefix.endsWith('/') && '/' !in prefix.dropLast(1) &&
                                prefix.dropLast(1) !in listOf("", ".", "..") && '\\' !in prefix))
                    }
                    require(matches.size == 1) { "Expected exactly one $suffix at ZIP root or inside one runtime folder." }
                    val output = File(directory, name)
                    zip.getInputStream(matches.single()).use { source ->
                        output.outputStream().use { target -> boundedCopy(source, target, jarLimit) {} }
                    }
                    require(sha256(output) == checksum) { "$name differs from the verified Thor input; no JVM test started." }
                    output
                }
            }
        } finally {
            archive.delete()
        }
    }

    private fun boundedCopy(input: InputStream, output: OutputStream, limit: Long, progress: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        var nextReport = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Cancelled")
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            require(copied <= limit) { "Selected ZIP/JAR exceeds the diagnostic size limit." }
            output.write(buffer, 0, count)
            if (copied >= nextReport) {
                progress(copied)
                nextReport = copied + 16L * 1024 * 1024
            }
        }
    }

    companion object {
        val BASELINE = linkedMapOf(
            "sqlite-jdbc-3.53.2.1.jar" to "f55e405ed96d5ffe629e05b7b51b059e1c7d64527c0cc90a972fbac06730ccc1",
            "sqlite-jdbc-3.53.2.1-natives-android.jar" to "011d4edb8d06012ced78d6aa675ffc85bf339d3cd640845684b80873ec5a6e97"
        )
        fun sha256(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
