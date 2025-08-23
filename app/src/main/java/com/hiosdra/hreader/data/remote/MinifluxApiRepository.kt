package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.dto.CreateFeedResponse
import com.hiosdra.hreader.data.remote.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.dto.DiscoverResponse
import com.hiosdra.hreader.data.remote.dto.EntriesResponse
import com.hiosdra.hreader.data.remote.dto.FeedCountersResponse
import com.hiosdra.hreader.data.remote.dto.OriginalContentResponse
import com.hiosdra.hreader.data.remote.dto.UpdateEntriesStatusRequest
import kotlinx.coroutines.delay

private const val ENTRIES_DOWNLOAD_DEFAULT_LIMIT = 200

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

    suspend fun getEntriesChangedAfter(
        changedAfter: String,
        status: String = "unread",
        order: String = "published_at", 
        direction: String = "asc",
        limit: Int = ENTRIES_DOWNLOAD_DEFAULT_LIMIT,
        offset: Int = 0
    ): EntriesResponse = withRetries {
        apiService.getEntriesChangedAfter(status, order, direction, limit, offset, changedAfter)
    }

    suspend fun getFeeds(): List<Feed> =
        withRetries { apiService.getFeeds() }

    suspend fun getFeedCounters(): FeedCountersResponse =
        withRetries { apiService.getFeedCounters() }

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
