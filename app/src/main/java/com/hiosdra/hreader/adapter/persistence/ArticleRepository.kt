package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.buildFtsMatchQuery
import com.hiosdra.hreader.adapter.persistence.room.buildLikePattern
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import com.hiosdra.hreader.core.application.port.out.ArticleStore
import com.hiosdra.hreader.core.application.port.out.ArticleListWindow
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.content.extractArticlePreview
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

private const val TAG = "ArticleRepository"

/** Miniflux and FreshRSS both take the ids inline, so one huge request is split into chunks. */
private const val STATUS_UPDATE_CHUNK = 200

/**
 * Deleting and updating in chunks keeps the statement below SQLite's bound-variable ceiling, which
 * on Android is 999. "Mark all as read" over a large backlog reaches that on its own.
 */
private const val DELETE_CHUNK = 500
private const val LOCAL_UPDATE_CHUNK = 400

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

/** Bounds a backlog top-up against a backend that keeps handing back entries already stored. */
private const val MAX_BACKLOG_PAGES = 25

/**
 * Ceiling on one sync's pagination — forty thousand entries at the current page size. Without it a
 * backend that keeps returning a continuation walks for as long as the worker is allowed to live,
 * and a cache that large has other problems anyway.
 */
private const val MAX_SYNC_PAGES = 200

/**
 * A page is a little over a screenful, so scrolling stays ahead of the reader without loading the
 * cache back into memory. Placeholders are off: the list groups rows by publication day, and a
 * header cannot be derived from a row that is not there yet.
 */
