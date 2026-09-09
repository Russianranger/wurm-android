package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class ClientJvmPolicyTest {
    @Test fun onlyGameEntrySelectsSerialInOrdinaryClientFlow() {
        for (mode in listOf("start", "local")) {
            val stages = ClientJvmPolicy.stages(mode)
            assertEquals(listOf("inventory", "compat", "prepare-graphics", "entry"), stages)
            stages.dropLast(1).forEach { assertTrue(ClientJvmPolicy.arguments(it).isEmpty()) }
        }
        val options = ClientJvmPolicy.arguments("entry")
        assertTrue(options.contains("-XX:+UseSerialGC"))
        assertTrue(options.contains("-Dwurm.client.expectedGc=serial"))
        assertTrue(options.any { it.startsWith("-Xlog:gc=") })
        assertFalse(options.any { "DisableExplicitGC" in it || "UseEpsilonGC" in it })
    }
    @Test fun memoryComparisonUsesTwoExplicitIndependentCollectors() {
        assertEquals(listOf("memory-g1", "memory-serial"), ClientJvmPolicy.stages("memory"))
        assertTrue(ClientJvmPolicy.arguments("memory-g1").contains("-XX:+UseG1GC"))
        assertTrue(ClientJvmPolicy.arguments("memory-serial").contains("-XX:+UseSerialGC"))
    }
    @Test fun existingGraphicsAndInputDiagnosticsKeepTheirCollectorSelection() {
        for (mode in listOf("input", "render", "window")) {
            assertEquals(listOf(mode), ClientJvmPolicy.stages(mode))
            assertTrue(ClientJvmPolicy.arguments(mode).isEmpty())
        }
    }
}
