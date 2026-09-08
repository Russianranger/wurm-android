package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ManagedWorldReportTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun observedPathsPortsAndSavedReportSurviveReopeningWithoutChangingInputs() {
        val root = temp.newFolder("work-test")
        val ini = File(root, "Adventure/wurm.ini").apply { parentFile!!.mkdirs() }
        val original = "DB_HOST=localhost\r\nDB_PASS=private-password\r\nRMI_PASSWORD=private-rmi\r\nDB_PORT=3306\r\n".toByteArray()
        ini.writeBytes(original)
        val file = File(temp.root, "report.txt")
        val report = ManagedWorldReport(file, root, ManagedLaunch("Adventure"))
        report.prepare()
        report.observe("[WurmARM64] GameFolder recognized: ${root.canonicalPath}/Adventure")
        report.observe("INFO: Database: jdbc:sqlite:localhost/sqlite/wurmitems.db (SQLite 3.53)")
        report.observe("[WurmARM64] Game port=-25536 query port=27016 mode=1 version=1.0.0.0")
        report.observe("[world] OPEN_FILE Adventure/top_layer.map")
        report.observe("[world] TCP_UNAVAILABLE tcp AccessDeniedException")
        report.event("LOOPBACK_READY 127.0.0.1:3724; POC returned")
        report.state("Child exited 0; normalStop=true")
        val saved = ManagedWorldReport.read(file, root, "Adventure")
        assertTrue(saved.contains("GAME_FOLDER Adventure"))
        assertTrue(saved.contains("JDBC_OPEN localhost/sqlite/wurmitems.db"))
        assertTrue(saved.contains("SHIM_PORTS game=40000 query=27016"))
        assertTrue(saved.contains("TCP_UNAVAILABLE tcp AccessDeniedException"))
        assertTrue(saved.contains("DB_HOST=localhost"))
        assertFalse(saved.contains("private-password"))
        assertFalse(saved.contains("RMI_PASSWORD"))
        assertArrayEquals(original, ini.readBytes())
        assertEquals(saved, ManagedWorldReport.read(File(file.path), File(root.path), "Adventure"))
        assertTrue(ManagedWorldReport.read(file, root, "Creative").startsWith("HISTORICAL REPORT"))
        assertTrue(ManagedWorldReport.read(file, null, "Adventure").startsWith("HISTORICAL REPORT"))
    }

    @Test fun ignoresUnknownLinesEscapingPathsCredentialsAndOldLaunchEvidence() {
        val root = temp.newFolder("work-test")
        val file = File(temp.root, "report.txt")
        val report = ManagedWorldReport(file, root, ManagedLaunch("Adventure"))
        report.prepare()
        listOf("INFO: password=private-secret", "INFO: Database: jdbc:sqlite:../escape.db (SQLite 3.53)",
            "INFO: Database: jdbc:sqlite:localhost/sqlite/a.db?password=private-secret (SQLite 3.53)",
            "[world] UNKNOWN private-secret", "[WurmARM64] GameFolder recognized: /outside/world", "[world] OPEN_FILE bad\nprivate-secret")
            .forEach(report::observe)
        val saved = file.readText()
        assertFalse(saved.contains("private-secret"))
        assertFalse(saved.contains("escape.db"))
        assertFalse(saved.contains("/outside/world"))
        report.observe("[world] OPEN_FILE Adventure/old.map")
        ManagedWorldReport(file, root, ManagedLaunch("Adventure")).prepare()
        assertFalse(file.readText().contains("old.map"))
        repeat(1000) { report.observe("[world] OPEN_FILE Adventure/map$it.map") }
        assertTrue(file.length() < 128 * 1024)
        assertTrue(file.readText().contains("REPORT_LIMIT"))
    }
}
