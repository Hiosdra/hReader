package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleListSnapshotTest {

    @Test
    fun `does not expose an old snapshot item after paging list shrinks`() {
        val snapshot = listOf(
            "article-0",
            "article-1",
            "article-2",
            "article-3",
            "article-4",
            "article-5",
            "article-6"
        )

        assertNull(snapshot.getIfInBounds(index = 6, upperBound = 6))
    }

    @Test
    fun `exposes snapshot item while it remains in the paging list`() {
        val snapshot = listOf(
            "article-0",
            "article-1",
            "article-2",
            "article-3",
            "article-4",
            "article-5",
            "article-6"
        )

        assertEquals("article-6", snapshot.getIfInBounds(index = 6, upperBound = 7))
    }
}