private val PAGING_CONFIG = PagingConfig(
    pageSize = 40,
    prefetchDistance = 20,
    enablePlaceholders = false
)

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val articleContentDao: ArticleContentDao,
    private val feedDao: FeedDao,
    private val api: FeedBackend,
    private val db: AppDatabase,
    private val preferencesManager: SyncPreferences,
    private val syncPerformanceLogger: SyncPerformanceTracker
) : ArticleStore {
    /**
     * The article list, filtered and searched in SQLite and read a page at a time. All three used
     * to happen in the view model over the whole cache: every article body in memory, scanned again
     * on each keystroke.
     */
    override fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListEntry>> {
        val match = buildFtsMatchQuery(query.searchQuery.trim())
        return Pager(PAGING_CONFIG) {
            if (match == null) {
                articleDao.pageArticles(
                    feedId = query.feedId,
                    starredOnly = query.starredOnly,
                    includeRead = query.includeRead,
                    sessionStart = query.sessionStart
                )
            } else {
                articleDao.pageSearchResults(
                    feedId = query.feedId,
                    starredOnly = query.starredOnly,
                    includeRead = query.includeRead,
                    sessionStart = query.sessionStart,
                    ftsQuery = match,
                    titleQuery = buildLikePattern(query.searchQuery)
                )
            }
        }.flow.map { page -> page.map { it.toListEntry() } }
    }

    override suspend fun listWindow(
        query: ArticleListQuery,
        articleId: Long,
        radius: Int
    ): ArticleListWindow {
        val totalCount = articleDao.countList(
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart
        )
        if (totalCount == 0) {
            return ArticleListWindow(emptyList(), 0, 0, 0)
        }

        val publishedAt = articleDao.getPublishedAt(articleId.toString())
        val currentPosition = publishedAt?.let {
            articleDao.countArticlesBefore(
                articleId = articleId.toString(),
                publishedAt = it,
                feedId = query.feedId,
                starredOnly = query.starredOnly,
                includeRead = query.includeRead,
                sessionStart = query.sessionStart
            )
        }?.coerceIn(0, (totalCount - 1).coerceAtLeast(0)) ?: 0
        val windowStart = (currentPosition - radius).coerceAtLeast(0)
        val ids = articleDao.getListWindow(
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart,
            limit = (radius * 2 + 1).coerceAtLeast(1),
            offset = windowStart
        ).toArticleIds("the reader's window")
        return ArticleListWindow(
            ids = ids,
            totalCount = totalCount,
            windowStartIndex = windowStart,
            currentIndex = (currentPosition - windowStart).coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        )
    }

    override suspend fun unreadIds(feedId: Long?, starredOnly: Boolean): List<Long> =
        articleDao.getUnreadIds(feedId, starredOnly).toArticleIds("the unread set")

    override fun observeUnreadCount(feedId: Long?, starredOnly: Boolean): Flow<Int> =
        articleDao.observeUnreadCountFor(feedId, starredOnly)

    override fun observeReadCount(feedId: Long?, starredOnly: Boolean): Flow<Int> =
        articleDao.observeReadCountFor(feedId, starredOnly)

    override fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesWithFeedByIds(ids.map { it.toString() }).map { rows ->
            rows.map { it.toEntry() }
        }

    override suspend fun refreshArticles(forceFullSync: Boolean) {
        val syncStartTime = System.currentTimeMillis()

        // Local changes go up before the server state comes down, so reconciliation below can
        // treat the backend as authoritative without discarding anything the user just did.
        pushPendingStatuses()
        pushPendingStars()

        val useIncrementalSync = !forceFullSync && shouldUseIncrementalSync(syncStartTime)
        syncPerformanceLogger.logSyncMode(useIncrementalSync, getLastSyncTime().takeIf { it > 0 })

        val fetchedIds = mutableSetOf<String>()
        // Whether the backend ran out of pages, as opposed to [MAX_SYNC_PAGES] cutting the walk
        // short. Only the first case leaves [fetchedIds] holding the server's whole answer, which
        // is the one thing the reconciliation below assumes about it.
        var walkedToTheEnd = false
        syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ARTICLE_PAGES) {
            var cursor: String? = null
            var pages = 0
            while (true) {
                val page = fetchArticleBatch(useIncrementalSync, ENTRIES_PAGE_LIMIT, cursor)
                if (page.entries.isNotEmpty()) {
                    // Persisting per page keeps memory flat regardless of backlog size and leaves
                    // partial progress behind if the sync is interrupted.
                    persistPage(page.entries)
                    fetchedIds += page.entries.map { it.id.toString() }
                }
                cursor = page.cursor
                pages++
                if (cursor == null || page.entries.isEmpty()) {
                    walkedToTheEnd = true
                    break
                }
                if (pages >= MAX_SYNC_PAGES) {
                    Log.w(TAG, "Stopped paging after $pages pages; the rest waits for the next sync")
                    break
                }
            }
        }

        reconcileFeeds()

        // Reconciliation deletes every locally unread article this run did not see, so it may only
        // run over a complete answer. Cut short by the page cap it would delete precisely the part
        // of the backlog it never got to. The full-sync stamp is withheld for the same reason: it
        // is what makes the next run go full again rather than incremental over a cache that was
        // never reconciled.
        if (!useIncrementalSync && walkedToTheEnd) {
            dropUnreadArticlesMissingFrom(fetchedIds)
            preferencesManager.setLastFullSyncTimestamp(syncStartTime)
        }
        pruneExpiredReadArticles()
        topUpOfflineBacklog()

        syncPerformanceLogger.logBatchInfo(ENTRIES_PAGE_LIMIT, fetchedIds.size)
        preferencesManager.setLastSyncTimestamp(syncStartTime)
    }

    /**
     * Unread articles are whatever is left over, which on a quiet week is nothing. This tops the
     * cache up to the configured target with recent entries regardless of read state, so a reader
     * heading offline leaves with a known amount of material rather than a hopeful one.
     *
     * A failure here does not fail the sync: the articles the user actually subscribed to are
     * already stored by this point, and the backlog is a best-effort extra.
     */
    private suspend fun topUpOfflineBacklog() {
        val target = preferencesManager.getOfflineBacklogTarget()
        if (target <= 0) return

        val storedIds = articleDao.getAllIds().toHashSet()
        if (storedIds.size >= target) return

        try {
            syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.OFFLINE_BACKLOG_TOP_UP) {
                var cursor: String? = null
                var pages = 0
                while (storedIds.size < target && pages < MAX_BACKLOG_PAGES) {
                    val page = api.getRecentEntries(limit = ENTRIES_PAGE_LIMIT, cursor = cursor)
                    if (page.entries.isEmpty()) break

                    val missing = page.entries
                        .filterNot { it.id.toString() in storedIds }
                        .take(target - storedIds.size)
                    if (missing.isNotEmpty()) {
                        persistBacklogPage(missing)
                        storedIds += missing.map { it.id.toString() }
                    }

                    pages++
                    cursor = page.cursor ?: break
                }
                Log.i(TAG, "Offline backlog holds ${storedIds.size} of $target articles")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Offline backlog top-up failed: ${e.message}")
        }
    }

    private suspend fun persistBacklogPage(entries: List<Entry>) {
        val now = Instant.now()
        val feeds = entries.associate { it.feed.id to it.feed.toArticleFeedEntity() }.values.toList()
        // Read entries get their read time stamped now, so retention starts counting from the
        // download rather than leaving them un-prunable forever.
        val articles = entries.map { entry ->
            entry.toEntity().copy(
                backlogFetchedAt = now,
                readAt = now.takeIf { entry.status == ArticleStatus.READ }
            )
        }
        db.withTransaction {
            feedDao.insertFeeds(feeds)
            articleDao.insertArticles(articles)
        }
    }

    private suspend fun persistPage(entries: List<Entry>) {
        val feeds = entries.associate { it.feed.id to it.feed.toArticleFeedEntity() }.values.toList()
        val articles = entries.map { it.toEntity() }
        db.withTransaction {
            feedDao.insertFeeds(feeds)
            insertArticlesPreservingPendingStatus(articles)
        }
    }

    private suspend fun reconcileFeeds() {
        val incoming = api.getFeeds().map { it.toArticleFeedEntity() }
        val incomingIds = incoming.mapTo(hashSetOf()) { it.id }
        val staleIds = feedDao.getAllIds().filterNot(incomingIds::contains)
        db.withTransaction {
            if (incoming.isNotEmpty()) feedDao.insertFeeds(incoming)
            staleIds.chunked(DELETE_CHUNK).forEach { feedIds ->
                articleDao.deleteByFeedIds(feedIds)
                feedDao.deleteByIds(feedIds)
            }
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
        fetchedArticles.mapNotNull { fetched ->
            val local = existingArticles[fetched.id] ?: return@mapNotNull null
            fetched.id.toLongOrNull()
                ?.takeIf { local.url != fetched.url || local.content != fetched.content }
        }.chunked(DELETE_CHUNK).forEach { changedEntryIds ->
            articleContentDao.deleteArticlesContent(changedEntryIds)
        }
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

    private suspend fun pushPendingStars() {
        val pending = articleDao.getPendingStars()
        if (pending.isEmpty()) return
        Log.i(TAG, "Pushing ${pending.size} queued stars")
        pending.groupBy { it.starred }.forEach { (starred, queued) ->
            pushStarOrLeaveQueued(queued.map { it.id }, starred)
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

    private suspend fun pushStarOrLeaveQueued(articleIds: List<String>, starred: Boolean) {
        try {
            articleIds.chunked(STATUS_UPDATE_CHUNK).forEach { chunk ->
                api.updateEntriesStarred(chunk.map { it.toLong() }, starred)
                articleDao.clearStarredPendingSync(chunk, starred)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Star push failed; queued for the next sync: ${e.message}")
        }
    }

    override suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) {
        if (articleIds.isEmpty()) return
        // Queued first: if the push fails (offline, server down) the next sync picks it up.
        val readAt = Instant.now().takeIf { newStatus == ArticleStatus.READ }
        articleIds.chunked(LOCAL_UPDATE_CHUNK).forEach { chunk ->
            articleDao.updateStatusForIds(chunk, newStatus, readAt)
        }
    }

    override suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    /**
     * Of [articleIds], those still read and stamped no later than [readBefore]. What a bulk "mark
     * as read" may take back: an article the reader has opened since carries a later stamp, and
     * reverting that one too would undo something they did on purpose.
     */
    override suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long> =
        articleIds.map { it.toString() }
            .chunked(LOCAL_UPDATE_CHUNK)
            .flatMap { articleDao.getIdsReadNoLaterThan(it, readBefore) }
            .toArticleIds("an undo")

    override suspend fun updateStarred(articleId: Long, starred: Boolean) {
        val ids = listOf(articleId.toString())
        articleDao.updateStarredForIds(ids, starred)
    }

    /**
     * Articles only carry a preview from the version that introduced the column, so everything
     * cached before it has none. Filling them in from the background beats a migration that would
     * have to run an HTML parser over every stored body inside one transaction.
     */
    override suspend fun backfillMissingPreviews(limit: Int): Int {
        val stale = articleDao.getArticlesMissingPreview(limit)
        if (stale.isEmpty()) return 0
        var filled = 0
        stale.forEach { article ->
            // Written even when it comes out empty. An article whose body is a single figure or an
            // embed yields no readable text, and leaving those rows null meant the same five
            // hundred were selected and parsed again on every prefetch, for ever.
            articleDao.setPreview(article.id, extractArticlePreview(article.content).orEmpty())
            filled++
        }
        if (filled > 0) Log.d(TAG, "Backfilled $filled article previews")
        return filled
    }

    /**
     * Unread articles reduced to what background prefetching downloads. Reading them as full
     * entries would load every cached body and run a feed lookup per row, for fields prefetching
     * never touches.
     */
    override suspend fun getPrefetchTargets(): List<PrefetchTarget> = articleDao.getPrefetchTargets()
        .mapNotNull { target ->
            target.id.toLongOrNull()?.let { id ->
                PrefetchTarget(id = id, url = target.url, enclosures = target.enclosures)
            }
        }

    override suspend fun getFeed(feedId: Long): Feed? = feedDao.getFeedById(feedId)?.toArticleFeed()

    suspend fun refreshArticles() = refreshArticles(forceFullSync = false)

    suspend fun backfillMissingPreviews() = backfillMissingPreviews(PREVIEW_BACKFILL_LIMIT)

    companion object {
        internal const val PREVIEW_BACKFILL_LIMIT = 500
    }
}

/**
 * The stored ids as the rest of the app uses them. The column is text because that is what both
 * backends hand over, but every value written to it is the decimal form of a [Long], so a token
 * that will not parse is a broken invariant rather than an article to be quietly left out of the
 * list — which is what dropping it silently looked like from the outside.
 */
private fun List<String>.toArticleIds(what: String): List<Long> {
    val ids = mapNotNull { it.toLongOrNull() }
    if (ids.size != size) Log.w(TAG, "Ignored ${size - ids.size} unreadable article ids in $what")
    return ids
}
