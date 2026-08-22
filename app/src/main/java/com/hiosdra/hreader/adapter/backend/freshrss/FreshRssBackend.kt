package com.hiosdra.hreader.adapter.backend.freshrss

import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Enclosure
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.EntriesPage
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.adapter.backend.common.FeedDiscoveryService
import com.hiosdra.hreader.adapter.backend.common.fetchHtml
import com.hiosdra.hreader.adapter.backend.freshrss.dto.StreamContentsResponse
import com.hiosdra.hreader.adapter.backend.freshrss.dto.StreamEnclosure
import com.hiosdra.hreader.adapter.backend.freshrss.dto.StreamItem
import com.hiosdra.hreader.adapter.backend.freshrss.dto.StreamOrigin
import com.hiosdra.hreader.adapter.backend.freshrss.dto.Subscription
import com.hiosdra.hreader.adapter.backend.common.withRetries
import com.hiosdra.hreader.adapter.backend.common.withFeedFailureMapping
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

private const val JSON_OUTPUT = "json"
private const val OLDEST_FIRST = "o"
private const val NEWEST_FIRST = "n"
private const val READ_STATE = "user/-/state/com.google/read"
private const val STARRED_STATE = "user/-/state/com.google/starred"
private const val EDIT_ACTION = "edit"
private const val UNSUBSCRIBE_ACTION = "unsubscribe"
private const val FEED_STREAM_PREFIX = "feed/"
private const val ITEM_ID_TAG_PREFIX = "tag:google.com,2005:reader/item/"
private const val WORDS_PER_MINUTE = 250

