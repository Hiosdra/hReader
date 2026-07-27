package com.hiosdra.hreader.data.remote.miniflux

import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.miniflux.dto.EntriesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinifluxPagingTest {

    @Test
    fun `absent cursor starts at the first offset`() {
        assertEquals(0, null.toOffset())
    }

    @Test
    fun `malformed cursor falls back to the first offset`() {
        assertEquals(0, "not-a-number".toOffset())
    }

    @Test
    fun `cursor carries the offset forward`() {
        assertEquals(200, "200".toOffset())
    }

    @Test
    fun `a full page advances the cursor by the page size`() {
        val page = responseWith(entryCount = 50).toEntriesPage(offset = 100, limit = 50)

        assertEquals("150", page.cursor)
    }

    @Test
    fun `a partial page ends the pagination`() {
        val page = responseWith(entryCount = 12).toEntriesPage(offset = 100, limit = 50)

        assertNull(page.cursor)
    }

    @Test
    fun `an empty page ends the pagination`() {
        val page = responseWith(entryCount = 0).toEntriesPage(offset = 0, limit = 50)

        assertNull(page.cursor)
        assertEquals(0, page.entries.size)
    }

    private fun responseWith(entryCount: Int) = EntriesResponse(
        total = entryCount,
        entries = (1..entryCount).map { index ->
            Entry(
                id = index.toLong(),
                title = "Entry $index",
                author = null,
                url = "https://example.com/$index",
                publishedAt = "2026-01-01T00:00:00Z",
                content = null,
                feed = Feed(id = 1, title = "Feed", siteUrl = null, feedUrl = "https://example.com/feed"),
                readingTime = null
            )
        }
    )
}
