package com.hiosdra.hreader.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class ArticleListQueryTest {

    private val opened: Instant = Instant.parse("2026-07-29T10:00:00Z")
    private val later: Instant = Instant.parse("2026-07-29T10:05:00Z")
    private val query = ArticleListQuery(sessionStart = opened)

    @Test
    fun `changing feed starts a new visit`() {
        val moved = query.withFeed(feedId = 7L, now = later)

        assertEquals(7L, moved.feedId)
        assertEquals(later, moved.sessionStart)
    }

    @Test
    fun `asking for the same feed changes nothing`() {
        assertSame(query, query.withFeed(feedId = null, now = later))

        val onFeed = query.copy(feedId = 7L)
        assertSame(onFeed, onFeed.withFeed(feedId = 7L, now = later))
    }

    @Test
    fun `switching to starred starts a new visit`() {
        val starred = query.withStarredOnly(starredOnly = true, now = later)

        assertEquals(true, starred.starredOnly)
        assertEquals(later, starred.sessionStart)
    }

    @Test
    fun `showing read articles keeps the visit going`() {
        val widened = query.withIncludeRead(true)

        assertEquals(true, widened.includeRead)
        assertEquals(opened, widened.sessionStart)
    }

    @Test
    fun `searching keeps the visit going`() {
        val searched = query.withSearch("kotlin")

        assertEquals("kotlin", searched.searchQuery)
        assertEquals(opened, searched.sessionStart)
    }

    @Test
    fun `repeating a value never restarts the visit`() {
        assertSame(query, query.withIncludeRead(false))
        assertSame(query, query.withSearch(""))
        assertSame(query, query.withStarredOnly(starredOnly = false, now = later))
    }
}
