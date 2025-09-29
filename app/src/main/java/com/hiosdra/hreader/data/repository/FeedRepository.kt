package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import com.hiosdra.hreader.data.remote.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverResponse

class FeedRepository(private val api: MinifluxApiRepository) {
    suspend fun getFeeds(): List<Feed> = api.getFeeds()
    suspend fun getUnreadCounts(): Map<Long, Int> = api.getFeedCounters().unreads.mapKeys { it.key.toLong() }
    suspend fun createFeed(url: String) { api.createFeed(CreateFeedRequest(feed_url = url)) }
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = api.discoverFeeds(DiscoverRequest(url)).map { it.toDomain() }
}

private fun DiscoverResponse.toDomain(): DiscoveredFeed =
    DiscoveredFeed(url = url, title = title, type = type)

