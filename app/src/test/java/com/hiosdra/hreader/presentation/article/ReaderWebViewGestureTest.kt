package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWebViewGestureTest {

    @Test
    fun `movement inside touch slop has no direction`() {
        assertNull(readerGestureDirection(deltaX = 4f, deltaY = 5f, touchSlop = 8f))
    }

    @Test
    fun `vertical movement wins over a small horizontal drift`() {
        assertEquals(
            ReaderGestureDirection.Vertical,
            readerGestureDirection(deltaX = 18f, deltaY = 40f, touchSlop = 8f)
        )
    }

    @Test
    fun `horizontal pager movement requires clear horizontal direction`() {
        assertNull(readerGestureDirection(deltaX = 30f, deltaY = 27f, touchSlop = 8f))
        assertEquals(
            ReaderGestureDirection.Horizontal,
            readerGestureDirection(deltaX = 48f, deltaY = 20f, touchSlop = 8f)
        )
    }
}
