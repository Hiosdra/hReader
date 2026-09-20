package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleReadAutomationTest {

    @Test
    fun `disabled feed does not mark an unread article automatically`() {
        assertFalse(article(feedAutoMarkRead = false).let(::shouldAutomaticallyMarkRead))
    }

    @Test
    fun `enabled feed marks unread article but never rewrites a read article`() {
        assertTrue(article(feedAutoMarkRead = true).let(::shouldAutomaticallyMarkRead))
        assertFalse(
            article(feedAutoMarkRead = true, status = ArticleStatus.READ)
                .let(::shouldAutomaticallyMarkRead)
        )
    }

    private fun article(
        feedAutoMarkRead: Boolean,
        status: ArticleStatus = ArticleStatus.UNREAD
    ) = Entry(
        id = 1L,
        title = "Article",
        author = null,
        url = "https://example.com/article",
        publishedAt = Instant.EPOCH,
        content = null,
        feed = Feed(
            id = 2L,
            title = "Feed",
            siteUrl = null,
            feedUrl = "https://example.com/feed",
            autoMarkRead = feedAutoMarkRead
        ),
        readingTime = null,
        status = status
    )
}
