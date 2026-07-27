package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import java.time.Instant

const val ENTRIES_PAGE_LIMIT = 200

data class EntriesPage(
    val entries: List<Entry>,
    val cursor: String?
)

interface FeedBackend {
    suspend fun getUnreadEntries(limit: Int = ENTRIES_PAGE_LIMIT, cursor: String? = null): EntriesPage

    suspend fun getUnreadEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int = ENTRIES_PAGE_LIMIT,
        cursor: String? = null
    ): EntriesPage

    suspend fun getFeeds(): List<Feed>

    suspend fun getUnreadCounts(): Map<Long, Int>

    suspend fun createFeed(feedUrl: String)

    suspend fun discoverFeeds(url: String): List<DiscoveredFeed>

    suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus)

    suspend fun fetchFullContent(entryId: Long): String?

    suspend fun verifyConnection(): Int
}
