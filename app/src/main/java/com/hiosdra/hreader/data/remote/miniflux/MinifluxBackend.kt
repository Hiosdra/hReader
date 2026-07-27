package com.hiosdra.hreader.data.remote.miniflux

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.EntriesPage
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.data.remote.miniflux.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.EntriesResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.UpdateEntriesStatusRequest
import com.hiosdra.hreader.data.remote.withRetries
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val UNREAD_STATUS = "unread"
private const val ORDER_PUBLISHED_AT = "published_at"
private const val DIRECTION_ASCENDING = "asc"

class MinifluxBackend(private val apiService: MinifluxApiService) : FeedBackend {

    override suspend fun getUnreadEntries(limit: Int, cursor: String?): EntriesPage {
        val offset = cursor.toOffset()
        return withRetries {
            apiService.getEntries(UNREAD_STATUS, ORDER_PUBLISHED_AT, DIRECTION_ASCENDING, limit, offset)
                .toEntriesPage(offset, limit)
        }
    }

    override suspend fun getUnreadEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage {
        val offset = cursor.toOffset()
        val changedAfterIso = DateTimeFormatter.ISO_INSTANT.format(changedAfter.atZone(ZoneOffset.UTC))
        return withRetries {
            apiService.getEntriesChangedAfter(
                UNREAD_STATUS,
                ORDER_PUBLISHED_AT,
                DIRECTION_ASCENDING,
                limit,
                offset,
                changedAfterIso
            ).toEntriesPage(offset, limit)
        }
    }

    override suspend fun getFeeds(): List<Feed> = withRetries { apiService.getFeeds() }

    override suspend fun verifyConnection(): Int = apiService.getFeeds().size

    override suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getFeedCounters().unreads.mapKeys { it.key.toLong() }
    }

    override suspend fun createFeed(feedUrl: String) = withRetries {
        apiService.createFeed(CreateFeedRequest(feed_url = feedUrl))
        Unit
    }

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = withRetries {
        apiService.discoverFeeds(DiscoverRequest(url)).map { it.toDomain() }
    }

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
        if (entryIds.isEmpty()) return
        withRetries { apiService.updateEntriesStatus(UpdateEntriesStatusRequest(entryIds, status.wire)) }
    }

    override suspend fun fetchFullContent(entryId: Long): String? =
        withRetries { apiService.fetchOriginalContent(entryId).content.takeIf { it.isNotBlank() } }
}

internal fun String?.toOffset(): Int = this?.toIntOrNull() ?: 0

internal fun EntriesResponse.toEntriesPage(offset: Int, limit: Int): EntriesPage = EntriesPage(
    entries = entries,
    cursor = (offset + limit).toString().takeIf { entries.size >= limit }
)

private fun DiscoverResponse.toDomain(): DiscoveredFeed =
    DiscoveredFeed(url = url, title = title, type = type)
