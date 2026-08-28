package com.hiosdra.hreader.adapter.backend.miniflux

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Enclosure
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.adapter.backend.miniflux.dto.CreateFeedRequest
import com.hiosdra.hreader.adapter.backend.miniflux.dto.DiscoverRequest
import com.hiosdra.hreader.adapter.backend.miniflux.dto.DiscoverResponse
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEnclosure
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEntriesResponse
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxEntry
import com.hiosdra.hreader.adapter.backend.miniflux.dto.MinifluxFeed
import com.hiosdra.hreader.adapter.backend.miniflux.dto.UpdateEntriesStatusRequest
import com.hiosdra.hreader.adapter.backend.miniflux.dto.UpdateFeedRequest
import com.hiosdra.hreader.adapter.backend.common.withRetries
import com.hiosdra.hreader.adapter.backend.common.withFeedFailureMapping
import java.time.Instant
import java.time.OffsetDateTime

private const val UNREAD_STATUS = "unread"
private const val READ_STATUS = "read"
private const val ORDER_ID = "id"
private const val DIRECTION_ASCENDING = "asc"
private const val DIRECTION_DESCENDING = "desc"

private val UNREAD_ONLY = listOf(UNREAD_STATUS)
private val READ_AND_UNREAD = listOf(UNREAD_STATUS, READ_STATUS)

class MinifluxBackend(private val apiService: MinifluxApiService) : FeedBackend {

    override suspend fun getUnreadEntries(limit: Int, cursor: String?): EntriesPage =
        fetchEntries(UNREAD_ONLY, changedAfter = null, limit = limit, cursor = cursor)

    override suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage =
        fetchEntries(READ_AND_UNREAD, changedAfter = changedAfter.epochSecond, limit = limit, cursor = cursor)

    /**
     * Descending by id, so a page walks backwards from the newest entry and [cursor] is the oldest
     * id already seen. Ordering by id rather than date keeps the keyset stable even when a feed
     * backdates what it publishes.
     */
    override suspend fun getRecentEntries(limit: Int, cursor: String?): EntriesPage = withRetries {
        apiService.getEntries(
            statuses = READ_AND_UNREAD,
            order = ORDER_ID,
            direction = DIRECTION_DESCENDING,
            limit = limit,
            afterEntryId = null,
            changedAfter = null,
            beforeEntryId = cursor.toEntryIdCursor()
        ).toEntriesPage(limit)
    }

    override suspend fun getFeeds(): List<Feed> = withRetries { fetchFeeds() }

    override suspend fun verifyConnection(): Int = withRetries { fetchFeeds().size }

    override suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getFeedCounters().unreads.mapKeys { it.key.toLong() }
    }

    // Creating a feed is not idempotent: a retried POST after a client-side timeout would
    // subscribe twice, so this call takes the failure instead.
    override suspend fun createFeed(feedUrl: String) = withFeedFailureMapping {
        apiService.createFeed(CreateFeedRequest(feed_url = feedUrl))
        Unit
    }

    override suspend fun deleteFeed(feedId: Long) {
        withRetries { apiService.deleteFeed(feedId) }
    }

    override suspend fun renameFeed(feedId: Long, title: String) {
        withRetries { apiService.updateFeed(feedId, UpdateFeedRequest(title = title)) }
    }

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = withFeedFailureMapping {
        apiService.discoverFeeds(DiscoverRequest(url)).map { it.toDomain() }
    }

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
        if (entryIds.isEmpty()) return
        withRetries { apiService.updateEntriesStatus(UpdateEntriesStatusRequest(entryIds, status.toWire())) }
    }

    override suspend fun fetchFullContent(entryId: Long, articleUrl: String?): String? =
        withRetries { apiService.fetchOriginalContent(entryId).content.takeIf { it.isNotBlank() } }

    private suspend fun fetchFeeds(): List<Feed> = apiService.getFeeds().map { it.toDomain() }

    private suspend fun fetchEntries(
        statuses: List<String>,
        changedAfter: Long?,
        limit: Int,
        cursor: String?
    ): EntriesPage = withRetries {
        apiService.getEntries(
            statuses = statuses,
            order = ORDER_ID,
            direction = DIRECTION_ASCENDING,
            limit = limit,
            afterEntryId = cursor.toEntryIdCursor(),
            changedAfter = changedAfter
        ).toEntriesPage(limit)
    }
}

private fun ArticleStatus.toWire(): String = when (this) {
    ArticleStatus.READ -> READ_STATUS
    ArticleStatus.UNREAD -> UNREAD_STATUS
}

private fun String?.toArticleStatus(): ArticleStatus =
    if (this == READ_STATUS) ArticleStatus.READ else ArticleStatus.UNREAD

internal fun String?.toEntryIdCursor(): Long? = this?.toLongOrNull()

internal fun MinifluxEntriesResponse.toEntriesPage(limit: Int): EntriesPage = EntriesPage(
    entries = entries.map { it.toDomain() },
    // Entries come back ordered by id, so the last id of a full page is where the next one resumes.
    cursor = entries.lastOrNull()?.id?.toString()?.takeIf { entries.size >= limit }
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
