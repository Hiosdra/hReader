package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.feeds.OpmlImportResult

interface FeedStore {
    suspend fun getCachedFeeds(): List<Feed>
    suspend fun getCachedUnreadCounts(): Map<Long, Int>
    suspend fun refreshFeeds(): List<Feed>
    suspend fun getUnreadCounts(): Map<Long, Int>
    suspend fun createFeed(url: String)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed>
    suspend fun verifyConnection(): Int
    suspend fun deleteFeed(feedId: Long)
    suspend fun renameFeed(feedId: Long, title: String)
    suspend fun exportOpml(title: String): String
    suspend fun importOpml(xml: String): OpmlImportResult
}
