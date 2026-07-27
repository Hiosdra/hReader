package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.FeedDiscoveryService
import com.hiosdra.hreader.data.remote.FreshRssApiRepository

class FeedRepository(
    private val api: FreshRssApiRepository,
    private val feedDiscoveryService: FeedDiscoveryService
) {
    suspend fun getFeeds(): List<Feed> = api.getFeeds()
    suspend fun getUnreadCounts(): Map<Long, Int> = api.getUnreadCounts()
    suspend fun createFeed(url: String) = api.createFeed(url)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = feedDiscoveryService.discoverFeeds(url)
    suspend fun verifyConnection(): Int = api.verifyConnection()
}
