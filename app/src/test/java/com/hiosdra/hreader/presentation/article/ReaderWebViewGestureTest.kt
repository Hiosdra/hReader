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

    @Test
    fun `content height waits for two equal measurements`() {
        assertEquals(false, contentHeightIsSettled(0, 1200, 1))
        assertEquals(true, contentHeightIsSettled(1200, 1200, 2))
        assertEquals(false, contentHeightIsSettled(1200, 1400, 2))
    }

    @Test
    fun `content height tracker reports changes and then settles them`() {
        val tracker = ContentHeightStabilityTracker()

        assertEquals(ContentHeightUpdate(1200, false), tracker.update(1200))
        assertEquals(ContentHeightUpdate(1200, true), tracker.update(1200))
        assertEquals(ContentHeightUpdate(1400, false), tracker.update(1400))
        assertEquals(ContentHeightUpdate(1400, true), tracker.update(1400))
        assertNull(tracker.update(1400))
    }

    @Test
    fun `one forward sequence consumes the header before the body`() {
        val target = FakeArticleWebViewScrollTarget(maxScrollY = 1000)
        val controller = ArticleWebViewScrollController().apply { attachForTest(target) }

        assertEquals(-120f, controller.consumeComposeScrollDelta(-120f), 0f)
        assertEquals(120, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(0, oversizedArticleBodyScrollPx(target.scrollY, 200))

        assertEquals(-130f, controller.consumeComposeScrollDelta(-130f), 0f)
        assertEquals(200, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(50, oversizedArticleBodyScrollPx(target.scrollY, 200))
    }

    @Test
    fun `one move crossing the header boundary loses no delta`() {
        val target = FakeArticleWebViewScrollTarget(maxScrollY = 1000, initialScrollY = 190)
        val controller = ArticleWebViewScrollController().apply { attachForTest(target) }

        assertEquals(-30f, controller.consumeComposeScrollDelta(-30f), 0f)
        assertEquals(220, target.scrollY)
        assertEquals(200, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(20, oversizedArticleBodyScrollPx(target.scrollY, 200))
    }

    @Test
    fun `reverse sequence reaches the body start before revealing the header`() {
        val target = FakeArticleWebViewScrollTarget(maxScrollY = 1000, initialScrollY = 350)
        val controller = ArticleWebViewScrollController().apply { attachForTest(target) }

        assertEquals(100f, controller.consumeComposeScrollDelta(100f), 0f)
        assertEquals(200, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(50, oversizedArticleBodyScrollPx(target.scrollY, 200))

        assertEquals(100f, controller.consumeComposeScrollDelta(100f), 0f)
        assertEquals(150, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(0, oversizedArticleBodyScrollPx(target.scrollY, 200))
    }

    @Test
    fun `direction change uses the same continuous WebView offset`() {
        val target = FakeArticleWebViewScrollTarget(maxScrollY = 1000, initialScrollY = 180)
        val controller = ArticleWebViewScrollController().apply { attachForTest(target) }

        controller.consumeComposeScrollDelta(-80f)
        controller.consumeComposeScrollDelta(35f)

        assertEquals(225, target.scrollY)
        assertEquals(200, oversizedArticleHeaderScrollPx(target.scrollY, 200))
        assertEquals(25, oversizedArticleBodyScrollPx(target.scrollY, 200))
    }
}

private class FakeArticleWebViewScrollTarget(
    private val maxScrollY: Int,
    initialScrollY: Int = 0
) : ArticleWebViewScrollTarget {
    override var scrollY: Int = initialScrollY
        private set

    override fun scrollBy(deltaY: Int) {
        scrollY = (scrollY + deltaY).coerceIn(0, maxScrollY)
    }
}
