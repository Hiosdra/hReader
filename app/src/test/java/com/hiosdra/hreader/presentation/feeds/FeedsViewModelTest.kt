package com.hiosdra.hreader.presentation.feeds

import com.hiosdra.hreader.core.domain.model.Feed
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.Assert.*

class SubscriptionOrderTest {

    private val feeds = listOf(
        Feed(id = 1, title = "Zed Weekly", siteUrl = null, feedUrl = "https://zed.example.com/feed"),
        Feed(id = 2, title = "alpha times", siteUrl = null, feedUrl = "https://alpha.example.com/feed"),
        Feed(id = 3, title = "Beta Report", siteUrl = null, feedUrl = "https://beta.example.com/feed")
    )

    @Test
    fun `unread count comes before the title`() {
        val ordered = sortSubscriptions(feeds, mapOf(1L to 5, 2L to 1, 3L to 9))
        assertEquals(listOf("Beta Report", "Zed Weekly", "alpha times"), ordered.map { it.title })
    }

    @Test
    fun `feeds with the same count fall back to a case insensitive title`() {
        val ordered = sortSubscriptions(feeds, mapOf(1L to 3, 2L to 3, 3L to 3))
        assertEquals(listOf("alpha times", "Beta Report", "Zed Weekly"), ordered.map { it.title })
    }

    @Test
    fun `feeds missing from the counts sort as read`() {
        val ordered = sortSubscriptions(feeds, mapOf(1L to 1))
        assertEquals(listOf("Zed Weekly", "alpha times", "Beta Report"), ordered.map { it.title })
    }

    @Test
    fun `held positions survive counts that would reorder the rows`() {
        val settled = sortSubscriptions(feeds, mapOf(1L to 5, 2L to 1, 3L to 9))
        val rowOrder = settled.withIndex().associate { (position, feed) -> feed.id to position }

        val held = holdRowOrder(feeds, mapOf(1L to 0, 2L to 40, 3L to 0), rowOrder)

        assertEquals(settled.map { it.title }, held.map { it.title })
    }

    @Test
    fun `feeds nobody has placed yet go last`() {
        val added = Feed(id = 4, title = "Added Later", siteUrl = null, feedUrl = "https://added.example.com/feed")
        val rowOrder = mapOf(1L to 0, 2L to 1, 3L to 2)

        val held = holdRowOrder(feeds + added, mapOf(4L to 99), rowOrder)

        assertEquals(
            listOf("Zed Weekly", "alpha times", "Beta Report", "Added Later"),
            held.map { it.title }
        )
    }
}

class FeedsSearchTest {

    @Test
    fun `search functionality filters feeds by title`() {
        val feeds = listOf(
            Feed(id = 1, title = "Tech News", siteUrl = "https://tech.example.com", feedUrl = "https://tech.example.com/feed"),
            Feed(id = 2, title = "Science Daily", siteUrl = "https://science.example.com", feedUrl = "https://science.example.com/feed"),
            Feed(id = 3, title = "Programming Blog", siteUrl = "https://programming.example.com", feedUrl = "https://programming.example.com/feed")
        )
        
        // Test filtering by title (case-insensitive)
        val techResults = filterFeeds(feeds, "tech")
        assertEquals(1, techResults.size)
        assertEquals("Tech News", techResults[0].title)
        
        val scienceResults = filterFeeds(feeds, "science")
        assertEquals(1, scienceResults.size)
        assertEquals("Science Daily", scienceResults[0].title)
    }

    @Test
    fun `search functionality filters feeds by site URL`() {
        val feeds = listOf(
            Feed(id = 1, title = "Tech News", siteUrl = "https://tech.example.com", feedUrl = "https://tech.example.com/feed"),
            Feed(id = 2, title = "Science Daily", siteUrl = "https://science.example.com", feedUrl = "https://science.example.com/feed"),
            Feed(id = 3, title = "Programming Blog", siteUrl = "https://programming.example.com", feedUrl = "https://programming.example.com/feed")
        )
        
        // Test filtering by site URL
        val programmingResults = filterFeeds(feeds, "programming.example.com")
        assertEquals(1, programmingResults.size)
        assertEquals("Programming Blog", programmingResults[0].title)
    }

