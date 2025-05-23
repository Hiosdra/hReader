package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.CreateFeedRequest
import com.hiosdra.hreader.data.model.CreateFeedResponse
import com.hiosdra.hreader.data.model.DiscoverRequest
import com.hiosdra.hreader.data.model.DiscoverResponse
import com.hiosdra.hreader.data.model.EntriesResponse
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.model.FeedCountersResponse
import com.hiosdra.hreader.data.model.OriginalContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val ENTRIES_DOWNLOAD_DEFAULT_LIMIT = 50

class MinifluxApiRepository(private val apiService: MinifluxApiService) {
    suspend fun getEntries(
        status: String = "unread",
        order: String = "published_at",
        direction: String = "asc",
        limit: Int = ENTRIES_DOWNLOAD_DEFAULT_LIMIT,
        offset: Int = 0
    ): EntriesResponse = withRetries {
        apiService.getEntries(status, order, direction, limit, offset)
    }

    suspend fun getEntriesByIds(ids: List<Long>): List<Entry> = withRetries {
        coroutineScope {
            ids.map { id -> async(Dispatchers.IO) { apiService.getEntryById(id) } }.awaitAll()
        }
    }

    suspend fun getEntryById(entryId: Long): Entry =
        withRetries { apiService.getEntryById(entryId) }

    suspend fun getFeeds(): List<Feed> =
        withRetries { apiService.getFeeds() }

    suspend fun getFeedCounters(): FeedCountersResponse =
        withRetries { apiService.getFeedCounters() }

    suspend fun getFeedEntries(
        feedId: Long,
        status: String = "unread",
        order: String = "published_at",
        direction: String = "desc",
        limit: Int = ENTRIES_DOWNLOAD_DEFAULT_LIMIT
    ): EntriesResponse = withRetries {
        apiService.getFeedEntries(feedId, status, order, direction, limit)
    }

    suspend fun createFeed(request: CreateFeedRequest): CreateFeedResponse =
        withRetries { apiService.createFeed(request) }

    suspend fun discoverFeeds(request: DiscoverRequest): List<DiscoverResponse> =
        withRetries { apiService.discoverFeeds(request) }

    suspend fun updateEntriesStatus(request: UpdateEntriesStatusRequest) =
        withRetries { apiService.updateEntriesStatus(request) }

    suspend fun fetchOriginalContent(entryId: Long): OriginalContentResponse =
        withRetries { apiService.fetchOriginalContent(entryId) }
}

private suspend fun <T> withRetries(
    maxAttempts: Int = 5,
    delayMillis: Long = 500,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastError = e
            if (attempt < maxAttempts - 1) {
                delay(delayMillis)
            }
        }
    }
    throw lastError ?: RuntimeException("Unknown error in withRetries")
}
