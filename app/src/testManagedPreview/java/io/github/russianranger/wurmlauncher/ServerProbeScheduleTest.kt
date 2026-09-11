package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class ServerProbeScheduleTest {
    @Test fun startupThenTenMinutesReadyCreatesFortyChecks() {
        val probes = ServerProbeSchedule()
        assertTrue(probes.due(0))
        assertFalse(probes.due(499_999_999))
        assertTrue(probes.due(500_000_000))
        probes.ready()
        var checks = 0
        for (tick in 1..1200) if (probes.due(500_000_000L + tick * 500_000_000L)) checks++
        assertEquals(40, checks)
    }
    @Test fun lateTicksDoNotBurstAndANewSessionStartsImmediately() {
        val probes = ServerProbeSchedule()
        assertTrue(probes.due(-10_000_000_000)) // nanoTime origin need not be positive
        probes.ready()
        assertTrue(probes.due(90_000_000_000))
        assertFalse(probes.due(90_000_000_001))
        assertFalse(probes.due(104_999_999_999))
        assertTrue(probes.due(105_000_000_000))
        assertTrue(ServerProbeSchedule().due(105_000_000_000))
    }
}
