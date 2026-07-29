package com.hiosdra.hreader.data.remote.freshrss

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Enclosure
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.data.remote.EntriesPage
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.data.remote.FeedDiscoveryService
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamContentsResponse
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamEnclosure
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamItem
import com.hiosdra.hreader.data.remote.freshrss.dto.StreamOrigin
import com.hiosdra.hreader.data.remote.freshrss.dto.Subscription
import com.hiosdra.hreader.data.remote.withRetries
import java.io.IOException
import java.time.Instant
import kotlin.math.absoluteValue

private const val JSON_OUTPUT = "json"
private const val OLDEST_FIRST = "o"
private const val NEWEST_FIRST = "n"
private const val READ_STATE = "user/-/state/com.google/read"
private const val FEED_STREAM_PREFIX = "feed/"
private const val ITEM_ID_TAG_PREFIX = "tag:google.com,2005:reader/item/"
private const val WORDS_PER_MINUTE = 250

class FreshRssBackend(
    private val apiService: FreshRssApiService,
    private val feedDiscoveryService: FeedDiscoveryService
) : FeedBackend {

    override suspend fun getUnreadEntries(limit: Int, cursor: String?): EntriesPage =
        withRetries { streamContents(limit, cursor, startTimeSeconds = null) }

    /**
     * Unlike Miniflux this cannot surface entries read on another client, so it keeps excluding
     * read ones. The Google Reader `ot` parameter filters on the entry date or `lastModified`
     * (a content change), while marking an entry read updates `lastUserModified` — a different
     * column that `ot` never looks at. Dropping the exclusion would only enlarge every response.
     * Reconciling read state here needs the `stream/items/ids` diff instead.
     */
    override suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage = withRetries { streamContents(limit, cursor, changedAfter.epochSecond) }

    /**
     * The reading-list stream without the read-state exclusion, newest first, so the backlog fills
     * with what was published most recently rather than with whatever is still unread.
     */
    override suspend fun getRecentEntries(limit: Int, cursor: String?): EntriesPage = withRetries {
        apiService.getStreamContents(
            output = JSON_OUTPUT,
            count = limit.coerceAtMost(ENTRIES_PAGE_LIMIT),
            order = NEWEST_FIRST,
            excludeTarget = null,
            startTimeSeconds = null,
            continuation = cursor
        ).toEntriesPage()
    }

    override suspend fun getFeeds(): List<Feed> = withRetries { fetchFeeds() }

    override suspend fun verifyConnection(): Int = fetchFeeds().size

    override suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getUnreadCounts(JSON_OUTPUT).unreadCounts
            .filter { it.id.startsWith(FEED_STREAM_PREFIX) }
            .associate { streamIdToFeedId(it.id) to it.count }
    }

    // Subscribing is not idempotent, so a retry after a client-side timeout could add the feed
    // twice. The caller sees the failure instead.
    override suspend fun createFeed(feedUrl: String) {
        val response = apiService.quickAddSubscription(feedUrl, writeToken())
        if (response.numResults < 1) {
            throw IOException(response.error ?: "FreshRSS could not subscribe to $feedUrl")
        }
    }

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> =
        feedDiscoveryService.discoverFeeds(url)

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
        if (entryIds.isEmpty()) return
        val markAsRead = status == ArticleStatus.READ
        withRetries {
            apiService.editTag(
                itemIds = entryIds,
                addTag = READ_STATE.takeIf { markAsRead },
                removeTag = READ_STATE.takeUnless { markAsRead },
                writeToken = writeToken()
            ).close()
        }
    }

    override suspend fun fetchFullContent(entryId: Long): String? = null

    private suspend fun streamContents(limit: Int, cursor: String?, startTimeSeconds: Long?): EntriesPage =
        apiService.getStreamContents(
            output = JSON_OUTPUT,
            count = limit.coerceAtMost(ENTRIES_PAGE_LIMIT),
            order = OLDEST_FIRST,
            excludeTarget = READ_STATE,
            startTimeSeconds = startTimeSeconds,
            continuation = cursor
        ).toEntriesPage()

    private suspend fun fetchFeeds(): List<Feed> =
        apiService.getSubscriptions(JSON_OUTPUT).subscriptions.map { it.toFeed() }

    private suspend fun writeToken(): String = apiService.getWriteToken().use { it.string().trim() }
}

private val HEX_ITEM_ID = Regex("[0-9a-fA-F]{16}")

internal fun StreamContentsResponse.toEntriesPage(): EntriesPage = EntriesPage(
    entries = items.map { it.toEntry() },
    cursor = continuation?.takeIf { it.isNotBlank() }
)

private fun StreamItem.toEntry(): Entry {
    val body = content?.content ?: summary?.content
    return Entry(
        id = resolveId(),
        title = title.orEmpty(),
        author = author?.takeIf { it.isNotBlank() },
        url = canonical.firstNotNullOfOrNull { it.href } ?: alternate.firstNotNullOfOrNull { it.href }.orEmpty(),
        publishedAt = Instant.ofEpochSecond(published ?: 0L),
        content = body,
        feed = origin.toFeed(),
        readingTime = body?.let { estimateReadingTimeMinutes(it) },
        enclosures = enclosure.toEnclosures(),
        status = if (categories.any { it == READ_STATE }) ArticleStatus.READ else ArticleStatus.UNREAD
    )
}

private fun List<StreamEnclosure>.toEnclosures(): List<Enclosure> = mapNotNull { enclosure ->
    val href = enclosure.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    Enclosure(url = href, mimeType = enclosure.type)
}

private fun StreamItem.resolveId(): Long =
    numericId?.toLongOrNull() ?: parseItemId(id)

private fun parseItemId(rawId: String): Long {
    // Only the long form (tag:google.com,2005:reader/item/<hex>) carries a hexadecimal id.
    // A short-form id is decimal, and a 16-digit decimal id is also a valid hex string,
    // so the prefix — not the shape of the token — decides how to read it.
    val isLongForm = rawId.startsWith(ITEM_ID_TAG_PREFIX)
    val token = rawId.removePrefix(ITEM_ID_TAG_PREFIX).substringAfterLast('/')
    return if (isLongForm && HEX_ITEM_ID.matches(token)) {
        java.lang.Long.parseUnsignedLong(token, 16)
    } else {
        token.toLong()
    }
}

private fun StreamOrigin?.toFeed(): Feed = Feed(
    id = streamIdToFeedId(this?.streamId.orEmpty()),
    title = this?.title.orEmpty(),
    siteUrl = this?.htmlUrl?.takeIf { it.isNotBlank() },
    feedUrl = ""
)

private fun Subscription.toFeed(): Feed = Feed(
    id = streamIdToFeedId(id),
    title = title.orEmpty(),
    siteUrl = htmlUrl?.takeIf { it.isNotBlank() },
    feedUrl = url.orEmpty()
)

private fun streamIdToFeedId(streamId: String): Long {
    val token = streamId.removePrefix(FEED_STREAM_PREFIX)
    return token.toLongOrNull() ?: token.hashCode().toLong().absoluteValue
}

private fun estimateReadingTimeMinutes(html: String): Int {
    val words = html.replace(Regex("<[^>]*>"), " ").split(Regex("\\s+")).count { it.isNotBlank() }
    if (words == 0) return 0
    return ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1)
}
