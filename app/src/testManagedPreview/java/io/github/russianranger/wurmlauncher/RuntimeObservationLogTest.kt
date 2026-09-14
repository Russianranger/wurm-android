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
            log.observe("[client-allocation] time observedAllocatedBytes=8388608 thread[1,main]=8388608")
            log.observe("[client-memory-test] time JOB class=fixture.Job allocatedBytes=4096")
            log.observe("[client-ui] FPS_APPLIED target=60 time=fixture pid=1")
            log.observe("[2026-09-14T01:00:00.000+0000][42][43][gc,heap  ] GC(7) Tenured: 800M->607M(989M)")
            log.observe("[2026-09-14T01:00:00.000+0000][42][43][gc       ] GC(7) Pause Full (Allocation Failure) 600ms")
            log.observe("[2026-09-14T01:00:00.000+0000][42][43][safepoint] Safepoint total=600ms")
            log.observe("[graphics] lookup name=not-evidence GC(7)")
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
            assertTrue(saved.contains("observedAllocatedBytes=8388608"))
            assertTrue(saved.contains("JOB class=fixture.Job allocatedBytes=4096"))
            assertTrue(saved.contains("FPS_APPLIED target=60 time=fixture pid=1"))
            assertTrue(saved.contains("Tenured: 800M->607M"))
            assertTrue(saved.contains("Pause Full (Allocation Failure)"))
            assertTrue(saved.contains("Safepoint total=600ms"))
            assertFalse(saved.contains("not-evidence"))
        } finally { home.deleteRecursively() }
    }
}
