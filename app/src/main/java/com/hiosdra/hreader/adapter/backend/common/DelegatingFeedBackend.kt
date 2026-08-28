package com.hiosdra.hreader.adapter.backend.common

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
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

    override suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage = active.getEntriesChangedAfter(changedAfter, limit, cursor)

    override suspend fun getRecentEntries(limit: Int, cursor: String?): EntriesPage =
        active.getRecentEntries(limit, cursor)

    override suspend fun getFeeds(): List<Feed> = active.getFeeds()

    override suspend fun getUnreadCounts(): Map<Long, Int> = active.getUnreadCounts()

    override suspend fun createFeed(feedUrl: String) = active.createFeed(feedUrl)

    override suspend fun deleteFeed(feedId: Long) = active.deleteFeed(feedId)

    override suspend fun renameFeed(feedId: Long, title: String) = active.renameFeed(feedId, title)

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = active.discoverFeeds(url)

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) =
        active.updateEntriesStatus(entryIds, status)

    override suspend fun fetchFullContent(entryId: Long, articleUrl: String?): String? =
        active.fetchFullContent(entryId, articleUrl)

    override suspend fun verifyConnection(): Int = active.verifyConnection()
}
