package io.github.russianranger.wurmlauncher

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ClientNativeHeapTest {
    @Test fun preloadIsLimitedToGraphicsChildrenAndOrderedBeforeCxx() {
        val root = Files.createTempDirectory("client-native-heap").toFile()
        try {
            for (stage in listOf("inventory", "compat", "prepare-graphics", "input", "memory-g1", "memory-serial", "server"))
                assertTrue(ClientNativeHeap.environment(stage, root).isEmpty())
            try {
                ClientNativeHeap.environment("entry", root)
                fail("Missing runtime should reject startup")
            } catch (_: IllegalStateException) {}
            File(root, ClientNativeHeap.runtime).writeText("fixture")
            File(root, ClientNativeHeap.cxx).writeText("fixture")
            for (stage in listOf("entry", "window", "render")) {
                val env = ClientNativeHeap.environment(stage, root)
                assertEquals(File(root, ClientNativeHeap.runtime).absolutePath + ":" +
                    File(root, ClientNativeHeap.cxx).absolutePath, env["LD_PRELOAD"])
                assertEquals("asan", env["WURM_HEAP_TAGGING"])
                assertTrue(env.getValue("ASAN_OPTIONS").contains("abort_on_error=1"))
                assertTrue(env.getValue("ASAN_OPTIONS").contains("log_to_syslog=false"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun sanitizerFailureTakesPrecedenceOverLastCompletedDraw() {
        val evidence = ClientCrashEvidence(10001, 0)
        evidence.observe("[graphics-trace] END seq=3018")
        evidence.observe("==123==ERROR: AddressSanitizer: heap-buffer-overflow on address 0x123")
        val summary = evidence.summary(134)
        assertTrue(summary.contains("AddressSanitizer: heap-buffer-overflow"))
        assertFalse(summary.contains("last graphics event"))
        assertFalse(ClientCrashEvidence(10001, 0).summary(134).contains("AddressSanitizer"))
    }
}
