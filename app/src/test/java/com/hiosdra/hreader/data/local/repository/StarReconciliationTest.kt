package com.hiosdra.hreader.data.local.repository

import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.model.ArticleStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StarReconciliationTest {

    private val now: Instant = Instant.parse("2026-07-29T10:00:00Z")

    @Test
    fun `a star waiting to be pushed outranks what the backend reports`() {
        val fetched = article(starred = false)
        val local = article(starred = true, starredPendingSync = true)

        val merged = fetched.reconciledWith(local, now)

        assertTrue(merged.starred)
        assertTrue(merged.starredPendingSync)
    }

    @Test
    fun `an unstar waiting to be pushed also survives`() {
        val fetched = article(starred = true)
        val local = article(starred = false, starredPendingSync = true)

        val merged = fetched.reconciledWith(local, now)

        assertFalse(merged.starred)
        assertTrue(merged.starredPendingSync)
    }

    @Test
    fun `with nothing queued the backend decides`() {
        val fetched = article(starred = true)
        val local = article(starred = false, starredPendingSync = false)

        val merged = fetched.reconciledWith(local, now)

        assertTrue(merged.starred)
        assertFalse(merged.starredPendingSync)
    }

    @Test
    fun `an article the cache has never seen keeps what was fetched`() {
        val merged = article(starred = true).reconciledWith(null, now)

        assertTrue(merged.starred)
        assertFalse(merged.starredPendingSync)
    }

    private fun article(
        starred: Boolean,
        starredPendingSync: Boolean = false
    ) = ArticleEntity(
        id = "1",
        title = "Article",
        author = null,
        url = "https://example.com/1",
        publishedAt = now,
        content = null,
        feedId = 1L,
        readingTime = null,
        enclosures = emptyList(),
        status = ArticleStatus.UNREAD,
        starred = starred,
        starredPendingSync = starredPendingSync
    )
}
