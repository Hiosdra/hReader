package com.hiosdra.hreader.ui.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleReadStatusTest {

    @Test
    fun `read article offers action to mark it unread`() {
        assertEquals("Mark as unread", readStatusActionLabel(isRead = true))
    }

    @Test
    fun `unread article offers action to mark it read`() {
        assertEquals("Mark as read", readStatusActionLabel(isRead = false))
    }
}
