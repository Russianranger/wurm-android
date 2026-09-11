package io.github.russianranger.wurmlauncher

import java.io.File

/** Retain low-rate observations when the verbose console rotates. Timestamps/PIDs identify attempts. */
class RuntimeObservationLog(private val file: File) {
    @Synchronized fun observe(line: String) {
        if (!line.startsWith("[runtime-memory] ") && !line.startsWith("[graphics-error] ") &&
            !line.contains(" TCP_PROBE_POLICY ") && !line.contains(" TCP_PROBE_SUMMARY ")) return
        if (file.length() > 512 * 1024) file.writeText(file.readText().takeLast(256 * 1024).substringAfter('\n'))
        file.appendText(line.take(4000) + "\n")
    }
    @Synchronized fun read(): String = if (file.isFile) file.readText() else "No runtime observations recorded.\n"
}
