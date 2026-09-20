package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.domain.model.ArticleContentKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticlePresentationStateTest {

    @Test
    fun `selecting a page derives its stable list position`() {
        val state = ArticleNavigationState(
            entries = listOf(entry(1), entry(2), entry(3)),
            currentIndex = 1,
            listWindowStartIndex = 10,
            listSize = 30
        )

        val selected = state.selectIndex(2)

        assertEquals(2, selected.currentIndex)
        assertEquals(13, selected.currentListPosition)
        assertEquals(setOf(1L, 2L, 3L), selected.readerWindowIds())
    }

    @Test
    fun `resolving a list clamps the start page without AI or content state`() {
        val state = ArticleNavigationState()

        val resolved = state.resolveList(
            currentIndex = 4,
            windowStartIndex = 20,
            totalCount = 100
        )

        assertEquals(0, resolved.currentIndex)
        assertEquals(21, resolved.currentListPosition)
        assertEquals(100, resolved.listSize)
    }

    @Test
    fun `reading progress is clamped and trimmed with the content window`() {
        val progress = ArticleReadingProgressState(
            positions = mapOf(1L to -1f, 2L to 0.4f, 3L to 1.2f),
            loadedIds = setOf(1L, 2L, 3L)
        )
            .withPosition(1L, -1f)
            .withPosition(3L, 1.2f)
            .trimTo(setOf(2L, 3L))

        assertEquals(mapOf(2L to 0.4f, 3L to 1f), progress.positions)
        assertEquals(setOf(2L, 3L), progress.loadedIds)
        assertEquals(mapOf(3L to 1f), progress.withoutPosition(2L).positions)
    }

    @Test
    fun `loading content exposes feed fallback provenance without content dependencies`() {
        val entry = entry(1L)
        val state = ArticleUiState(
            navigation = ArticleNavigationState(entries = listOf(entry))
        )

        val provenance = state.getContentProvenance(entry.id)

        assertEquals(ArticleContentKind.FEED_FALLBACK, provenance.kind)
        assertEquals(entry.url, provenance.sourceUrl)
    }

    private fun entry(id: Long) = Entry(
        id = id,
        title = "Article $id",
        author = null,
        url = "https://example.com/$id",
        publishedAt = Instant.ofEpochSecond(id),
        content = "<p>Article $id</p>",
        feed = Feed(
            id = 1L,
            title = "Feed",
            siteUrl = null,
            feedUrl = "https://example.com/feed"
        ),
        readingTime = null
    )
}
