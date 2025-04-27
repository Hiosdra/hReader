package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.CreateFeedResponse
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.model.EntriesResponse
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.model.FeedCountersResponse

class MinifluxApiRepository(private val apiService: MinifluxApiService) {
    suspend fun getEntries(
        status: String = "unread",
        order: String = "published_at",
        direction: String = "asc",
        limit: Int = 50
    ): EntriesResponse = apiService.getEntries(status, order, direction, limit)

    suspend fun getEntriesByIds(ids: String): EntriesResponse = apiService.getEntriesByIds(ids)

    suspend fun getEntryById(entryId: Long): Entry = apiService.getEntryById(entryId)

    suspend fun getFeeds(): List<Feed> = apiService.getFeeds()

    suspend fun getFeedCounters(): FeedCountersResponse = apiService.getFeedCounters()

    suspend fun getFeedEntries(
        feedId: Long,
        status: String = "unread",
        order: String = "published_at",
        direction: String = "desc",
        limit: Int = 100
    ): EntriesResponse = apiService.getFeedEntries(feedId, status, order, direction, limit)

    suspend fun createFeed(request: CreateFeedRequest): CreateFeedResponse = apiService.createFeed(request)

    suspend fun discoverFeeds(request: DiscoverRequest): List<DiscoverResponse> = apiService.discoverFeeds(request)

    suspend fun updateEntriesStatus(request: UpdateEntriesStatusRequest) = apiService.updateEntriesStatus(request)
}
