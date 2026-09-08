package io.github.russianranger.wurmlauncher

import java.io.File

/** Environment supplied before exec, so the linker and JLI see the same paths. */
internal object ProbeEnvironment {
    fun create(home: File, native: File, tmp: File): Map<String, String> = linkedMapOf(
        "JAVA_HOME" to home.absolutePath,
        "TMPDIR" to tmp.absolutePath,
        "PATH" to "/system/bin",
        "LANG" to "en_US.UTF-8",
        // OpenJDK 17 RequiresSetenv() checks this prefix against GetJVMPath().
        // A different first entry triggers re-exec of JAVA_HOME/bin/java, which
        // deliberately isn't an executable in the Android writable JRE image.
        "LD_LIBRARY_PATH" to "${home.absolutePath}/lib/server:${home.absolutePath}/lib:${native.absolutePath}",
        "WURM_JLI_PATH" to File(native, "libjli.so").absolutePath,
        "_JAVA_LAUNCHER_DEBUG" to "1"
    )
}
