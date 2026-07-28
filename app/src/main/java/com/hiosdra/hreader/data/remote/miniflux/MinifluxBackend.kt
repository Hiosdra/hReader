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

private const val UNREAD_STATUS = "unread"
private const val READ_STATUS = "read"
private const val ORDER_ID = "id"
private const val DIRECTION_ASCENDING = "asc"

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

    override suspend fun getFeeds(): List<Feed> = withRetries { fetchFeeds() }

    override suspend fun verifyConnection(): Int = fetchFeeds().size

    override suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getFeedCounters().unreads.mapKeys { it.key.toLong() }
    }

    // Creating a feed is not idempotent: a retried POST after a client-side timeout would
    // subscribe twice, so this call takes the failure instead.
    override suspend fun createFeed(feedUrl: String) {
        apiService.createFeed(CreateFeedRequest(feed_url = feedUrl))
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
