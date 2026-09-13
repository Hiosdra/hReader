package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleReadStateAnimationTest {

    @Test
    fun `bulk and sync updates do not request animation`() {
        assertFalse(
            shouldAnimateArticleReadState(
                requestedReadState = null,
                currentReadState = true
            )
        )
    }

    @Test
    fun `pending direct change does not animate before the model update`() {
        assertFalse(
            shouldAnimateArticleReadState(
                requestedReadState = true,
                currentReadState = false
            )
        )
    }

    @Test
    fun `individual idle changes keep their transition`() {
        assertTrue(
            shouldAnimateArticleReadState(
                requestedReadState = true,
                currentReadState = true
            )
        )
    }
}
