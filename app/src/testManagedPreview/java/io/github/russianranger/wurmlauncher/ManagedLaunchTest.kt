package io.github.russianranger.wurmlauncher

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ManagedLaunchTest {
    private val config = ManagedLaunch("Adventure")
    private val native = File("/app/native")
    private val home = File("/app/java")
    private val tmp = File("/app/files/session-test/tmp")
    private val runtime = File("/app/files/managed/world")
    private val helper = File(tmp.parentFile, "runtime-probe.jar")

    @Test fun preflightCanPreparePrivateOverlayWithoutLoadingGameClasses() {
        val args = config.arguments(native, home, tmp, runtime, helper, true)
        assertEquals(listOf("server.ServerPreflight", tmp.parent, runtime.path), args.takeLast(3))
        val cp = args[args.indexOf("-cp") + 1].split(':')
        assertFalse(cp.any { it.endsWith("/server.jar") || it == "server.jar" })
        assertTrue(cp.contains(helper.path))
    }
    @Test fun serverUsesOverlayBeforeOriginalAndRetainsPinnedSqliteAndPocOrder() {
        val args = config.arguments(native, home, tmp, runtime, helper, false)
        val cp = args[args.indexOf("-cp") + 1].split(':')
        assertEquals("wurm-arm64-poc.jar", cp.first())
        assertTrue(cp.indexOf("poc-lib/sqlite-jdbc-3.53.2.1.jar") < cp.indexOf("server.jar"))
        assertTrue(cp.indexOf("${tmp.parent}/server-sqlite.jar") in 0 until cp.indexOf("server.jar"))
        assertTrue(args.contains("-Dwurm.server.sqliteOverlay=${tmp.parent}/server-sqlite.jar"))
        assertTrue(args.contains("-Dwurm.server.firstErrors=/app/files/managed-first-errors.txt"))
        assertEquals(listOf("server.ManagedServerMain", "Adventure"), args.takeLast(2))
    }
    @Test fun storageAuditStillInvokesOnlyReadOnlyAudit() {
        val args = config.auditArguments(native, home, tmp, runtime, helper, File("/audit"), false)
        assertEquals(listOf("persistence.StorageAudit", runtime.path, "/audit", "Adventure", "check"), args.takeLast(5))
        assertFalse(args.contains("server.ServerPreflight"))
        assertFalse(args.any { it.startsWith("-Dwurm.server.") || it == "-Dwurm.probe.network=true" })
    }
}
