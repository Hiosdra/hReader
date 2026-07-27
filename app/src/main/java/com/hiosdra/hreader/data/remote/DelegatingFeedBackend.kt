package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.BackendType
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import java.time.Instant

class DelegatingFeedBackend(
    private val config: ServerConfig,
    private val freshRssBackend: FeedBackend,
    private val minifluxBackend: FeedBackend
) : FeedBackend {

    private val active: FeedBackend
        get() = when (config.backendType()) {
            BackendType.FRESHRSS -> freshRssBackend
            BackendType.MINIFLUX -> minifluxBackend
        }

    override suspend fun getUnreadEntries(limit: Int, cursor: String?): EntriesPage =
        active.getUnreadEntries(limit, cursor)

    override suspend fun getUnreadEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage = active.getUnreadEntriesChangedAfter(changedAfter, limit, cursor)

    override suspend fun getFeeds(): List<Feed> = active.getFeeds()

    override suspend fun getUnreadCounts(): Map<Long, Int> = active.getUnreadCounts()

    override suspend fun createFeed(feedUrl: String) = active.createFeed(feedUrl)

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = active.discoverFeeds(url)

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) =
        active.updateEntriesStatus(entryIds, status)

    override suspend fun fetchFullContent(entryId: Long): String? = active.fetchFullContent(entryId)

    override suspend fun verifyConnection(): Int = active.verifyConnection()
}
