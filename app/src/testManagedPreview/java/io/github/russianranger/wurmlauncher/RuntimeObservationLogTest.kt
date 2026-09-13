package io.github.russianranger.wurmlauncher

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RuntimeObservationLogTest {
    @Test fun consoleNoiseDoesNotEvictMemoryAndReopeningRetainsBoundedHistory() {
        val home = Files.createTempDirectory("runtime-history").toFile()
        try {
            val file = java.io.File(home, "history.txt")
            val log = RuntimeObservationLog(file)
            log.observe("[runtime-memory] first")
            repeat(5000) { log.observe("[window] frame=$it") }
            assertEquals("[runtime-memory] first\n", RuntimeObservationLog(file).read())
            repeat(600) { log.observe("[runtime-memory] sample=$it " + "x".repeat(4000)) }
            log.observe("[graphics-error] origin=fixture")
            log.observe("[app] time TCP_PROBE_SUMMARY checks=40")
            log.observe("[mods] time CLIENT_MOD_READY livemap")
            log.observe("[graphics-ui] time UI_TIMING intervalP95Ms=33.33 payloadAllocations=1")
            log.observe("[window] FRAME_TIMING time=fixture pid=1 renderFps=30.0")
            log.observe("[diagnostics] time CLIENT_LOG_MODE normal")
            log.observe("[graphics-ui] FRAME_COPY_ERROR fixture")
            log.observe("[graphics-driver] time=fixture message=invalid uniform")
            log.observe("[client-gc] time WORLD_GC_REQUEST action=skipped")
            log.observe("[client-audio] time SOUND_FALLBACK_PREPARED")
            log.observe("[graphics-capability] time GPU_MEMORY_QUERY_UNAVAILABLE")
            assertTrue(file.length() <= 2 * 1024 * 1024 + 4001)
            val saved = RuntimeObservationLog(file).read()
            assertFalse(saved.contains("frame="))
            assertTrue(saved.startsWith("[runtime-memory] "))
            assertTrue(saved.contains("sample=599"))
            assertTrue(saved.contains("origin=fixture"))
            assertTrue(saved.contains("checks=40"))
            assertTrue(saved.contains("CLIENT_MOD_READY livemap"))
            assertTrue(saved.contains("intervalP95Ms=33.33 payloadAllocations=1"))
            assertTrue(saved.contains("FRAME_TIMING time=fixture pid=1"))
            assertTrue(saved.contains("CLIENT_LOG_MODE normal"))
            assertTrue(saved.contains("FRAME_COPY_ERROR fixture"))
            assertTrue(saved.contains("message=invalid uniform"))
            assertTrue(saved.contains("WORLD_GC_REQUEST action=skipped"))
            assertTrue(saved.contains("SOUND_FALLBACK_PREPARED"))
            assertTrue(saved.contains("GPU_MEMORY_QUERY_UNAVAILABLE"))
        } finally { home.deleteRecursively() }
    }
}
