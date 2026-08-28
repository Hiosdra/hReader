package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import java.time.Instant

const val ENTRIES_PAGE_LIMIT = 200

data class EntriesPage(
    val entries: List<Entry>,
    val cursor: String?
)

interface FeedBackend {
    suspend fun getUnreadEntries(limit: Int = ENTRIES_PAGE_LIMIT, cursor: String? = null): EntriesPage

    suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int = ENTRIES_PAGE_LIMIT,
        cursor: String? = null
    ): EntriesPage

    suspend fun getRecentEntries(limit: Int = ENTRIES_PAGE_LIMIT, cursor: String? = null): EntriesPage

    suspend fun getFeeds(): List<Feed>

    suspend fun getUnreadCounts(): Map<Long, Int>

    suspend fun createFeed(feedUrl: String)

    suspend fun deleteFeed(feedId: Long)

    suspend fun renameFeed(feedId: Long, title: String)

    suspend fun discoverFeeds(url: String): List<DiscoveredFeed>

    suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus)

    suspend fun fetchFullContent(entryId: Long, articleUrl: String? = null): String?

    suspend fun verifyConnection(): Int
}
