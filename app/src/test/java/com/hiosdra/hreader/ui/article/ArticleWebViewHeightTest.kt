package com.hiosdra.hreader.ui.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleWebViewHeightTest {

    @Test
    fun `normal content height is unchanged`() {
        assertEquals(120_000, safeArticleWebViewHeightPx(120_000))
        assertFalse(articleWebViewNeedsInternalScroll(120_000))
    }

    @Test
    fun `safe boundary remains available without internal scrolling`() {
        assertEquals(
            MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX,
            safeArticleWebViewHeightPx(MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX)
        )
        assertFalse(articleWebViewNeedsInternalScroll(MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX))
    }

    @Test
    fun `oversized content is capped and uses internal scrolling`() {
        val oversizedHeight = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX + 59_013

        assertEquals(
            MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX,
            safeArticleWebViewHeightPx(oversizedHeight)
        )
        assertTrue(articleWebViewNeedsInternalScroll(oversizedHeight))
    }

    @Test
    fun `non-positive content height maps to zero`() {
        assertEquals(0, safeArticleWebViewHeightPx(0))
        assertEquals(0, safeArticleWebViewHeightPx(-1))
        assertFalse(articleWebViewNeedsInternalScroll(0))
    }

    @Test
    fun `saved progress maps to the oversized WebView scroll range`() {
        assertEquals(
            29_507,
            articleWebViewRestoreScrollY(
                progress = 0.5f,
                contentHeightPx = 321_013,
                viewportHeightPx = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX
            )
        )
    }

    @Test
    fun `saved WebView progress is clamped to its scroll range`() {
        assertEquals(
            0,
            articleWebViewRestoreScrollY(
                progress = -1f,
                contentHeightPx = 321_013,
                viewportHeightPx = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX
            )
        )
        assertEquals(
            59_013,
            articleWebViewRestoreScrollY(
                progress = 2f,
                contentHeightPx = 321_013,
                viewportHeightPx = MAX_SAFE_ARTICLE_WEB_VIEW_HEIGHT_PX
            )
        )
    }
}
