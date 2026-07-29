package com.hiosdra.hreader.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeFromLeftEdgeTest {

    @Test
    fun `does not open without movement`() {
        assertFalse(shouldOpenAfterSwipe(offsetPx = 0f, velocityPxPerSecond = 5000f, thresholdPx = 100f))
    }

    @Test
    fun `opens once dragged past the threshold`() {
        assertTrue(shouldOpenAfterSwipe(offsetPx = 120f, velocityPxPerSecond = 0f, thresholdPx = 100f))
    }

    @Test
    fun `opens on a short fast flick`() {
        assertTrue(shouldOpenAfterSwipe(offsetPx = 20f, velocityPxPerSecond = 1500f, thresholdPx = 100f))
    }

    @Test
    fun `stays closed on a short slow drag`() {
        assertFalse(shouldOpenAfterSwipe(offsetPx = 20f, velocityPxPerSecond = 100f, thresholdPx = 100f))
    }
}
