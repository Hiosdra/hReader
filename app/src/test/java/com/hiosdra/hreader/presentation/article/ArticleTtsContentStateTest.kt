package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTtsContentStateTest {
    @Test
    fun `empty content after loading is unavailable`() {
        assertEquals(
            ArticleTtsContentState.UNAVAILABLE,
            articleTtsContentState(content = "", contentLoadFinished = true)
        )
    }

    @Test
    fun `whitespace-only content after loading is unavailable`() {
        assertEquals(
            ArticleTtsContentState.UNAVAILABLE,
            articleTtsContentState(content = " \n\t", contentLoadFinished = true)
        )
    }

    @Test
    fun `markup without readable text after loading is unavailable`() {
        assertEquals(
            ArticleTtsContentState.UNAVAILABLE,
            articleTtsContentState(
                content = "<nav>Menu</nav><img src=\"only-an-image.png\">",
                contentLoadFinished = true
            )
        )
    }

    @Test
    fun `missing content before loading finishes is loading`() {
        assertEquals(
            ArticleTtsContentState.LOADING,
            articleTtsContentState(content = null, contentLoadFinished = false)
        )
    }

    @Test
    fun `non-empty content is available`() {
        assertEquals(
            ArticleTtsContentState.AVAILABLE,
            articleTtsContentState(content = "<p>Article text</p>", contentLoadFinished = false)
        )
    }
}
