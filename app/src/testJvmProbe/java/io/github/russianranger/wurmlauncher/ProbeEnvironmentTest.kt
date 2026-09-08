package io.github.russianranger.wurmlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Exercise a real Linux JDK 17 launcher, not a copy of RequiresSetenv's logic.
 * Android loading is a separate physical-device gate.
 */
class ProbeEnvironmentTest {
    @Test fun jliAvoidsReexecWithTheAppEnvironment() {
        assumeTrue(System.getProperty("os.name") == "Linux" && System.getProperty("java.specification.version") == "17")
        val home = File(System.getProperty("java.home"))
        val native = File(home, "lib")
        val environment = ProbeEnvironment.create(home, native, File(System.getProperty("java.io.tmpdir")))
        // Negative control: the order shipped in 0.3.0 really does trigger exec.
        val previous = environment + ("LD_LIBRARY_PATH" to
            "${native.absolutePath}:${home.absolutePath}/lib:${home.absolutePath}/lib/server")
        val oldLog = launch(home, previous)
        assertTrue(oldLog, oldLog.contains("mustsetenv: TRUE"))
        assertTrue(oldLog, oldLog.contains("TRACER_MARKER:About to EXEC"))

        val log = launch(home, environment)
        assertTrue(log, log.contains("mustsetenv: FALSE"))
        assertFalse(log, log.contains("mustsetenv: TRUE"))
        assertFalse(log, log.contains("TRACER_MARKER:About to EXEC"))
    }

    private fun launch(home: File, environment: Map<String, String>): String {
        val output = File.createTempFile("wurm-jli-launch-", ".log")
        val builder = ProcessBuilder(File(home, "bin/java").absolutePath, "-version")
            .redirectErrorStream(true).redirectOutput(output)
        builder.environment().apply { clear(); putAll(environment) }
        var process: Process? = null
        try {
            process = builder.start()
            assertTrue("Host JLI did not finish within ten seconds", process.waitFor(10, TimeUnit.SECONDS))
            val log = output.readText()
            assertEquals(log, 0, process.exitValue())
            return log
        } finally {
            process?.let { if (it.isAlive) { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) } }
            output.delete()
        }
    }
}
