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

    /**
     * Entries that changed since [changedAfter]. Backends that can express "status changed after"
     * must include read entries: filtering them down to unread would hide entries read on another
     * client, and since those also drop out of [getUnreadEntries] the local cache would keep
     * showing them as unread forever. Backends that cannot express it return unread only, and the
     * caller falls back on a periodic full sync to reconcile.
     */
    suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int = ENTRIES_PAGE_LIMIT,
        cursor: String? = null
    ): EntriesPage

    /**
     * The most recent entries regardless of read state, newest first, for stocking up before a
     * stretch offline. [getUnreadEntries] cannot serve that: everything already read is exactly
     * what it leaves out, and unread alone is whatever happens to be left over.
     */
    suspend fun getRecentEntries(limit: Int = ENTRIES_PAGE_LIMIT, cursor: String? = null): EntriesPage

    suspend fun getFeeds(): List<Feed>

    suspend fun getUnreadCounts(): Map<Long, Int>

    suspend fun createFeed(feedUrl: String)

    suspend fun discoverFeeds(url: String): List<DiscoveredFeed>

    suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus)

    suspend fun fetchFullContent(entryId: Long): String?

    suspend fun verifyConnection(): Int
}
