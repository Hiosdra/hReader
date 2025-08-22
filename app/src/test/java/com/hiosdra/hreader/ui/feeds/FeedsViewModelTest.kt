package com.hiosdra.hreader.ui.feeds

import com.hiosdra.hreader.data.model.Feed
import org.junit.Test
import org.junit.Assert.*

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