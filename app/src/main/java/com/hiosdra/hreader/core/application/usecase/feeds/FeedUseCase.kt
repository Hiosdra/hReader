package com.hiosdra.hreader.core.application.usecase.feeds

import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.feeds.OpmlImportResult
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.StateFlow

class FeedUseCase(
    private val feeds: FeedStore,
    network: NetworkStatus
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    suspend fun getCachedFeeds(): List<Feed> = feeds.getCachedFeeds()
    suspend fun getCachedUnreadCounts(): Map<Long, Int> = feeds.getCachedUnreadCounts()
    suspend fun refreshFeeds(): List<Feed> = feeds.refreshFeeds()
    suspend fun getUnreadCounts(): Map<Long, Int> = feeds.getUnreadCounts()
    suspend fun createFeed(url: String) = feeds.createFeed(url)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = feeds.discoverFeeds(url)
    suspend fun verifyConnection(): Int = feeds.verifyConnection()
    suspend fun deleteFeed(feedId: Long) = feeds.deleteFeed(feedId)
    suspend fun renameFeed(feedId: Long, title: String) = feeds.renameFeed(feedId, title)
    suspend fun setAiOverviewPreloading(feedId: Long, enabled: Boolean) =
        feeds.setAiOverviewPreloading(feedId, enabled)
    suspend fun exportOpml(title: String): String = feeds.exportOpml(title)
    suspend fun importOpml(xml: String): OpmlImportResult = feeds.importOpml(xml)
}
