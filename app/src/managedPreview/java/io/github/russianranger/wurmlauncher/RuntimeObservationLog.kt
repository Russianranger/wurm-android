package io.github.russianranger.wurmlauncher

import java.io.File

/** Retain low-rate observations when the verbose console rotates. Timestamps/PIDs identify attempts. */
class RuntimeObservationLog(private val file: File) {
    @Synchronized fun observe(line: String) {
        if (!line.startsWith("[runtime-memory] ") && !line.startsWith("[graphics-error] ") && !line.startsWith("[graphics-depth] ") &&
            !line.startsWith("[graphics-driver] ") && !line.startsWith("[graphics-capability] ") &&
            !line.startsWith("[client-gc] ") && !line.startsWith("[client-audio] ") &&
            !line.contains(" TCP_PROBE_POLICY ") && !line.contains(" TCP_PROBE_SUMMARY ") && !line.startsWith("[mods] ") &&
            !line.startsWith("[window] FRAME_TIMING ") && !(line.startsWith("[graphics-ui] ") &&
                (line.contains(" UI_TIMING ") || line.contains(" FRAME_READ_ERROR ") || line.contains(" FRAME_COPY_ERROR "))) &&
            !line.startsWith("[diagnostics] ")) return
        // Room for a 60–90 minute qualification run including five-second viewer
        // samples, while remaining bounded for repeated sessions.
        if (file.length() > 2 * 1024 * 1024) file.writeText(file.readText().takeLast(1024 * 1024).substringAfter('\n'))
        file.appendText(line.take(4000) + "\n")
    }
    @Synchronized fun read(): String = if (file.isFile) file.readText() else "No runtime observations recorded.\n"
}
