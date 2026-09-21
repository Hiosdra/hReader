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
import com.hiosdra.hreader.adapter.backend.common.withCursorRetries
import com.hiosdra.hreader.adapter.backend.common.withFeedFailureMapping
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

private const val JSON_OUTPUT = "json"
private const val OLDEST_FIRST = "o"
private const val NEWEST_FIRST = "n"
private const val READ_STATE = "user/-/state/com.google/read"
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
        withCursorRetries(cursor) { streamContents(limit, cursor, startTimeSeconds = null) }

    override suspend fun getEntriesChangedAfter(
        changedAfter: Instant,
        limit: Int,
        cursor: String?
    ): EntriesPage = withCursorRetries(cursor) { streamContents(limit, cursor, changedAfter.epochSecond) }

    override suspend fun getRecentEntries(limit: Int, cursor: String?): EntriesPage = withCursorRetries(cursor) {
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
        val html = withContext(Dispatchers.IO) { httpClient.fetchHtml(url) }
        return withContext(Dispatchers.Default) {
            val document = Jsoup.parse(html, url)
            document.select("script, style, noscript, nav, header, footer, aside, form").remove()
            val candidate = listOf(
                "[itemprop=articleBody]",
                "article",
                "main",
                "body"
            ).asSequence()
                .mapNotNull { selector -> document.select(selector).firstOrNull() }
                .firstOrNull { it.text().trim().length >= 80 }
                ?: return@withContext null
            candidate.html().takeIf { Jsoup.parse(it).text().isNotBlank() }
        }
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
        status = if (categories.any { it == READ_STATE }) ArticleStatus.READ else ArticleStatus.UNREAD
    )
}

private fun List<StreamEnclosure>.toEnclosures(): List<Enclosure> = mapNotNull { enclosure ->
    val href = enclosure.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    Enclosure(url = href, mimeType = enclosure.type)
}

private fun StreamItem.resolveId(): Long? =
    numericId?.toLongOrNull() ?: parseItemId(id)

private fun parseItemId(rawId: String): Long? {
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
    return value and Long.MAX_VALUE
}

private fun estimateReadingTimeMinutes(html: String): Int {
    val words = html.replace(Regex("<[^>]*>"), " ").split(Regex("\\s+")).count { it.isNotBlank() }
    if (words == 0) return 0
    return ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1)
}
