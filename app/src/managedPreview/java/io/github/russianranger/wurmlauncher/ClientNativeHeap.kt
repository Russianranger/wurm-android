package io.github.russianranger.wurmlauncher

import java.io.File

/** Preload before exec; never supplied to the server or the isolated JVM memory tests. */
internal object ClientNativeHeap {
    const val runtime = "libclang_rt.asan-aarch64-android.so"
    const val cxx = "libc++_shared.so"
    // HotSpot installs handlers for its deliberate fault-based runtime checks.
    // Instrumented ASan failures still report and abort independently of those handlers.
    const val options = "log_to_syslog=false:detect_leaks=0:abort_on_error=1:" +
        "allow_user_segv_handler=1:handle_segv=0:handle_sigbus=0:handle_sigfpe=0:" +
        "use_sigaltstack=0:allocator_may_return_null=1:malloc_context_size=20:" +
        "fast_unwind_on_malloc=1:symbolize=0"

    fun environment(stage: String, native: File): Map<String, String> {
        if (stage !in setOf("entry", "window", "render")) return emptyMap()
        val libraries = listOf(runtime, cxx).map { File(native, it) }
        check(libraries.all { it.isFile }) { "Native heap diagnostic libraries missing" }
        return mapOf("LD_PRELOAD" to libraries.joinToString(":") { it.absolutePath },
            "ASAN_OPTIONS" to options)
    }
}
