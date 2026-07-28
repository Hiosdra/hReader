package com.hiosdra.hreader.data.local.repository

import android.util.Log
import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

private const val TAG = "ArticleRepository"

/** Miniflux and FreshRSS both take the ids inline, so one huge request is split into chunks. */
private const val STATUS_UPDATE_CHUNK = 200

/** Deleting in chunks keeps the statement below SQLite's bound-variable ceiling. */
private const val DELETE_CHUNK = 500

private val INCREMENTAL_SYNC_WINDOW: Duration = Duration.ofHours(24)

/** Upper bound on how long the cache may go without being reconciled against the full server state. */
private val FULL_SYNC_INTERVAL: Duration = Duration.ofDays(7)

/**
 * The device clock is compared against server-side change timestamps, so a few seconds of skew
 * would drop entries from an incremental sync permanently. Re-fetching a small overlap is free —
 * entries are upserted.
 */
private val INCREMENTAL_SYNC_OVERLAP: Duration = Duration.ofMinutes(5)

/** How long a read article is kept after being read; without it the cache grows without bound. */
private val READ_ARTICLE_RETENTION: Duration = Duration.ofDays(30)

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val api: FeedBackend,
    private val db: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val syncPerformanceLogger: SyncPerformanceLogger
) {
    fun getAllArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllArticlesOldestFirst().map { it.mapToEntries() }

    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesByIds(ids.map { it.toString() }).map { it.mapToEntries() }

    suspend fun getAllArticlesForFeed(feedId: Long): Flow<List<Entry>> {
        val feed = feedDao.getFeedById(feedId) ?: throw IllegalStateException("Feed not found")
        return articleDao.getAllArticlesForFeed(feedId).map { articles ->
            articles.map { it.toEntry(feed) }
        }
    }

    suspend fun refreshArticles() {
        val syncStartTime = System.currentTimeMillis()

        // Local changes go up before the server state comes down, so reconciliation below can
        // treat the backend as authoritative without discarding anything the user just did.
        pushPendingStatuses()

        val useIncrementalSync = shouldUseIncrementalSync(syncStartTime)
        syncPerformanceLogger.logSyncMode(useIncrementalSync, getLastSyncTime().takeIf { it > 0 })

        val fetchedIds = mutableSetOf<String>()
        syncPerformanceLogger.measureSyncTime("Article pages") {
            var cursor: String? = null
            while (true) {
                val page = fetchArticleBatch(useIncrementalSync, ENTRIES_PAGE_LIMIT, cursor)
                if (page.entries.isNotEmpty()) {
                    // Persisting per page keeps memory flat regardless of backlog size and leaves
                    // partial progress behind if the sync is interrupted.
                    persistPage(page.entries)
                    fetchedIds += page.entries.map { it.id.toString() }
                }
                cursor = page.cursor
                if (cursor == null || page.entries.isEmpty()) break
            }
        }

        feedDao.insertFeeds(api.getFeeds().map { it.toFeedEntity() })

        if (!useIncrementalSync) {
            dropUnreadArticlesMissingFrom(fetchedIds)
            preferencesManager.setLastFullSyncTimestamp(syncStartTime)
        }
        pruneExpiredReadArticles()

        syncPerformanceLogger.logBatchInfo(ENTRIES_PAGE_LIMIT, fetchedIds.size)
        preferencesManager.setLastSyncTimestamp(syncStartTime)
    }

    private suspend fun persistPage(entries: List<Entry>) {
        val feeds = entries.associate { it.feed.id to it.feed.toFeedEntity() }.values.toList()
        val articles = entries.map { it.toEntity() }
        db.withTransaction {
            feedDao.insertFeeds(feeds)
            insertArticlesPreservingPendingStatus(articles)
        }
    }

    /**
     * A full sync is also forced every [FULL_SYNC_INTERVAL] regardless of how recently the app
     * synced. With an hourly worker the 24h rule alone would never expire, so the reconciliation
     * that drops entries the backend stopped returning would never get a chance to run — and on
     * backends that cannot report status changes it is the only thing that reconciles them.
     */
    private fun shouldUseIncrementalSync(syncStartTime: Long): Boolean {
        val lastSyncTimestamp = getLastSyncTime()
        if (lastSyncTimestamp <= 0) return false

        val lastFullSync = preferencesManager.getLastFullSyncTimestamp()
        if (lastFullSync <= 0 || (syncStartTime - lastFullSync) >= FULL_SYNC_INTERVAL.toMillis()) return false

        return (syncStartTime - lastSyncTimestamp) < INCREMENTAL_SYNC_WINDOW.toMillis()
    }

    private fun getLastSyncTime(): Long = preferencesManager.getLastSyncTimestamp()

    private suspend fun fetchArticleBatch(useIncremental: Boolean, limit: Int, cursor: String?) =
        if (useIncremental) {
            val changedAfter = Instant.ofEpochMilli(getLastSyncTime()).minus(INCREMENTAL_SYNC_OVERLAP)
            Log.d(TAG, "Using incremental sync since: $changedAfter")
            api.getEntriesChangedAfter(changedAfter, limit = limit, cursor = cursor)
        } else {
            Log.d(TAG, "Using full sync")
            api.getUnreadEntries(limit = limit, cursor = cursor)
        }

    private suspend fun insertArticlesPreservingPendingStatus(fetchedArticles: List<ArticleEntity>) {
        val existingArticles = articleDao.getArticlesImmediate(fetchedArticles.map { it.id }).associateBy { it.id }
        val now = Instant.now()
        articleDao.insertArticles(fetchedArticles.map { it.reconciledWith(existingArticles[it.id], now) })
    }

    /**
     * A full sync only asks for unread entries, so anything read or deleted elsewhere simply stops
     * being returned. Without this it would sit in the cache as unread forever.
     */
    private suspend fun dropUnreadArticlesMissingFrom(fetchedIds: Set<String>) {
        val stale = articleDao.getSyncedUnreadIds().filterNot { it in fetchedIds }
        if (stale.isEmpty()) return
        Log.d(TAG, "Dropping ${stale.size} locally unread articles the backend no longer returns")
        stale.chunked(DELETE_CHUNK).forEach { articleDao.deleteByIds(it) }
    }

    private suspend fun pruneExpiredReadArticles() {
        val removed = articleDao.deleteArticlesReadBefore(Instant.now().minus(READ_ARTICLE_RETENTION))
        if (removed > 0) Log.d(TAG, "Pruned $removed read articles past the retention window")
    }

    private suspend fun pushPendingStatuses() {
        val pending = articleDao.getPendingStatuses()
        if (pending.isEmpty()) return
        Log.i(TAG, "Pushing ${pending.size} queued status changes")
        pending.groupBy { it.status ?: ArticleStatus.UNREAD }.forEach { (status, queued) ->
            pushStatusOrLeaveQueued(queued.map { it.id }, status)
        }
    }

    /**
     * A failed push is not an error the caller has to handle: the change stays queued and the next
     * sync tries again. Cancellation is not a failure and has to keep propagating.
     */
    private suspend fun pushStatusOrLeaveQueued(articleIds: List<String>, status: ArticleStatus) {
        try {
            // Cleared chunk by chunk so a failure halfway through does not requeue what got through.
            articleIds.chunked(STATUS_UPDATE_CHUNK).forEach { chunk ->
                api.updateEntriesStatus(chunk.map { it.toLong() }, status)
                articleDao.clearPendingSync(chunk, status)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Status push failed; queued for the next sync: ${e.message}")
        }
    }

    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) {
        if (articleIds.isEmpty()) return
        // Queued first: if the push fails (offline, server down) the next sync picks it up.
        val readAt = Instant.now().takeIf { newStatus == ArticleStatus.READ }
        articleDao.updateStatusForIds(articleIds, newStatus, readAt)
        pushStatusOrLeaveQueued(articleIds, newStatus)
    }

    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    /**
     * Unread articles reduced to what background prefetching downloads. Reading them as full
     * entries would load every cached body and run a feed lookup per row, for fields prefetching
     * never touches.
     */
    suspend fun getPrefetchTargets(): List<PrefetchTarget> = articleDao.getPrefetchTargets()

    suspend fun getFeed(feedId: Long): Feed? {
        val entity = feedDao.getFeedById(feedId) ?: return null
        return entity.toFeed()
    }

    private suspend fun List<ArticleEntity>.mapToEntries(): List<Entry> = map { article ->
        val feed = feedDao.getFeedById(article.feedId) ?: throw IllegalStateException("Feed not found")
        article.toEntry(feed)
    }
}

/**
 * Merges a freshly fetched article with what is already cached. The backend owns the read state —
 * that is what makes a status change from another client land here. The one thing it cannot know
 * about is a local change that has not been pushed yet, so that one wins and stays queued.
 *
 * Read times are local bookkeeping: no backend reports them, so an article that arrives already
 * read without a recorded time is stamped [now], and one that goes back to unread loses its stamp.
 */
internal fun ArticleEntity.reconciledWith(local: ArticleEntity?, now: Instant): ArticleEntity {
    val merged = if (local != null && local.pendingSync) {
        copy(status = local.status, pendingSync = true)
    } else this
    val readAt = if (merged.status == ArticleStatus.READ) local?.readAt ?: now else null
    return merged.copy(readAt = readAt)
}

private fun ArticleEntity.toEntry(feedEntity: FeedEntity): Entry = Entry(
    id = id.toLong(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feed = feedEntity.toFeed(),
    readingTime = readingTime,
    enclosures = enclosures,
    status = status ?: ArticleStatus.UNREAD
)

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

private fun Entry.toEntity(): ArticleEntity = ArticleEntity(
    id = id.toString(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feedId = feed.id,
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)

private fun Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)
