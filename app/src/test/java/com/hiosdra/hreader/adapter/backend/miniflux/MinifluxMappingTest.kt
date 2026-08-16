package com.hiosdra.hreader.adapter.backend.miniflux

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEnclosure
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEntriesResponse
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEntry
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MinifluxMappingTest {

    @Test
    fun `absent cursor starts before the first entry id`() {
        assertNull(null.toEntryIdCursor())
    }

    @Test
    fun `malformed cursor starts before the first entry id`() {
        assertNull("not-a-number".toEntryIdCursor())
    }

    @Test
    fun `cursor carries the entry id forward`() {
        assertEquals(4711L, "4711".toEntryIdCursor())
    }

    @Test
    fun `a full page resumes after its last entry id`() {
        val page = responseWith(entryCount = 50).toEntriesPage(limit = 50)

        assertEquals("50", page.cursor)
    }

    @Test
    fun `a partial page ends the pagination`() {
        assertNull(responseWith(entryCount = 12).toEntriesPage(limit = 50).cursor)
    }

    @Test
    fun `an empty page ends the pagination`() {
        val page = responseWith(entryCount = 0).toEntriesPage(limit = 50)

        assertNull(page.cursor)
        assertEquals(0, page.entries.size)
    }

    @Test
    fun `an offset date time is parsed into an instant`() {
        val entry = entry(publishedAt = "2026-01-15T08:30:00+01:00").toDomain()

        assertEquals(Instant.parse("2026-01-15T07:30:00Z"), entry.publishedAt)
    }

    @Test
    fun `a zulu timestamp is parsed into an instant`() {
        val entry = entry(publishedAt = "2026-01-15T07:30:00Z").toDomain()

        assertEquals(Instant.parse("2026-01-15T07:30:00Z"), entry.publishedAt)
    }

    @Test
    fun `an unparsable timestamp falls back to the epoch`() {
        assertEquals(Instant.EPOCH, entry(publishedAt = "not a date").toDomain().publishedAt)
        assertEquals(Instant.EPOCH, entry(publishedAt = null).toDomain().publishedAt)
    }

    @Test
    fun `the read status is mapped from the wire value`() {
        assertEquals(ArticleStatus.READ, entry(status = "read").toDomain().status)
        assertEquals(ArticleStatus.UNREAD, entry(status = "unread").toDomain().status)
        assertEquals(ArticleStatus.UNREAD, entry(status = null).toDomain().status)
    }

    @Test
    fun `enclosures without a url are dropped`() {
        val entry = entry(
            enclosures = listOf(
                MinifluxEnclosure(url = "https://example.com/a.jpg", mimeType = "image/jpeg"),
                MinifluxEnclosure(url = null, mimeType = "image/png"),
                MinifluxEnclosure(url = "  ", mimeType = "image/gif")
            )
        ).toDomain()

        assertEquals(1, entry.enclosures.size)
        assertEquals("https://example.com/a.jpg", entry.enclosures.first().url)
    }

    @Test
    fun `an entry without a feed still maps`() {
        val entry = MinifluxEntry(id = 1, feed = null).toDomain()

        assertEquals(0L, entry.feed.id)
        assertEquals("", entry.feed.feedUrl)
    }

    private fun entry(
        publishedAt: String? = "2026-01-15T07:30:00Z",
        status: String? = "unread",
        enclosures: List<MinifluxEnclosure> = emptyList()
    ) = MinifluxEntry(
        id = 1,
        title = "Title",
        url = "https://example.com/1",
        publishedAt = publishedAt,
        feed = MinifluxFeed(id = 1, title = "Feed", feedUrl = "https://example.com/feed"),
        enclosures = enclosures,
        status = status
    )

    private fun responseWith(entryCount: Int) = MinifluxEntriesResponse(
        total = entryCount,
        entries = (1..entryCount).map { MinifluxEntry(id = it.toLong(), title = "Entry $it") }
    )
}
