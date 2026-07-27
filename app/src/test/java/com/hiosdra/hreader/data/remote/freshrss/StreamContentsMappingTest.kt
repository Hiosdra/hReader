package com.hiosdra.hreader.data.remote.freshrss

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamContent
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamContentsResponse
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamEnclosure
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamItem
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamLink
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StreamContentsMappingTest {

    @Test
    fun `hexadecimal item id is decoded to its numeric form`() {
        val entry = itemWith(id = "tag:google.com,2005:reader/item/00000000000005a7").toEntry()

        assertEquals(1447L, entry.id)
    }

    @Test
    fun `hexadecimal item id made of digits only is not read as decimal`() {
        val entry = itemWith(id = "tag:google.com,2005:reader/item/0000000000000123").toEntry()

        assertEquals(291L, entry.id)
    }

    @Test
    fun `a short form id is read as decimal even when it looks hexadecimal`() {
        val entry = itemWith(id = "1721234567890123").toEntry()

        assertEquals(1721234567890123L, entry.id)
    }

    @Test
    fun `numeric id provided by FreshRSS takes precedence`() {
        val entry = itemWith(
            id = "tag:google.com,2005:reader/item/00000000000005a7",
            numericId = "1447"
        ).toEntry()

        assertEquals(1447L, entry.id)
    }

    @Test
    fun `read state category maps to read status`() {
        val read = itemWith(categories = listOf("user/-/state/com.google/read")).toEntry()
        val unread = itemWith(categories = listOf("user/-/state/com.google/reading-list")).toEntry()

        assertEquals(ArticleStatus.READ, read.status)
        assertEquals(ArticleStatus.UNREAD, unread.status)
    }

    @Test
    fun `the published unix timestamp becomes an instant`() {
        val entry = itemWith(published = 1_700_000_000L).toEntry()

        assertEquals(Instant.ofEpochSecond(1_700_000_000L), entry.publishedAt)
    }

    @Test
    fun `a missing published timestamp falls back to the epoch`() {
        assertEquals(Instant.EPOCH, itemWith(published = null).toEntry().publishedAt)
    }

    @Test
    fun `feed id is taken from the origin stream id`() {
        val entry = itemWith(origin = StreamOrigin(streamId = "feed/42", title = "Blog", htmlUrl = "https://blog.example")).toEntry()

        assertEquals(42L, entry.feed.id)
        assertEquals("Blog", entry.feed.title)
        assertEquals("https://blog.example", entry.feed.siteUrl)
    }

    @Test
    fun `content falls back to the summary when no full content is present`() {
        val entry = itemWith(summary = StreamContent("<p>Summary body</p>")).toEntry()

        assertEquals("<p>Summary body</p>", entry.content)
    }

    @Test
    fun `enclosures without a link are skipped`() {
        val entry = itemWith(
            enclosure = listOf(
                StreamEnclosure(href = "https://example.com/a.jpg", type = "image/jpeg"),
                StreamEnclosure(href = null, type = "image/png")
            )
        ).toEntry()

        assertEquals(1, entry.enclosures.size)
        assertEquals("https://example.com/a.jpg", entry.enclosures.first().url)
    }

    @Test
    fun `blank continuation is treated as the last page`() {
        val page = StreamContentsResponse(items = emptyList(), continuation = "  ").toEntriesPage()

        assertNull(page.cursor)
    }

    @Test
    fun `continuation is preserved when more pages are available`() {
        val page = StreamContentsResponse(items = emptyList(), continuation = "1519").toEntriesPage()

        assertEquals("1519", page.cursor)
    }

    private fun StreamItem.toEntry() =
        StreamContentsResponse(items = listOf(this), continuation = null).toEntriesPage().entries.single()

    private fun itemWith(
        id: String = "tag:google.com,2005:reader/item/0000000000000001",
        numericId: String? = null,
        published: Long? = 0L,
        categories: List<String> = emptyList(),
        origin: StreamOrigin? = StreamOrigin(streamId = "feed/1"),
        summary: StreamContent? = null,
        enclosure: List<StreamEnclosure> = emptyList()
    ) = StreamItem(
        id = id,
        numericId = numericId,
        title = "Title",
        author = null,
        published = published,
        canonical = listOf(StreamLink(href = "https://example.com/article")),
        alternate = emptyList(),
        summary = summary,
        content = null,
        categories = categories,
        origin = origin,
        enclosure = enclosure
    )
}
