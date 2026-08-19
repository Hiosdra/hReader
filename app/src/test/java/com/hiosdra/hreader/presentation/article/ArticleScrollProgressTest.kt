package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
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
    fun `list progress is zero before the list has measured`() {
        assertEquals(
            0f,
            articleListScrollProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                totalItemsCount = 20,
                averageItemSizePx = 0,
                viewportSizePx = 1000,
                isAtEnd = false
            )
        )
    }

    @Test
    fun `list progress uses the visible item window`() {
        assertEquals(
            0.5f,
            articleListScrollProgress(
                firstVisibleItemIndex = 5,
                firstVisibleItemScrollOffset = 0,
                totalItemsCount = 20,
                averageItemSizePx = 100,
                viewportSizePx = 1000,
                isAtEnd = false
            ),
            0.001f
        )
    }

    @Test
    fun `list progress reaches the end when the list cannot scroll forward`() {
        assertEquals(
            1f,
            articleListScrollProgress(
                firstVisibleItemIndex = 12,
                firstVisibleItemScrollOffset = 40,
                totalItemsCount = 20,
                averageItemSizePx = 100,
                viewportSizePx = 1000,
                isAtEnd = true
            )
        )
    }

    @Test
    fun `web view progress is zero when content does not exceed the viewport`() {
        assertEquals(0f, readerWebViewScrollProgress(40, 1000f, 1000f))
        assertEquals(0f, readerWebViewScrollProgress(40, 1000f, 1200f))
    }

    @Test
    fun `web view progress is clamped to the scrollable content`() {
        assertEquals(0.5f, readerWebViewScrollProgress(500, 2000f, 1000f), 0.001f)
        assertEquals(1f, readerWebViewScrollProgress(1200, 2000f, 1000f))
    }
}
