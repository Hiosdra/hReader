package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `content height waits for two equal measurements`() {
        assertEquals(false, contentHeightIsSettled(0, 1200, 1))
        assertEquals(true, contentHeightIsSettled(1200, 1200, 2))
        assertEquals(false, contentHeightIsSettled(1200, 1400, 2))
    }

    @Test
    fun `parent-only movement does not dispatch a child move`() {
        val gesture = ReaderWebViewGestureState(touchSlop = 8f)
        gesture.start(600f)

        val move = gesture.move(y = 400f, consumedParentDelta = 200f)

        assertFalse(move)
        assertEquals(200f, gesture.touchEventOffsetY, 0f)
        assertTrue(gesture.shouldCancelChildOnUp)
    }

    @Test
    fun `child receives only the movement left after the parent reaches its limit`() {
        val gesture = ReaderWebViewGestureState(touchSlop = 8f)
        gesture.start(600f)
        assertFalse(gesture.move(y = 400f, consumedParentDelta = 200f))

        val move = gesture.move(y = 300f, consumedParentDelta = 0f)

        assertTrue(move)
        assertEquals(200f, gesture.touchEventOffsetY, 0f)
        assertFalse(gesture.shouldCancelChildOnUp)
    }

    @Test
    fun `parent-only reverse movement cancels the child gesture`() {
        val gesture = ReaderWebViewGestureState(touchSlop = 8f)
        gesture.start(200f)

        val move = gesture.move(y = 300f, consumedParentDelta = -100f)

        assertFalse(move)
        assertTrue(gesture.shouldCancelChildOnUp)
    }

    @Test
    fun `a new gesture does not inherit the previous handoff`() {
        val gesture = ReaderWebViewGestureState(touchSlop = 8f)
        gesture.start(600f)
        assertFalse(gesture.move(y = 400f, consumedParentDelta = 200f))

        gesture.start(600f)
        val move = gesture.move(y = 500f, consumedParentDelta = 0f)

        assertTrue(move)
        assertEquals(0f, gesture.touchEventOffsetY, 0f)
        assertFalse(gesture.shouldCancelChildOnUp)
    }
}
