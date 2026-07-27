package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.FeedBackend

class FeedRepository(private val backend: FeedBackend) {
    suspend fun getFeeds(): List<Feed> = backend.getFeeds()
    suspend fun getUnreadCounts(): Map<Long, Int> = backend.getUnreadCounts()
    suspend fun createFeed(url: String) = backend.createFeed(url)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = backend.discoverFeeds(url)
    suspend fun verifyConnection(): Int = backend.verifyConnection()
}