    @Test
    fun `empty search query shows all feeds`() {
        val feeds = listOf(
            Feed(id = 1, title = "Tech News", siteUrl = "https://tech.example.com", feedUrl = "https://tech.example.com/feed"),
            Feed(id = 2, title = "Science Daily", siteUrl = "https://science.example.com", feedUrl = "https://science.example.com/feed"),
            Feed(id = 3, title = "Programming Blog", siteUrl = "https://programming.example.com", feedUrl = "https://programming.example.com/feed")
        )
        
        val results = filterFeeds(feeds, "")
        assertEquals(3, results.size)
    }

    @Test
    fun `search is case insensitive`() {
        val feeds = listOf(
            Feed(id = 1, title = "Tech News", siteUrl = "https://tech.example.com", feedUrl = "https://tech.example.com/feed"),
            Feed(id = 2, title = "Science Daily", siteUrl = "https://science.example.com", feedUrl = "https://science.example.com/feed")
        )
        
        // Test case insensitivity
        val upperResults = filterFeeds(feeds, "TECH")
        assertEquals(1, upperResults.size)
        assertEquals("Tech News", upperResults[0].title)
        
        val mixedResults = filterFeeds(feeds, "ScIeNcE")
        assertEquals(1, mixedResults.size)
        assertEquals("Science Daily", mixedResults[0].title)
    }

    @Test
    fun `search with no matches returns empty list`() {
        val feeds = listOf(
            Feed(id = 1, title = "Tech News", siteUrl = "https://tech.example.com", feedUrl = "https://tech.example.com/feed"),
            Feed(id = 2, title = "Science Daily", siteUrl = "https://science.example.com", feedUrl = "https://science.example.com/feed")
        )
        
        val results = filterFeeds(feeds, "nonexistent")
        assertTrue(results.isEmpty())
    }

    /**
     * Extract the filtering logic for testing purposes
     */
    private fun filterFeeds(feeds: List<Feed>, query: String): List<Feed> {
        val normalizedQuery = query.lowercase().trim()
        return if (normalizedQuery.isEmpty()) {
            feeds
        } else {
            feeds.filter { feed ->
                feed.title.lowercase().contains(normalizedQuery) ||
                feed.siteUrl?.lowercase()?.contains(normalizedQuery) == true
            }
        }
    }
}

class FeedsViewModelTest {

    @Test
    fun `successful action with no success message stays quiet`() {
        val message = feedActionMessage(
            result = Result.success(Unit),
            success = null,
            failure = { "Could not rename: ${it.message}" }
        )

        assertNull(message)
    }

    @Test
    fun `failed action still exposes failure message`() {
        val message = feedActionMessage(
            result = Result.failure<Unit>(IllegalStateException("server rejected it")),
            success = null,
            failure = { "Could not rename: ${it.message}" }
        )

        assertEquals("Could not rename: server rejected it", message)
    }
}

class OpmlExportTest {

    @Test
    fun `missing output stream is reported as a failed write`() {
        assertFalse(writeOpml(null, "<opml/>"))
    }

    @Test
    fun `opml is written as utf8`() {
        val output = ByteArrayOutputStream()

        assertTrue(writeOpml(output, "zażółć"))
        assertEquals("zażółć", output.toString(Charsets.UTF_8.name()))
    }
}

class NextSubscriptionTest {

    private val feeds = listOf(
        Feed(id = 1, title = "First", siteUrl = null, feedUrl = "https://first.example.com/feed"),
        Feed(id = 2, title = "Second", siteUrl = null, feedUrl = "https://second.example.com/feed"),
        Feed(id = 3, title = "Third", siteUrl = null, feedUrl = "https://third.example.com/feed")
    )

    @Test
    fun `returns the feed after the current row`() {
        assertEquals(2L, nextSubscriptionId(feeds, currentFeedId = 1L))
    }

    @Test
    fun `last row has no next feed`() {
        assertNull(nextSubscriptionId(feeds, currentFeedId = 3L))
    }

    @Test
    fun `unknown row has no next feed`() {
        assertNull(nextSubscriptionId(feeds, currentFeedId = 99L))
    }
}