class FreshRssBackend(
    private val apiService: FreshRssApiService,
    private val feedDiscoveryService: FeedDiscoveryService,
    private val httpClient: OkHttpClient
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

    override suspend fun verifyConnection(): Int = withRetries { fetchFeeds().size }

    override suspend fun getUnreadCounts(): Map<Long, Int> = withRetries {
        apiService.getUnreadCounts(JSON_OUTPUT).unreadCounts
            .filter { it.id.startsWith(FEED_STREAM_PREFIX) }
            .associate { streamIdToFeedId(it.id) to it.count }
    }

    // Subscribing is not idempotent, so a retry after a client-side timeout could add the feed
    // twice. The caller sees the failure instead.
    override suspend fun createFeed(feedUrl: String) = withFeedFailureMapping {
        val response = apiService.quickAddSubscription(feedUrl, writeToken())
        if (response.numResults < 1) {
            throw IOException(response.error ?: "FreshRSS could not subscribe to the requested feed")
        }
    }

    override suspend fun deleteFeed(feedId: Long) {
        withRetries {
            apiService.editSubscription(
                action = UNSUBSCRIBE_ACTION,
                streamId = FEED_STREAM_PREFIX + feedId,
                title = null,
                writeToken = writeToken()
            ).close()
        }
    }

    override suspend fun renameFeed(feedId: Long, title: String) {
        withRetries {
            apiService.editSubscription(
                action = EDIT_ACTION,
                streamId = FEED_STREAM_PREFIX + feedId,
                title = title,
                writeToken = writeToken()
            ).close()
        }
    }

    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = withFeedFailureMapping {
        feedDiscoveryService.discoverFeeds(url)
    }

    override suspend fun updateEntriesStatus(entryIds: List<Long>, status: ArticleStatus) {
        if (entryIds.isEmpty()) return
        editTag(entryIds, READ_STATE, add = status == ArticleStatus.READ)
    }

    override suspend fun updateEntriesStarred(entryIds: List<Long>, starred: Boolean) {
        if (entryIds.isEmpty()) return
        editTag(entryIds, STARRED_STATE, add = starred)
    }

    private suspend fun editTag(entryIds: List<Long>, state: String, add: Boolean) {
        withRetries {
            apiService.editTag(
                itemIds = entryIds,
                addTag = state.takeIf { add },
                removeTag = state.takeUnless { add },
                writeToken = writeToken()
            ).close()
        }
    }

    override suspend fun fetchFullContent(entryId: Long, articleUrl: String?): String? {
        val url = articleUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val document = Jsoup.parse(httpClient.fetchHtml(url), url)
        document.select("script, style, noscript, nav, header, footer, aside, form").remove()
        val candidate = listOf(
            "[itemprop=articleBody]",
            "article",
            "main",
            "body"
        ).asSequence()
            .mapNotNull { selector -> document.select(selector).firstOrNull() }
            .firstOrNull { it.text().trim().length >= 80 }
            ?: return null
        return candidate.html().takeIf { Jsoup.parse(it).text().isNotBlank() }
    }

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

/**
 * An item whose id cannot be read is dropped rather than allowed to fail the page. It used to throw
 * out of the parse, and since a malformed id is not a retryable failure the whole sync stopped —
 * permanently, because the next run met the same entry.
 */
internal fun StreamContentsResponse.toEntriesPage(): EntriesPage = EntriesPage(
    entries = items.mapNotNull { item -> item.resolveId()?.let { item.toEntry(it) } },
    cursor = continuation?.takeIf { it.isNotBlank() }
)

private fun StreamItem.toEntry(id: Long): Entry {
    val body = content?.content ?: summary?.content
    return Entry(
        id = id,
        title = title.orEmpty(),
        author = author?.takeIf { it.isNotBlank() },
        url = canonical.firstNotNullOfOrNull { it.href } ?: alternate.firstNotNullOfOrNull { it.href }.orEmpty(),
        publishedAt = Instant.ofEpochSecond(published ?: 0L),
        content = body,
        feed = origin.toFeed(),
        readingTime = body?.let { estimateReadingTimeMinutes(it) },
        enclosures = enclosure.toEnclosures(),
        status = if (categories.any { it == READ_STATE }) ArticleStatus.READ else ArticleStatus.UNREAD,
        starred = categories.any { it == STARRED_STATE }
    )
}

private fun List<StreamEnclosure>.toEnclosures(): List<Enclosure> = mapNotNull { enclosure ->
    val href = enclosure.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    Enclosure(url = href, mimeType = enclosure.type)
}

private fun StreamItem.resolveId(): Long? =
    numericId?.toLongOrNull() ?: parseItemId(id)

private fun parseItemId(rawId: String): Long? {
    // Only the long form (tag:google.com,2005:reader/item/<hex>) carries a hexadecimal id.
    // A short-form id is decimal, and a 16-digit decimal id is also a valid hex string,
    // so the prefix — not the shape of the token — decides how to read it.
    val isLongForm = rawId.startsWith(ITEM_ID_TAG_PREFIX)
    val token = rawId.removePrefix(ITEM_ID_TAG_PREFIX).substringAfterLast('/')
    return if (isLongForm && HEX_ITEM_ID.matches(token)) {
        runCatching { java.lang.Long.parseUnsignedLong(token, 16) }.getOrNull()
    } else {
        token.toLongOrNull()
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

/**
 * A stream id is normally `feed/<number>`. When it is not — some installations name the stream
 * after its address — a digest of the token stands in for the number.
 *
 * [String.hashCode] used to: 32 bits is small enough that a few hundred subscriptions make a
 * collision realistic, and a collision merges two feeds into one row, files one feed's articles
 * under the other's name and lets unsubscribing from either delete both. `absoluteValue` also
 * leaves [Int.MIN_VALUE] negative.
 */
private fun streamIdToFeedId(streamId: String): Long {
    val token = streamId.removePrefix(FEED_STREAM_PREFIX)
    return token.toLongOrNull() ?: token.digestToId()
}

private fun String.digestToId(): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(StandardCharsets.UTF_8))
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (digest[index].toLong() and 0xFF)
    }
    // Only the sign bit is cleared. Folding it in — or shifting it away — would map two digests
    // that differ solely there onto the same id, which is the collision this is here to avoid.
    return value and Long.MAX_VALUE
}

private fun estimateReadingTimeMinutes(html: String): Int {
    val words = html.replace(Regex("<[^>]*>"), " ").split(Regex("\\s+")).count { it.isNotBlank() }
    if (words == 0) return 0
    return ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1)
}
