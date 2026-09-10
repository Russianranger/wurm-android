package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class TouchPointerTest {
    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>?) {
        assertNotNull(actual)
        assertEquals(expected.first, actual!!.first, .00001f)
        assertEquals(expected.second, actual.second, .00001f)
    }
    @Test fun mapsLetterboxedImageAndRejectsBorders() {
        assertPoint(.5f to .5f, FramePointerGeometry.point(960f, 250f, 1920, 500, 960, 540))
        assertNull(FramePointerGeometry.point(100f, 250f, 1920, 500, 960, 540))
        assertPoint(0f to .5f, FramePointerGeometry.point(100f, 250f, 1920, 500, 960, 540, true))
        assertPoint(.5f to 0f, FramePointerGeometry.point(480f, 230f, 960, 1000, 960, 540))
        assertNull(FramePointerGeometry.point(480f, 100f, 960, 1000, 960, 540))
        assertPoint(1f to 1f, FramePointerGeometry.point(960f, 540f, 960, 540, 960, 540))
        assertNull(FramePointerGeometry.point(Float.NaN, 0f, 960, 540, 960, 540))
        assertNull(FramePointerGeometry.point(0f, 0f, 0, 540, 960, 540))
    }
    @Test fun tapMovesBeforePressAndReleasesAtFinalPosition() {
        val events = mutableListOf<String>()
        val pointer = TouchPointer { events.add(it); true }
        assertTrue(pointer.down(.25f to .75f))
        pointer.up(.3f to .8f)
        assertEquals(listOf("POINT 0.25 0.75", "BUTTON 0 1", "POINT 0.3 0.8", "BUTTON 0 0"), events)
        assertFalse(pointer.held)
        pointer.cancel()
        assertEquals(4, events.size)
    }
    @Test fun dragAndCancelReleaseWithoutAnotherClick() {
        val events = mutableListOf<String>()
        val pointer = TouchPointer { events.add(it); true }
        pointer.down(0f to 0f); pointer.move(1f to 1f); pointer.cancel(); pointer.up(null)
        assertEquals(listOf("POINT 0.0 0.0", "BUTTON 0 1", "POINT 1.0 1.0", "BUTTON 0 0"), events)
    }
    @Test fun unavailableInputCannotQueueAnOrphanPress() {
        val events = mutableListOf<String>()
        val pointer = TouchPointer { events.add(it); false }
        assertFalse(pointer.down(.5f to .5f))
        pointer.cancel()
        assertEquals(listOf("POINT 0.5 0.5"), events)
    }
}
