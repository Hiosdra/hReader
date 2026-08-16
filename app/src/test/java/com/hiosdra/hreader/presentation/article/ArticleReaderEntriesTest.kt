package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.domain.model.OfflinePage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArticleReaderEntriesTest {

    @Test
    fun `temporary room omission keeps the pager order and previous entry`() {
        val previous = listOf(entry(1), entry(2, "Previous title"), entry(3))
        val latest = listOf(entry(3, "Updated title"), entry(1, "Updated first"))

        val result = mergeReaderEntries(listOf(1, 2, 3), latest, previous)

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        assertEquals("Updated first", result[0].title)
        assertEquals("Previous title", result[1].title)
        assertEquals("Updated title", result[2].title)
    }

    @Test
    fun `entry missing from both room snapshots is omitted`() {
        val result = mergeReaderEntries(
            ids = listOf(1, 2, 3),
            latestEntries = listOf(entry(1), entry(3)),
            previousEntries = emptyList()
        )

        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun `reader state keeps article payloads around the current page only`() {
        val entries = (1L..5L).map(::entry)
        val state = ArticleUiState(
            entries = entries,
            currentIndex = 2,
            content = entries.associate { it.id to "<p>${it.id}</p>" },
            leadImages = entries.associate { it.id to "https://example.com/${it.id}.jpg" },
            localImagePaths = entries.associate { it.id to mapOf("image" to "/tmp/${it.id}.jpg") },
            offlinePages = entries.associate { item ->
                item.id to OfflinePage(
                    entryId = item.id,
                    originalUrl = item.url,
                    baseUrl = "https://offline.hreader.local/article/${item.id}/",
                    html = "<p>${item.id}</p>",
                    resourceDirectory = "/tmp/${item.id}",
                    isComplete = true
                )
            }
        )

        val trimmed = state.trimReaderState()

        assertEquals(setOf(2L, 3L, 4L), trimmed.content.keys)
        assertEquals(setOf(2L, 3L, 4L), trimmed.leadImages.keys)
        assertEquals(setOf(2L, 3L, 4L), trimmed.localImagePaths.keys)
        assertEquals(setOf(2L, 3L, 4L), trimmed.offlinePages.keys)
    }

    private fun entry(id: Long, title: String = "Article $id") = Entry(
        id = id,
        title = title,
        author = null,
        url = "https://example.com/articles/$id",
        publishedAt = Instant.ofEpochSecond(id),
        content = "<p>$title</p>",
        feed = Feed(
            id = 1L,
            title = "Feed",
            siteUrl = null,
            feedUrl = "https://example.com/feed"
        ),
        readingTime = null
    )
}
