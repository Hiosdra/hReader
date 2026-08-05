package com.hiosdra.hreader.ui.article

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
}
