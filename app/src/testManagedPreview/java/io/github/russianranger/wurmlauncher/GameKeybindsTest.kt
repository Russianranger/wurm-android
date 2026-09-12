package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class GameKeybindsTest {
    private val id="01234567-0123-0123-0123-012345678901"
    private fun response()=Properties().apply {
        setProperty("request",id); setProperty("status","ok"); setProperty("revision","a".repeat(64))
        setProperty("editable","true"); setProperty("keyChoices","W,UP,E"); setProperty("count","1")
        setProperty("item.0.action","MOVE_FORWARD"); setProperty("item.0.label","Move forward")
        setProperty("item.0.category","Movement"); setProperty("item.0.keys","W,UP")
    }
    @Test fun responseIdentityAndFailureAreEnforced() {
        assertTrue(runCatching { GameKeybinds.parse(response(),"another request") }.isFailure)
        val p=response(); p.setProperty("status","error"); p.setProperty("notice","Not ready")
        assertTrue(runCatching { GameKeybinds.parse(p,id) }.exceptionOrNull()!!.message!!.contains("Not ready"))
    }
    @Test fun normalizeModifiersAndPreserveMultipleKeysAndUnbinding() {
        val data=GameKeybinds.parse(response(),id); val action=data.actions.single()
        assertTrue(GameKeybinds.command(id,data,action,"w, shift+ctrl+e").endsWith(" W,CTRL+SHIFT+E"))
        assertTrue(GameKeybinds.command(id,data,action,"").endsWith(" -"))
        for (bad in listOf("W,", "W,W", "CTRL+CTRL+E", "Z", "W\nSTOP", "CTRL+SHIFT+E,SHIFT+CTRL+E"))
            assertTrue(bad,runCatching { GameKeybinds.command(id,data,action,bad) }.isFailure)
        assertTrue(runCatching { GameKeybinds.command(id,data.copy(editable=false),action,"E") }.isFailure)
    }
    @Test fun resolutionDefaultsTo720pAndKeepsExplicitChoices() {
        assertEquals("1280x720",GraphicsOptions.resolution(null))
        assertEquals("1280x720",GraphicsOptions.resolution("unknown"))
        assertEquals("800x480",GraphicsOptions.resolution("800x480"))
        assertEquals("960x540",GraphicsOptions.resolution("960x540"))
    }
}
