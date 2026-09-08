package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ControllerMappingTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun sharedMouseBindingRepeatsAndUnplugDoNotReleaseOtherOwners() {
        val events = mutableListOf<String>(); val m = ControllerMapping(ControllerProfile(), events::add)
        m.button(1,"A",true); m.button(1,"A",true); m.button(2,"RT",true)
        m.releaseDevice(1); assertEquals(listOf("BUTTON 0 1"), events)
        m.releaseDevice(2); assertEquals(listOf("BUTTON 0 1", "BUTTON 0 0"), events)
    }
    @Test fun sticksHaveDeadZoneHysteresisAndContinuousMouseMovement() {
        val events = mutableListOf<String>(); val m = ControllerMapping(ControllerProfile(), events::add)
        fun axes(x: Float, rx: Float = 0f, ry: Float = 0f) = m.axes(1,x,0f,rx,ry,0f,0f,0f,0f)
        axes(.1f); assertTrue(events.isEmpty())
        axes(.7f); axes(.51f); assertEquals(listOf("KEY 32 1"),events)
        axes(.4f); assertEquals("KEY 32 0",events.last())
        axes(0f,1f,1f); m.tick(.01f); m.tick(.01f)
        assertEquals(listOf("MOVE 7.0 -7.0", "MOVE 7.0 -7.0"),events.takeLast(2))
        m.reset(); val size = events.size; m.tick(.01f); assertEquals(size,events.size); assertEquals("RESET",events.last())
    }
    @Test fun hatsAndButtonEventsShareBindingsAndTriggersRelease() {
        val events = mutableListOf<String>(); val m = ControllerMapping(ControllerProfile(), events::add)
        m.button(1,"DpadUp",true)
        m.axes(1,0f,0f,0f,0f,0f,-1f,1f,0f)
        m.button(1,"DpadUp",false)
        assertFalse(events.contains("KEY 2 0"))
        m.axes(1,0f,0f,0f,0f,0f,0f,0f,0f)
        assertTrue(events.containsAll(listOf("KEY 2 0", "BUTTON 1 0")))
    }
    @Test fun configurationRoundTripWheelAndInvertY() {
        val file = File(temp.root,"controller.properties")
        val profile = ControllerProfile(ControllerProfile.DEFAULTS + ("X" to "WHEEL:120"), .2f,.3f,1000f,true)
        profile.save(file); assertEquals(profile,ControllerProfile.load(file))
        val events = mutableListOf<String>(); val m = ControllerMapping(ControllerProfile.load(file),events::add)
        m.button(1,"X",true); m.button(1,"X",true); m.button(1,"X",false)
        m.axes(1,0f,0f,0f,1f,0f,0f,0f,0f); m.tick(.01f)
        assertEquals(listOf("WHEEL 120", "MOVE 0.0 10.0"),events)
        ControllerProfile().save(file); assertEquals(ControllerProfile(),ControllerProfile.load(file))
    }
    @Test fun invalidProfilesAndAxesCannotProduceInvalidEvents() {
        for (p in listOf(ControllerProfile(deadZone=Float.NaN), ControllerProfile(mouseSpeed=0f), ControllerProfile(bindings=mapOf("A" to "KEY:999")))) {
            assertThrows(IllegalArgumentException::class.java) { p.validate() }
        }
        val events = mutableListOf<String>(); val m = ControllerMapping(ControllerProfile(),events::add)
        m.axes(1,Float.NaN,Float.NaN,Float.POSITIVE_INFINITY,0f,0f,0f,0f,0f); m.tick(.1f)
        assertTrue(events.isEmpty())
    }
}
