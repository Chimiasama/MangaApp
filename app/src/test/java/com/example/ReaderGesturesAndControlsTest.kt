package com.example

import com.example.ui.screens.getNextZoomScale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderGesturesAndControlsTest {

    @Test
    fun testGetNextZoomScaleCycle() {
        // Initial scale 1.0 -> 1.75
        var scale = getNextZoomScale(1.0f)
        assertEquals(1.75f, scale, 0.01f)

        // 1.75 -> 2.75
        scale = getNextZoomScale(scale)
        assertEquals(2.75f, scale, 0.01f)

        // 2.75 -> 4.0
        scale = getNextZoomScale(scale)
        assertEquals(4.0f, scale, 0.01f)

        // 4.0 -> 1.0 (reset)
        scale = getNextZoomScale(scale)
        assertEquals(1.0f, scale, 0.01f)
    }

    @Test
    fun testGetNextZoomScaleEdgeValues() {
        assertEquals(1.75f, getNextZoomScale(1.0f), 0.01f)
        assertEquals(1.75f, getNextZoomScale(1.39f), 0.01f)
        assertEquals(2.75f, getNextZoomScale(1.4f), 0.01f)
        assertEquals(2.75f, getNextZoomScale(2.29f), 0.01f)
        assertEquals(4.0f, getNextZoomScale(2.3f), 0.01f)
        assertEquals(4.0f, getNextZoomScale(3.59f), 0.01f)
        assertEquals(1.0f, getNextZoomScale(3.6f), 0.01f)
        assertEquals(1.0f, getNextZoomScale(4.5f), 0.01f)
    }
}
