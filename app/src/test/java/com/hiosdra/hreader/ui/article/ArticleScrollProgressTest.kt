package com.hiosdra.hreader.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleScrollProgressTest {
    @Test
    fun `progress is zero when content cannot scroll`() {
        assertEquals(0f, articleScrollProgress(value = 40, maxValue = 0))
    }

    @Test
    fun `progress is clamped to the content bounds`() {
        assertEquals(0f, articleScrollProgress(value = -1, maxValue = 100))
        assertEquals(1f, articleScrollProgress(value = 120, maxValue = 100))
    }

    @Test
    fun `progress maps back to a scroll offset`() {
        assertEquals(250, articleScrollOffset(progress = 0.25f, maxValue = 1000))
    }

    @Test
    fun `oversized WebView keeps saved progress after switching scroll modes`() {
        val savedProgress = 0.5f
        val contentHeightPx = 321_013f
        val viewportHeightPx = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX.toFloat()
        val restoredScrollY = articleWebViewRestoreScrollY(
            progress = savedProgress,
            contentHeightPx = contentHeightPx.toInt(),
            viewportHeightPx = viewportHeightPx.toInt()
        )

        assertEquals(
            savedProgress,
            articleReadingProgress(
                webViewNeedsInternalScroll = true,
                webViewScrollProgress = articleWebViewScrollProgress(
                    scrollY = restoredScrollY,
                    contentHeightPx = contentHeightPx,
                    viewportHeightPx = viewportHeightPx
                ),
                articleScrollValue = 0,
                articleScrollMaxValue = 1_000
            ),
            0.0001f
        )
    }

    @Test
    fun `normal feed progress uses the Compose scroll position`() {
        assertEquals(
            0.25f,
            articleReadingProgress(
                webViewNeedsInternalScroll = false,
                webViewScrollProgress = 0.9f,
                articleScrollValue = 250,
                articleScrollMaxValue = 1_000
            ),
            0.0001f
        )
    }

    @Test
    fun `reading progress is not persisted before position restoration`() {
        assertNull(
            articleReadingProgressForPersistence(
                positionRestored = false,
                webViewNeedsInternalScroll = true,
                webViewScrollProgress = 0f,
                articleScrollValue = 0,
                articleScrollMaxValue = 1_000
            )
        )
    }

    @Test
    fun `current WebView progress maps to a changed content height`() {
        val progress = 0.5f

        assertEquals(
            59_507,
            articleWebViewRestoreScrollY(
                progress = progress,
                contentHeightPx = 381_013,
                viewportHeightPx = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX
            )
        )
    }
}
