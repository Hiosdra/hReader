package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GroupIntoDaysTest {

    @Test
    fun `an empty page has no days`() {
        assertTrue(emptyList<ArticleListEntry>().groupIntoDays().isEmpty())
    }

    @Test
    fun `consecutive articles from one day form a single run`() {
        val entries = listOf(at("2026-07-27T08:00:00Z"), at("2026-07-27T20:00:00Z"))

        val days = entries.groupIntoDays()

        assertEquals(1, days.size)
        assertEquals(0, days.single().startIndex)
        assertEquals(2, days.single().size)
    }

    @Test
    fun `each change of day starts a new run`() {
        val entries = listOf(
            at("2026-07-27T08:00:00Z"),
            at("2026-07-28T08:00:00Z"),
            at("2026-07-28T09:00:00Z"),
            at("2026-07-29T08:00:00Z")
        )

        val days = entries.groupIntoDays()

        assertEquals(listOf(0, 1, 3), days.map { it.startIndex })
        assertEquals(listOf(1, 2, 1), days.map { it.size })
    }

    @Test
    fun `the runs cover every article exactly once`() {
        val entries = List(7) { at("2026-07-2${it % 3 + 1}T08:00:00Z") }

        val days = entries.groupIntoDays()

        assertEquals(entries.size, days.sumOf { it.size })
        days.zipWithNext { previous, next ->
            assertEquals(previous.startIndex + previous.size, next.startIndex)
        }
    }

    @Test
    fun `days are read in the device time zone`() {
        val entry = at("2026-07-27T23:30:00Z")
        val expected = Instant.parse("2026-07-27T23:30:00Z")
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        assertEquals(expected, entry.let { listOf(it) }.groupIntoDays().single().date)
    }

    @Test
    fun `a run is labelled with its own day`() {
        val days = listOf(at("2026-07-27T12:00:00Z"), at("2026-07-28T12:00:00Z")).groupIntoDays()

        assertEquals(2, days.size)
        assertTrue(days[0].date.isBefore(days[1].date))
    }

    private fun at(timestamp: String) = ArticleListEntry(
        id = timestamp.hashCode().toLong(),
        title = timestamp,
        preview = null,
        author = null,
        publishedAt = Instant.parse(timestamp),
        feed = Feed(id = 1L, title = "Feed", siteUrl = null, feedUrl = "https://example.com/feed"),
        imageUrl = null
    )
}
