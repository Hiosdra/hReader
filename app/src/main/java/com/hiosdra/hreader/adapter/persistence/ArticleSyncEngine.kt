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
import com.hiosdra.hreader.core.application.exception.BackendNotConfiguredException
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.ArticleSyncStore
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.application.sync.ArticleSyncCoordinator
import com.hiosdra.hreader.core.application.sync.ArticleSyncResult
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
    private val backendIdentity: BackendIdentity,
    private val cacheStore: CacheStore,
    private val preferenceWrites: PreferenceWriteBarrier,
    private val sessionGate: SyncSessionGate
) : ArticleSyncStore {
    private val syncMutex = Mutex()
    private val syncCoordinator = ArticleSyncCoordinator(
        api = api,
        preferences = preferences,
        preferenceWrites = preferenceWrites,
        sessionGate = sessionGate,
        onNewFullSync = { fullSyncSeenDao.deleteAll() }
    )

    override suspend fun refreshArticles(forceFullSync: Boolean): ArticleSyncResult = syncMutex.withLock {
        refreshArticlesInternal(forceFullSync)
    }

    private suspend fun refreshArticlesInternal(forceFullSync: Boolean): ArticleSyncResult {
        cacheStore.ensureCacheOwnerWhenConfigured()
        if (!backendIdentity.isComplete()) {
            throw BackendNotConfiguredException("The active backend is not configured")
        }
        val session = sessionGate.currentSession()
        checkSession(session)
        checkCacheOwner(session)
        pushPendingStatuses(session)

        val run = performance.measureSyncTime(SyncPerformanceOperation.ARTICLE_PAGES) {
            syncCoordinator.run(forceFullSync) { entries, fullSyncRunId ->
                persistPage(session, entries, fullSyncRunId)
            }
        }
        val useIncremental = run.isIncremental
        performance.logSyncMode(useIncremental, preferences.getLastSyncTimestamp().takeIf { it > 0 })

        checkSession(session)
        val activeFeedIds = reconcileFeeds(session)
        if (!useIncremental) {
            val fullSyncRunId = checkNotNull(run.checkpoint.fullSyncRunId)
            sessionGate.withSession(session) {
                dropUnreadArticlesMissingFrom(fullSyncRunId)
                fullSyncSeenDao.deleteRun(fullSyncRunId)
                preferences.setLastFullSyncTimestamp(run.checkpoint.startedAt)
                preferenceWrites.awaitWrites()
            }
        }
        sessionGate.withSession(session) {
            pruneExpiredReadArticles()
        }
        topUpOfflineBacklog(session)

        checkSession(session)
        sessionGate.withSession(session) {
            preferences.setLastSyncTimestamp(run.checkpoint.startedAt)
            preferenceWrites.awaitWrites()
        }
        performance.logBatchInfo(ENTRIES_PAGE_LIMIT, run.stats.fetched)
        performance.logArticleSyncStats(run.stats)
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

    private suspend fun topUpOfflineBacklog(session: SyncSession) {
        val target = preferences.getOfflineBacklogTarget()
        if (target <= 0) return

        var storedCount = articleDao.countArticles()
        if (storedCount >= target) return

        try {
            performance.measureSyncTime(SyncPerformanceOperation.OFFLINE_BACKLOG_TOP_UP) {
                var cursor: String? = null
                var pages = 0
                while (storedCount < target && pages < MAX_BACKLOG_PAGES) {
                    checkSession(session)
                    val page = sessionGate.withSession(session) {
                        api.getRecentEntries(limit = ENTRIES_PAGE_LIMIT, cursor = cursor)
                    }
                    checkSession(session)
                    if (page.entries.isEmpty()) break

                    val pageIds = page.entries.map { it.id.toString() }
                    val existingIds = articleDao.getExistingIds(pageIds).toHashSet()
                    val missing = page.entries
                        .filterNot { it.id.toString() in existingIds }
                        .take(target - storedCount)
                    if (missing.isNotEmpty()) {
                        persistBacklogPage(session, missing)
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

    private suspend fun persistBacklogPage(session: SyncSession, entries: List<Entry>) {
        sessionGate.withSession(session) {
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
    }

    private suspend fun persistPage(
        session: SyncSession,
        entries: List<Entry>,
        fullSyncRunId: String?
    ): ArticleSyncStats {
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
            credibilityStore.invalidateForEntries(result.invalidatedEntryIds, session)
        }
        return result.stats
    }

    private suspend fun reconcileFeeds(session: SyncSession): Set<Long> = sessionGate.withSession(session) {
        val fetchedFeeds = api.getFeeds().map { it.toArticleFeedEntity() }
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
        fetchedFeeds.mapTo(hashSetOf()) { it.id }
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
        var removed = 0
        while (true) {
            val stale = fullSyncSeenDao.getSyncedUnreadIdsMissingFrom(fullSyncRunId, DELETE_CHUNK)
            if (stale.isEmpty()) break
            articleDao.deleteByIds(stale)
            removed += stale.size
        }
        if (removed > 0) {
            Log.d(TAG, "Dropping $removed locally unread articles the backend no longer returns")
        }
    }

    private suspend fun pruneExpiredReadArticles() {
        val removed = articleDao.deleteArticlesReadBefore(Instant.now().minus(READ_ARTICLE_RETENTION))
        if (removed > 0) Log.d(TAG, "Pruned $removed read articles past the retention window")
    }

    private suspend fun pushPendingStatuses(session: SyncSession) {
        checkSession(session)
        val pending = sessionGate.withSession(session) { articleDao.getPendingStatuses() }
        if (pending.isEmpty()) return
        Log.i(TAG, "Pushing ${pending.size} queued status changes")
        pending.groupBy { it.status ?: ArticleStatus.UNREAD }.forEach { (status, queued) ->
            checkSession(session)
            try {
                queued.map { it.id }.chunked(STATUS_UPDATE_CHUNK).forEach { chunk ->
                    sessionGate.withSession(session) {
                        api.updateEntriesStatus(chunk.map { it.toLong() }, status)
                        articleDao.clearPendingSync(chunk, status)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Status push failed; queued for the next sync: ${e.message}")
            }
            checkSession(session)
        }
    }

    private fun checkSession(session: SyncSession) {
        if (!sessionGate.isCurrent(session)) throw StaleSyncSessionException()
    }

    private fun checkCacheOwner(session: SyncSession) {
        if (preferences.getCacheOwnerKey() != session.ownerKey) {
            throw StaleSyncSessionException()
        }
    }

    private data class PagePersistenceResult(
        val stats: ArticleSyncStats,
        val invalidatedEntryIds: List<Long>
    )
}
