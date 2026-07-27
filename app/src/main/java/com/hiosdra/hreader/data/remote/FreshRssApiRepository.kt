package com.hiosdra.hreader.data.remote

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Enclosure
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.dto.EntriesPage
import com.hiosdra.hreader.data.remote.dto.StreamContentsResponse
import com.hiosdra.hreader.data.remote.dto.StreamEnclosure
import com.hiosdra.hreader.data.remote.dto.StreamItem
import com.hiosdra.hreader.data.remote.dto.StreamOrigin
import com.hiosdra.hreader.data.remote.dto.Subscription
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private const val ENTRIES_DOWNLOAD_DEFAULT_LIMIT = 200
private const val JSON_OUTPUT = "json"
private const val OLDEST_FIRST = "o"
private const val READ_STATE = "user/-/state/com.google/read"
private const val FEED_STREAM_PREFIX = "feed/"
private const val ITEM_ID_TAG_PREFIX = "tag:google.com,2005:reader/item/"
private const val WORDS_PER_MINUTE = 250

class FreshRssApiRepository(private val apiService: FreshRssApiService) {

    suspend fun getUnreadEntries(
        limit: Int = ENTRIES_DOWNLOAD_DEFAULT_LIMIT,
        continuation: String? = null
    ): EntriesPage = withRetries {
        apiService.getStreamContents(
            output = JSON_OUTPUT,
            count = limit,
            order = OLDEST_FIRST,
            excludeTarget = READ_STATE,
            startTimeSeconds = null,
            continuation = continuation
        ).toEntriesPage()
    }

    suspend fun getUnreadEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int = ENTRIES_DOWNLOAD_DEFAULT_LIMIT,
        continuation: String? = null
    ): EntriesPage = withRetries {
        apiService.getStreamContents(
            output = JSON_OUTPUT,
            count = limit,
            order = OLDEST_FIRST,
            excludeTarget = READ_STATE,
            startTimeSeconds = changedAfter.epochSecond,
            continuation = continuation
        ).toEntriesPage()
    }

    suspend fun getFeeds(): List<Feed> = withRetries { fetchFeeds() }

    suspend fun verifyConnection(): Int = fetchFeeds().size

    suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getUnreadCounts(JSON_OUTPUT).unreadCounts
            .filter { it.id.startsWith(FEED_STREAM_PREFIX) }
            .associate { streamIdToFeedId(it.id) to it.count }
    }

    suspend fun createFeed(feedUrl: String) = withRetries {
        val response = apiService.quickAddSubscription(feedUrl, writeToken())
        if (response.numResults < 1) {
            throw IOException(response.error ?: "FreshRSS could not subscribe to $feedUrl")
        }
    }

    suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
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

    private suspend fun fetchFeeds(): List<Feed> =
        apiService.getSubscriptions(JSON_OUTPUT).subscriptions.map { it.toFeed() }

    private suspend fun writeToken(): String = apiService.getWriteToken().use { it.string().trim() }
}

private val PUBLISHED_AT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

private val HEX_ITEM_ID = Regex("[0-9a-fA-F]{16}")

internal fun StreamContentsResponse.toEntriesPage(): EntriesPage = EntriesPage(
    entries = items.map { it.toEntry() },
    continuation = continuation?.takeIf { it.isNotBlank() }
)

private fun StreamItem.toEntry(): Entry {
    val entryId = resolveId()
    val body = content?.content ?: summary?.content
    return Entry(
        id = entryId,
        title = title.orEmpty(),
        author = author?.takeIf { it.isNotBlank() },
        url = canonical.firstNotNullOfOrNull { it.href } ?: alternate.firstNotNullOfOrNull { it.href }.orEmpty(),
        publishedAt = PUBLISHED_AT_FORMATTER.format(Instant.ofEpochSecond(published ?: 0L)),
        content = body,
        feed = origin.toFeed(),
        readingTime = body?.let { estimateReadingTimeMinutes(it) },
        enclosures = enclosure.toEnclosures(entryId),
        status = if (categories.any { it == READ_STATE }) ArticleStatus.READ else ArticleStatus.UNREAD
    )
}

private fun List<StreamEnclosure>.toEnclosures(entryId: Long): List<Enclosure> = mapNotNull { enclosure ->
    val href = enclosure.href ?: return@mapNotNull null
    Enclosure(
        id = 0L,
        userId = 0L,
        entryId = entryId,
        url = href,
        mimeType = enclosure.type,
        size = null,
        mediaProgression = null
    )
}

private fun StreamItem.resolveId(): Long =
    numericId?.toLongOrNull() ?: parseItemId(id)

private fun parseItemId(rawId: String): Long {
    val token = rawId.removePrefix(ITEM_ID_TAG_PREFIX).substringAfterLast('/')
    return if (HEX_ITEM_ID.matches(token)) java.lang.Long.parseUnsignedLong(token, 16) else token.toLong()
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

private suspend fun <T> withRetries(
    maxAttempts: Int = 5,
    delayMillis: Long = 500,
    block: suspend () -> T
): T {
    require(maxAttempts >= 1)
    var attempts = 0
    while (true) {
        try {
            return block()
        } catch (e: Throwable) {
            attempts++
            if (attempts >= maxAttempts) throw e
            delay(delayMillis)
        }
    }
}
