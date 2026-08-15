package com.hiosdra.hreader.data.repository

import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.opml.buildOpml
import com.hiosdra.hreader.data.opml.parseOpml
import com.hiosdra.hreader.data.remote.FeedBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** What an OPML import managed to do, so the screen can say more than "done". */
data class OpmlImportResult(
    val added: Int,
    val skipped: Int,
    val failed: List<String>
)

class FeedRepository(
    private val backend: FeedBackend,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val db: AppDatabase
) {
    /**
     * The subscription list as last synced. Serving it from the cache is what lets the screen open
     * without a connection at all: it used to go straight to the backend and show an error.
     */
    suspend fun getCachedFeeds(): List<Feed> = feedDao.getAllFeeds().first().map { it.toFeed() }

    /** Unread counts from the cached articles, so the list still adds up while offline. */
    suspend fun getCachedUnreadCounts(): Map<Long, Int> =
        articleDao.observeUnreadCountsPerFeed().first().associate { it.feedId to it.unreadCount }

    suspend fun refreshFeeds(): List<Feed> {
        val feeds = backend.getFeeds()
        val incoming = feeds.map { it.toFeedEntity() }
        val staleIds = feedDao.getAllIds().filterNot { id -> incoming.any { it.id == id } }
        db.withTransaction {
            if (incoming.isNotEmpty()) feedDao.insertFeeds(incoming)
            staleIds.forEach { feedId ->
                articleDao.deleteByFeedId(feedId)
                feedDao.deleteById(feedId)
            }
        }
        return feeds
    }

    suspend fun getUnreadCounts(): Map<Long, Int> = backend.getUnreadCounts()
    suspend fun createFeed(url: String) = backend.createFeed(url)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = backend.discoverFeeds(url)
    suspend fun verifyConnection(): Int = backend.verifyConnection()

    /**
     * The server first: dropping the local copy of a feed the backend still carries would only
     * bring it back on the next sync, along with every article in it.
     */
    suspend fun deleteFeed(feedId: Long) {
        backend.deleteFeed(feedId)
        db.withTransaction {
            articleDao.deleteByFeedId(feedId)
            feedDao.deleteById(feedId)
        }
    }

    suspend fun renameFeed(feedId: Long, title: String) {
        backend.renameFeed(feedId, title)
        feedDao.updateTitle(feedId, title)
    }

    suspend fun exportOpml(title: String): String = buildOpml(getCachedFeeds(), title)

    /**
     * Subscribes to everything in the file that is not subscribed to already. One failure does not
     * stop the rest: a single unreachable feed in a hundred-line export should not cost the other
     * ninety-nine.
     */
    suspend fun importOpml(xml: String): OpmlImportResult {
        val parsed = parseOpml(xml)
        if (parsed.isEmpty()) return OpmlImportResult(added = 0, skipped = 0, failed = emptyList())

        val existing = getCachedFeeds().map { it.feedUrl }.toHashSet()
        var added = 0
        var skipped = 0
        val failed = mutableListOf<String>()
        parsed.forEach { feed ->
            if (feed.feedUrl in existing) {
                skipped++
                return@forEach
            }
            try {
                backend.createFeed(feed.feedUrl)
                added++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += feed.feedUrl
            }
        }
        if (added > 0) runCatching { refreshFeeds() }
        return OpmlImportResult(added = added, skipped = skipped, failed = failed)
    }
}

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

private fun Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)
