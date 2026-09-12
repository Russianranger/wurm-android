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
            repeat(400) { log.observe("[runtime-memory] sample=$it " + "x".repeat(4000)) }
            log.observe("[graphics-error] origin=fixture")
            log.observe("[app] time TCP_PROBE_SUMMARY checks=40")
            log.observe("[mods] time CLIENT_MOD_READY livemap")
            assertTrue(file.length() <= 512 * 1024 + 4001)
            val saved = RuntimeObservationLog(file).read()
            assertFalse(saved.contains("frame="))
            assertTrue(saved.startsWith("[runtime-memory] "))
            assertTrue(saved.contains("sample=399"))
            assertTrue(saved.contains("origin=fixture"))
            assertTrue(saved.contains("checks=40"))
            assertTrue(saved.contains("CLIENT_MOD_READY livemap"))
        } finally { home.deleteRecursively() }
    }
}
