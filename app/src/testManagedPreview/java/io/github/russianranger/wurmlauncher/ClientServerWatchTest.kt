package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class ClientServerWatchTest {
    @Test fun externalServerNeverInheritsStaleOwnedFailure() {
        val watch = ClientServerWatch()
        assertNull(watch.observe(false, false, "Error", "old attempt"))
        assertFalse(watch.requestDiagnostic(false, "Waiting for login response", 60))
    }
    @Test fun ownedServerExitExplainsWaitAndDoesNotSurviveANewAttempt() {
        val watch = ClientServerWatch()
        assertNull(watch.observe(true, true, "Running", "Adventure"))
        assertNull(watch.observe(true, true, "Stopping", "save pending"))
        assertTrue(watch.observe(false, false, "Error", "Child exited (0)")!!.contains("Child exited (0)"))
        assertTrue(watch.observe(false, false, "Stopped", "Stopped by user")!!.contains("Stopped by user"))
        assertNull(ClientServerWatch().observe(false, false, "Error", "old attempt"))
    }
    @Test fun diagnosticRequestsAreBoundedAndOnlyDuringConnectionWait() {
        val watch = ClientServerWatch()
        assertFalse(watch.requestDiagnostic(true, "Waiting for login response", 9))
        assertTrue(watch.requestDiagnostic(true, "Waiting for login response", 10))
        assertFalse(watch.requestDiagnostic(true, "Waiting for login response", 24))
        assertTrue(watch.requestDiagnostic(true, "Waiting for local authentication", 25))
        assertFalse(watch.requestDiagnostic(true, "Client game loop", 60))
        assertFalse(watch.requestDiagnostic(true, null, 60))
        assertTrue(watch.requestDiagnostic(true, "Waiting to retry connection", 60))
        assertTrue(ClientServerWatch().requestDiagnostic(true, "Waiting for login response", 10))
    }
}
