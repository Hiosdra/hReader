package com.hiosdra.hreader.data.remote.miniflux

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Enclosure
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.EntriesPage
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.data.remote.miniflux.dto.CreateFeedRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverRequest
import com.hiosdra.hreader.data.remote.miniflux.dto.DiscoverResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxEnclosure
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxEntriesResponse
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxEntry
import com.hiosdra.hreader.data.remote.miniflux.dto.MinifluxFeed
import com.hiosdra.hreader.data.remote.miniflux.dto.UpdateEntriesStatusRequest
import com.hiosdra.hreader.data.remote.withRetries
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val UNREAD_STATUS = "unread"
private const val READ_STATUS = "read"
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

    override suspend fun getFeeds(): List<Feed> = withRetries { fetchFeeds() }

    override suspend fun verifyConnection(): Int = fetchFeeds().size

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
        withRetries { apiService.updateEntriesStatus(UpdateEntriesStatusRequest(entryIds, status.toWire())) }
    }

    override suspend fun fetchFullContent(entryId: Long): String? =
        withRetries { apiService.fetchOriginalContent(entryId).content.takeIf { it.isNotBlank() } }

    private suspend fun fetchFeeds(): List<Feed> = apiService.getFeeds().map { it.toDomain() }
}

private fun ArticleStatus.toWire(): String = when (this) {
    ArticleStatus.READ -> READ_STATUS
    ArticleStatus.UNREAD -> UNREAD_STATUS
}

private fun String?.toArticleStatus(): ArticleStatus =
    if (this == READ_STATUS) ArticleStatus.READ else ArticleStatus.UNREAD

internal fun String?.toOffset(): Int = this?.toIntOrNull() ?: 0

internal fun MinifluxEntriesResponse.toEntriesPage(offset: Int, limit: Int): EntriesPage = EntriesPage(
    entries = entries.map { it.toDomain() },
    cursor = (offset + limit).toString().takeIf { entries.size >= limit }
)

internal fun MinifluxEntry.toDomain(): Entry = Entry(
    id = id,
    title = title.orEmpty(),
    author = author?.takeIf { it.isNotBlank() },
    url = url.orEmpty(),
    publishedAt = publishedAt.toInstant(),
    content = content,
    feed = feed.toDomain(),
    readingTime = readingTime,
    enclosures = enclosures.mapNotNull { it.toDomain() },
    status = status.toArticleStatus()
)

private fun MinifluxFeed?.toDomain(): Feed = Feed(
    id = this?.id ?: 0L,
    title = this?.title.orEmpty(),
    siteUrl = this?.siteUrl?.takeIf { it.isNotBlank() },
    feedUrl = this?.feedUrl.orEmpty()
)

private fun MinifluxEnclosure.toDomain(): Enclosure? {
    val link = url?.takeIf { it.isNotBlank() } ?: return null
    return Enclosure(url = link, mimeType = mimeType)
}

private fun String?.toInstant(): Instant {
    if (this.isNullOrBlank()) return Instant.EPOCH
    return runCatching { OffsetDateTime.parse(this).toInstant() }
        .recoverCatching { Instant.parse(this) }
        .getOrDefault(Instant.EPOCH)
}

private fun DiscoverResponse.toDomain(): DiscoveredFeed =
    DiscoveredFeed(url = url, title = title, type = type)
