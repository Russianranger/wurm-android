package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ClientConnectionStateTest {
    private val flags = "connecting=false authenticated=true loggedIn=true splash=false disconnected=false transport=true pendingBytes=0"
    private fun line(phase: String, detail: String = flags) = "[connection] STATE phase=$phase elapsedMs=12000 $detail"
    @Test fun exposesAuthenticationLoginAndDenialSeparately() {
        val state = ClientConnectionState()
        assertEquals("Waiting for local authentication", state.observe(line("AUTH_WAIT"))?.phase)
        assertEquals("Waiting for login response", state.observe(line("LOGIN_WAIT"))?.phase)
        assertEquals("Login rejected", state.observe(line("LOGIN_DENIED", "loginMessage=fixture rejection"))?.phase)
        assertTrue(state.latest!!.detail.contains("fixture rejection"))
        assertFalse(state.gameLoopReached)
    }
    @Test fun rejectsMalformedAndIncompleteGameLoopEvidence() {
        val state = ClientConnectionState()
        assertNull(state.observe("[connection] STATE phase=UNKNOWN elapsedMs=1 other"))
        assertNull(state.observe(line("GAME_LOOP", "loggedIn=true")))
        assertNull(state.observe(line("GAME_LOOP", flags.replace("splash=false", "splash=true"))))
        assertNull(state.observe(line("GAME_LOOP", flags.replace("authenticated=true", "authenticated=false"))))
        assertNull(state.observe(line("GAME_LOOP", flags.replace("transport=true", "transport=trueX"))))
        assertFalse(state.gameLoopReached)
    }
    @Test fun keepsStageBudgetsAndReleasesOnlyAfterGameLoop() {
        val state = ClientConnectionState()
        val two = TimeUnit.MINUTES.toNanos(2); val five = TimeUnit.MINUTES.toNanos(5)
        assertTrue(state.timedOut("window", two)); assertFalse(state.timedOut("entry", two))
        assertFalse(state.timedOut("entry", five - 1)); assertTrue(state.timedOut("entry", five))
        state.observe(line("LOGIN_ACCEPTED")); assertTrue(state.timedOut("entry", five))
        state.observe(line("GAME_LOOP")); assertTrue(state.gameLoopReached)
        assertFalse(state.timedOut("entry", TimeUnit.DAYS.toNanos(1)))
        assertTrue(state.timedOut("window", two)); assertFalse(state.timedOut("input", five))
        assertTrue(state.timedOut("input", TimeUnit.MINUTES.toNanos(10)))
        assertTrue(ClientConnectionState().timedOut("entry", five))
    }
    @Test fun boundsStatusTextAndIgnoresUnrelatedLines() {
        val state = ClientConnectionState()
        assertNull(state.observe("[client] LOCAL_TICKET_CREATED bytes=39"))
        assertNull(state.observe("[connection] TCP_PROBE reachable=true"))
        assertNull(state.observe("Login successful"))
        assertEquals("Connection details unavailable", state.observe("[connection] MONITOR_UNAVAILABLE fixture ABI")?.phase)
        assertFalse(state.gameLoopReached)
        val update = state.observe(line("AUTH_WAIT", "x".repeat(10000)))!!
        assertTrue(update.detail.length < 1500)
    }
}
