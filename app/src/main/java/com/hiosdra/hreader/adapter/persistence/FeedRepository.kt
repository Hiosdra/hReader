package com.hiosdra.hreader.adapter.persistence

import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.core.domain.model.DiscoveredFeed
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.adapter.opml.buildOpml
import com.hiosdra.hreader.adapter.opml.parseOpml
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.feeds.OpmlImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val DELETE_FEED_CHUNK = 500

class FeedRepository(
    private val backend: FeedBackend,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val db: AppDatabase
) : FeedStore {
    /**
     * The subscription list as last synced. Serving it from the cache is what lets the screen open
     * without a connection at all: it used to go straight to the backend and show an error.
     */
    override suspend fun getCachedFeeds(): List<Feed> = feedDao.getAllFeeds().first().map { it.toFeed() }

    /** Unread counts from the cached articles, so the list still adds up while offline. */
    override suspend fun getCachedUnreadCounts(): Map<Long, Int> =
        articleDao.observeUnreadCountsPerFeed().first().associate { it.feedId to it.unreadCount }

    override suspend fun refreshFeeds(): List<Feed> {
        val feeds = backend.getFeeds()
        val incoming = db.withTransaction {
            val existingSettings = feedDao.getAllFeedsImmediate()
                .associate { it.id to it.preloadAiOverview }
            val persistedFeeds = feeds.map { feed ->
                feed.toFeedEntity().copy(
                    preloadAiOverview = existingSettings[feed.id] ?: feed.preloadAiOverview
                )
            }
            val incomingIds = persistedFeeds.mapTo(hashSetOf()) { it.id }
            val staleIds = feedDao.getAllIds().filterNot(incomingIds::contains)
            if (persistedFeeds.isNotEmpty()) feedDao.insertFeeds(persistedFeeds)
            staleIds.chunked(DELETE_FEED_CHUNK).forEach { feedIds ->
                articleDao.deleteByFeedIds(feedIds)
                feedDao.deleteByIds(feedIds)
            }
            persistedFeeds
        }
        return incoming.map { it.toFeed() }
    }

    override suspend fun getUnreadCounts(): Map<Long, Int> = backend.getUnreadCounts()
    override suspend fun createFeed(url: String) = backend.createFeed(url)
    override suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = backend.discoverFeeds(url)
    override suspend fun verifyConnection(): Int = backend.verifyConnection()

    /**
     * The server first: dropping the local copy of a feed the backend still carries would only
     * bring it back on the next sync, along with every article in it.
     */
    override suspend fun deleteFeed(feedId: Long) {
        backend.deleteFeed(feedId)
        db.withTransaction {
            articleDao.deleteByFeedId(feedId)
            feedDao.deleteById(feedId)
        }
    }

    override suspend fun renameFeed(feedId: Long, title: String) {
        backend.renameFeed(feedId, title)
        feedDao.updateTitle(feedId, title)
    }

    override suspend fun setAiOverviewPreloading(feedId: Long, enabled: Boolean) {
        feedDao.updateAiOverviewPreloading(feedId, enabled)
    }

    override suspend fun exportOpml(title: String): String = buildOpml(getCachedFeeds(), title)

    /**
     * Subscribes to everything in the file that is not subscribed to already. One failure does not
     * stop the rest: a single unreachable feed in a hundred-line export should not cost the other
     * ninety-nine.
     */
    override suspend fun importOpml(xml: String): OpmlImportResult {
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
        if (added > 0) {
            runCatching { refreshFeeds() }
                .onFailure { if (it is CancellationException) throw it }
        }
        return OpmlImportResult(added = added, skipped = skipped, failed = failed)
    }
}

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl,
    preloadAiOverview = preloadAiOverview
)

private fun Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl,
    preloadAiOverview = preloadAiOverview
)
