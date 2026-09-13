package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FullSyncSeenDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FullSyncSeenEntity
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.ArticleSyncStore
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.ArticleSyncResult
import com.hiosdra.hreader.core.application.sync.ArticleSyncCoordinator
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

private const val TAG = "ArticleSyncEngine"
private const val STATUS_UPDATE_CHUNK = 200
private const val DELETE_CHUNK = 500
private const val MAX_BACKLOG_PAGES = 25
private val INCREMENTAL_SYNC_WINDOW: Duration = Duration.ofHours(24)
private val FULL_SYNC_INTERVAL: Duration = Duration.ofDays(7)
private val INCREMENTAL_SYNC_OVERLAP: Duration = Duration.ofMinutes(5)
private val READ_ARTICLE_RETENTION: Duration = Duration.ofDays(30)

internal class ArticleSyncEngine(
    private val articleDao: ArticleDao,
    private val articleContentDao: ArticleContentDao,
    private val feedDao: FeedDao,
    private val fullSyncSeenDao: FullSyncSeenDao,
    private val api: FeedBackend,
    private val db: AppDatabase,
    private val preferences: SyncPreferences,
    private val performance: SyncPerformanceTracker,
    private val imageStore: ArticleImageStore,
    private val credibilityStore: CredibilityStore,
    private val backendIdentity: BackendIdentity
) : ArticleSyncStore {
    private val syncMutex = Mutex()
    private val syncCoordinator = ArticleSyncCoordinator(
        api = api,
        preferences = preferences,
        onNewFullSync = { fullSyncSeenDao.deleteAll() }
    )

    override suspend fun refreshArticles(forceFullSync: Boolean): ArticleSyncResult = syncMutex.withLock {
        refreshArticlesInternal(forceFullSync)
    }

    private suspend fun refreshArticlesInternal(forceFullSync: Boolean): ArticleSyncResult {
        val currentOwner = backendIdentity.cacheOwnerKey()
        checkSession(currentOwner)
        pushPendingStatuses(currentOwner)

        val run = performance.measureSyncTime(SyncPerformanceOperation.ARTICLE_PAGES) {
            syncCoordinator.run(forceFullSync, currentOwner) { entries, fullSyncRunId ->
                persistPage(entries, fullSyncRunId)
            }
        }
        val useIncremental = run.isIncremental

        checkSession(currentOwner)
        val activeFeedIds = reconcileFeeds(currentOwner)
        if (!useIncremental) {
            val fullSyncRunId = checkNotNull(run.checkpoint.fullSyncRunId)
            dropUnreadArticlesMissingFrom(fullSyncRunId)
            fullSyncSeenDao.deleteRun(fullSyncRunId)
            preferences.setLastFullSyncTimestamp(run.checkpoint.startedAt)
        }
        pruneExpiredReadArticles()
        topUpOfflineBacklog(currentOwner)

        performance.logBatchInfo(ENTRIES_PAGE_LIMIT, run.stats.fetched)
        performance.logArticleSyncStats(run.stats)
        preferences.setLastSyncTimestamp(run.checkpoint.startedAt)
        val updatedFeedIds = run.fetchedFeedIds.intersect(activeFeedIds)
        val successfulFeedIds = if (useIncremental) updatedFeedIds else activeFeedIds
        return ArticleSyncResult(
            activeFeedIds = activeFeedIds,
            successfulFeedIds = successfulFeedIds,
            updatedFeedIds = updatedFeedIds,
            skippedFeedIds = activeFeedIds - updatedFeedIds,
            newArticles = run.stats.inserted,
            latestPublishedAtByFeed = run.latestPublishedAtByFeed
        )
    }

    private suspend fun topUpOfflineBacklog(ownerKey: String) {
        val target = preferences.getOfflineBacklogTarget()
        if (target <= 0) return

        var storedCount = articleDao.countArticles()
        if (storedCount >= target) return

        try {
            performance.measureSyncTime(SyncPerformanceOperation.OFFLINE_BACKLOG_TOP_UP) {
                var cursor: String? = null
                var pages = 0
                while (storedCount < target && pages < MAX_BACKLOG_PAGES) {
                    checkSession(ownerKey)
                    val page = api.getRecentEntries(limit = ENTRIES_PAGE_LIMIT, cursor = cursor)
                    checkSession(ownerKey)
                    if (page.entries.isEmpty()) break

                    val pageIds = page.entries.map { it.id.toString() }
                    val existingIds = articleDao.getExistingIds(pageIds).toHashSet()
                    val missing = page.entries
                        .filterNot { it.id.toString() in existingIds }
                        .take(target - storedCount)
                    if (missing.isNotEmpty()) {
                        persistBacklogPage(missing)
                        storedCount += missing.size
                    }

                    pages++
                    cursor = page.cursor ?: break
                }
                Log.i(TAG, "Offline backlog holds $storedCount of $target articles")
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
        val articles = entries.map { entry ->
            entry.toEntity().copy(
                backlogFetchedAt = now,
                readAt = now.takeIf { entry.status == ArticleStatus.READ }
            )
        }
        db.withTransaction {
            feedDao.insertFeeds(feeds.preservingAiOverviewPreloading())
            articleDao.insertArticles(articles)
        }
    }

    private suspend fun persistPage(entries: List<Entry>, fullSyncRunId: String?): ArticleSyncStats {
        val feeds = entries.associate { it.feed.id to it.feed.toArticleFeedEntity() }.values.toList()
        val articles = entries.map { it.toEntity() }
        val result = db.withTransaction {
            feedDao.insertFeeds(feeds.preservingAiOverviewPreloading())
            insertArticlesPreservingPendingStatus(articles).also { persistenceResult ->
                if (fullSyncRunId != null) {
                    fullSyncSeenDao.insertAll(
                        entries.map { entry ->
                            FullSyncSeenEntity(fullSyncRunId, entry.id.toString())
                        }
                    )
                }
                persistenceResult.invalidatedEntryIds
                    .chunked(DELETE_CHUNK)
                    .forEach { entryIds -> articleContentDao.deleteArticlesContent(entryIds) }
            }
        }
        for (entryId in result.invalidatedEntryIds) {
            try {
                imageStore.invalidateArticleImages(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not invalidate cached assets for entry $entryId: ${e.message}")
            }
        }
        if (result.invalidatedEntryIds.isNotEmpty()) {
            credibilityStore.invalidateForEntries(result.invalidatedEntryIds)
        }
        return result.stats
    }

    private suspend fun reconcileFeeds(ownerKey: String): Set<Long> {
        checkSession(ownerKey)
        val fetchedFeeds = api.getFeeds().map { it.toArticleFeedEntity() }
        checkSession(ownerKey)
        db.withTransaction {
            val incoming = fetchedFeeds.preservingAiOverviewPreloading()
            val incomingIds = incoming.mapTo(hashSetOf()) { it.id }
            val staleIds = feedDao.getAllIds().filterNot(incomingIds::contains)
            if (incoming.isNotEmpty()) feedDao.insertFeeds(incoming)
            staleIds.chunked(DELETE_CHUNK).forEach { feedIds ->
                articleDao.deleteByFeedIds(feedIds)
                feedDao.deleteByIds(feedIds)
            }
        }
        return fetchedFeeds.mapTo(hashSetOf()) { it.id }
    }

    private suspend fun List<FeedEntity>.preservingAiOverviewPreloading(): List<FeedEntity> {
        val existingSettings = feedDao.getAllFeedsImmediate().associate { it.id to it.preloadAiOverview }
        return map { feed -> feed.copy(preloadAiOverview = existingSettings[feed.id] ?: feed.preloadAiOverview) }
    }

    private suspend fun insertArticlesPreservingPendingStatus(
        fetchedArticles: List<ArticleEntity>
    ): PagePersistenceResult {
        val existingArticles = articleDao.getArticlesImmediate(fetchedArticles.map { it.id }).associateBy { it.id }
        val now = Instant.now()
        val reconciled = fetchedArticles.map { fetched ->
            fetched to fetched.reconciledWith(existingArticles[fetched.id], now)
        }
        val changed = reconciled.filter { (fetched, merged) -> existingArticles[fetched.id] != merged }
        val invalidatedEntryIds = reconciled.mapNotNull { (fetched, _) ->
            val local = existingArticles[fetched.id] ?: return@mapNotNull null
            fetched.id.toLongOrNull()?.takeIf {
                local.url != fetched.url ||
                    local.content != fetched.content ||
                    local.enclosures != fetched.enclosures ||
                    local.leadImageUrl != fetched.leadImageUrl ||
                    local.title != fetched.title ||
                    local.author != fetched.author ||
                    local.publishedAt != fetched.publishedAt
            }
        }
        if (changed.isNotEmpty()) articleDao.insertArticles(changed.map { it.second })
        val inserted = changed.count { (fetched, _) -> existingArticles[fetched.id] == null }
        return PagePersistenceResult(
            stats = ArticleSyncStats(
                fetched = fetchedArticles.size,
                unchanged = fetchedArticles.size - changed.size,
                inserted = inserted,
                updated = changed.size - inserted
            ),
            invalidatedEntryIds = invalidatedEntryIds
        )
    }

    private suspend fun dropUnreadArticlesMissingFrom(fullSyncRunId: String) {
        val stale = fullSyncSeenDao.getSyncedUnreadIdsMissingFrom(fullSyncRunId)
        if (stale.isEmpty()) return
        Log.d(TAG, "Dropping ${stale.size} locally unread articles the backend no longer returns")
        stale.chunked(DELETE_CHUNK).forEach { articleDao.deleteByIds(it) }
    }

    private suspend fun pruneExpiredReadArticles() {
        val removed = articleDao.deleteArticlesReadBefore(Instant.now().minus(READ_ARTICLE_RETENTION))
        if (removed > 0) Log.d(TAG, "Pruned $removed read articles past the retention window")
    }

    private suspend fun pushPendingStatuses(ownerKey: String) {
        val pending = articleDao.getPendingStatuses()
        if (pending.isEmpty()) return
        Log.i(TAG, "Pushing ${pending.size} queued status changes")
        pending.groupBy { it.status ?: ArticleStatus.UNREAD }.forEach { (status, queued) ->
            checkSession(ownerKey)
            try {
                queued.map { it.id }.chunked(STATUS_UPDATE_CHUNK).forEach { chunk ->
                    api.updateEntriesStatus(chunk.map { it.toLong() }, status)
                    checkSession(ownerKey)
                    articleDao.clearPendingSync(chunk, status)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Status push failed; queued for the next sync: ${e.message}")
            }
        }
    }

    private fun checkSession(ownerKey: String) {
        if (ownerKey.isNotBlank() && backendIdentity.cacheOwnerKey() != ownerKey) {
            throw StaleSyncSessionException()
        }
    }

    private data class PagePersistenceResult(
        val stats: ArticleSyncStats,
        val invalidatedEntryIds: List<Long>
    )
}
