package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleListItem
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReaderItem
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Enclosure
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePersistenceMappersTest {

    @Test
    fun articleListProjectionMapsFeedAndFirstImage() {
        val entry = ArticleListItem(
            id = "42",
            title = "Title",
            author = "Author",
            url = "https://example.com/article",
            publishedAt = Instant.EPOCH,
            preview = "Preview",
            readingTime = 3,
            leadImageUrl = "https://example.com/image",
            status = null,
            starred = false,
            backlogFetchedAt = Instant.EPOCH,
            feedId = 7L,
            feedTitle = null,
            feedSiteUrl = "https://example.com",
            feedUrl = null
        ).toListEntry()

        assertEquals(42L, entry.id)
        assertEquals(ArticleStatus.UNREAD, entry.status)
        assertEquals("https://example.com/image", entry.imageUrl)
        assertEquals(7L, entry.feed.id)
        assertEquals("", entry.feed.title)
        assertEquals("", entry.feed.feedUrl)
        assertEquals("https://example.com", entry.feed.siteUrl)
        assertTrue(entry.isBacklog)
    }

    @Test
    fun readerProjectionMapsMetadataWithoutLoadingArticleContent() {
        val entry = ArticleReaderItem(
            id = "42",
            title = "Title",
            author = "Author",
            url = "https://example.com/article",
            publishedAt = Instant.EPOCH,
            preview = "Preview",
            readingTime = 3,
            enclosures = emptyList(),
            status = ArticleStatus.READ,
            starred = true,
            backlogFetchedAt = null,
            feedId = 7L,
            feedTitle = "Feed",
            feedSiteUrl = null,
            feedUrl = "https://example.com/feed"
        ).toEntry()

        assertEquals(42L, entry.id)
        assertEquals("Preview", entry.preview)
        assertEquals(ArticleStatus.READ, entry.status)
        assertTrue(entry.starred)
        assertNull(entry.content)
        assertEquals("Feed", entry.feed.title)
        assertFalse(entry.isBacklog)
    }

    @Test
    fun entryMapperStoresDerivedPreviewAndFeedIdentity() {
        val feed = Feed(
            id = 7L,
            title = "Feed",
            siteUrl = "https://example.com",
            feedUrl = "https://example.com/feed"
        )
        val entry = Entry(
            id = 42L,
            title = "Title",
            author = null,
            url = "https://example.com/article",
            publishedAt = Instant.EPOCH,
            content = "<p>Hello <b>world</b></p>",
            feed = feed,
            readingTime = null
        ).toEntity()

        assertEquals("Hello world", entry.preview)
        assertEquals(7L, entry.feedId)
        assertEquals("https://example.com/article", entry.url)

        val feedEntity = feed.toArticleFeedEntity()
        assertEquals(feed.id, feedEntity.id)
        assertEquals(feed.title, feedEntity.title)
        assertEquals(feed.feedUrl, feedEntity.feedUrl)
    }
}
