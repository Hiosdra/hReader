package com.hiosdra.hreader.presentation.article

import org.junit.Assert.assertEquals
import org.junit.Test
import com.hiosdra.hreader.R

class ArticleReadStatusTest {

    @Test
    fun `read article offers action to mark it unread`() {
        assertEquals(R.string.article_mark_as_unread, readStatusActionLabel(isRead = true))
    }

    @Test
    fun `unread article offers action to mark it read`() {
        assertEquals(R.string.article_mark_as_read, readStatusActionLabel(isRead = false))
    }
}
