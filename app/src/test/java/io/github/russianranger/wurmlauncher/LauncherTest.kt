package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class LauncherTest {
    @Test fun quotesShellMetacharactersLiterally() {
        assertEquals("'/a b/it'\"'\"'s/\$(id);x'", LaunchConfig.quote("/a b/it's/\$(id);x"))
    }

    @Test fun validatesPathsWithoutRequiringAppSandboxAccess() {
        assertNull(LaunchConfig(LaunchConfig.DEFAULT_RUNTIME, LaunchConfig.DEFAULT_JAVA).validate())
        assertNull(LaunchConfig("/a b/it's", "/java").validate())
        assertNotNull(LaunchConfig("relative", "/java").validate())
        assertNotNull(LaunchConfig("/runtime\nwhoami", "/java").validate())
        assertNotNull(LaunchConfig("/", "/java").validate())
    }

    @Test fun boundsIndividualLogLinesAndPreservesFinalLine() {
        val lines = mutableListOf<String>()
        val input = "x".repeat(100_000) + "\nnormal\r\nlast"
        RootServerController.consumeLines(ByteArrayInputStream(input.toByteArray()), lines::add)
        assertEquals(3, lines.size)
        assertTrue(lines[0].length < 4096)
        assertTrue(lines[0].endsWith("[truncated]"))
        assertEquals("normal", lines[1])
        assertEquals("last", lines[2])
    }

    @Test fun keepsOnlyLast500LogLines() {
        repeat(700) { ServerState.append("line $it") }
        val lines = ServerState.snapshot().log.lines()
        assertEquals(500, lines.size)
        assertEquals("line 200", lines.first())
        assertEquals("line 699", lines.last())
    }
}
