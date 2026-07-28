package com.hiosdra.hreader.data.local.repository

import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.model.ArticleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ArticleReconciliationTest {

    private val now: Instant = Instant.parse("2026-07-28T10:00:00Z")

    @Test
    fun `an article read on another client becomes read locally`() {
        val local = article(status = ArticleStatus.UNREAD)
        val remote = article(status = ArticleStatus.READ)

        val merged = remote.reconciledWith(local, now)

        assertEquals(ArticleStatus.READ, merged.status)
        assertFalse(merged.pendingSync)
    }

    @Test
    fun `an article marked unread on another client becomes unread locally`() {
        val local = article(status = ArticleStatus.READ)
        val remote = article(status = ArticleStatus.UNREAD)

        assertEquals(ArticleStatus.UNREAD, remote.reconciledWith(local, now).status)
    }

    @Test
    fun `a status change that has not reached the backend survives the sync`() {
        val local = article(status = ArticleStatus.READ, pendingSync = true)
        val remote = article(status = ArticleStatus.UNREAD)

        val merged = remote.reconciledWith(local, now)

        assertEquals(ArticleStatus.READ, merged.status)
        assertTrue("It stays queued until the backend accepts it", merged.pendingSync)
    }

    @Test
    fun `a new article is taken as the backend sends it`() {
        val remote = article(status = ArticleStatus.UNREAD)

        assertEquals(remote, remote.reconciledWith(null, now))
    }

    @Test
    fun `the backend wins on everything other than a queued status`() {
        val local = article(title = "Stale title", status = ArticleStatus.UNREAD)
        val remote = article(title = "Corrected title", status = ArticleStatus.UNREAD)

        assertEquals("Corrected title", remote.reconciledWith(local, now).title)
    }

    @Test
    fun `an article that arrives read gets its read time stamped`() {
        val remote = article(status = ArticleStatus.READ)

        assertEquals(now, remote.reconciledWith(null, now).readAt)
    }

    @Test
    fun `an already recorded read time is not moved forward`() {
        val readEarlier = Instant.parse("2026-07-01T08:00:00Z")
        val local = article(status = ArticleStatus.READ, readAt = readEarlier)
        val remote = article(status = ArticleStatus.READ)

        assertEquals(readEarlier, remote.reconciledWith(local, now).readAt)
    }

    @Test
    fun `going back to unread clears the read time`() {
        val local = article(status = ArticleStatus.READ, readAt = now)
        val remote = article(status = ArticleStatus.UNREAD)

        assertNull(remote.reconciledWith(local, now).readAt)
    }

    @Test
    fun `a queued read keeps its local read time`() {
        val readEarlier = Instant.parse("2026-07-01T08:00:00Z")
        val local = article(status = ArticleStatus.READ, pendingSync = true, readAt = readEarlier)
        val remote = article(status = ArticleStatus.UNREAD)

        assertEquals(readEarlier, remote.reconciledWith(local, now).readAt)
    }

    private fun article(
        title: String = "Title",
        status: ArticleStatus = ArticleStatus.UNREAD,
        pendingSync: Boolean = false,
        readAt: Instant? = null
    ) = ArticleEntity(
        id = "1",
        title = title,
        author = null,
        url = "https://example.com/1",
        publishedAt = Instant.EPOCH,
        content = null,
        feedId = 1L,
        readingTime = null,
        enclosures = emptyList(),
        status = status,
        pendingSync = pendingSync,
        readAt = readAt
    )
}
