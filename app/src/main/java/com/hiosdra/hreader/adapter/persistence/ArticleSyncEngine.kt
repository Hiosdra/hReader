package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.ArticleSyncStore
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.ArticleSyncResult
import com.hiosdra.hreader.core.application.sync.ArticleSyncCoordinator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ArticleSyncEngine(
    private val persistence: ArticleSyncPersistence,
    private val pendingStatusUploader: PendingArticleStatusUploader,
    private val offlineBacklogTopUp: OfflineBacklogTopUp,
    private val retentionCoordinator: ArticleRetentionCoordinator,
    private val api: FeedBackend,
    private val preferences: SyncPreferences,
    private val performance: SyncPerformanceTracker,
    private val backendIdentity: BackendIdentity
) : ArticleSyncStore {
    private val syncMutex = Mutex()
    private val syncCoordinator = ArticleSyncCoordinator(
        api = api,
        preferences = preferences,
        onNewFullSync = { persistence.clearFullSyncSeenState() }
    )

    override suspend fun refreshArticles(forceFullSync: Boolean): ArticleSyncResult = syncMutex.withLock {
        refreshArticlesInternal(forceFullSync)
    }

    private suspend fun refreshArticlesInternal(forceFullSync: Boolean): ArticleSyncResult {
        val currentOwner = backendIdentity.cacheOwnerKey()
        checkSession(currentOwner)
        pendingStatusUploader.push(currentOwner)

        val run = performance.measureSyncTime(SyncPerformanceOperation.ARTICLE_PAGES) {
            syncCoordinator.run(forceFullSync, currentOwner) { entries, fullSyncRunId ->
                persistence.persistPage(entries, fullSyncRunId)
            }
        }
        val useIncremental = run.isIncremental

        checkSession(currentOwner)
        val activeFeedIds = reconcileFeeds(currentOwner)
        if (!useIncremental) {
            val fullSyncRunId = checkNotNull(run.checkpoint.fullSyncRunId)
            persistence.finishFullSync(fullSyncRunId)
            preferences.setLastFullSyncTimestamp(run.checkpoint.startedAt)
        }
        retentionCoordinator.pruneExpiredReadArticles()
        offlineBacklogTopUp.run(currentOwner)

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

    private suspend fun reconcileFeeds(ownerKey: String): Set<Long> {
        checkSession(ownerKey)
        val fetchedFeeds = api.getFeeds().map { it.toArticleFeedEntity() }
        checkSession(ownerKey)
        return persistence.reconcileFeeds(fetchedFeeds)
    }

    private fun checkSession(ownerKey: String) {
        if (ownerKey.isNotBlank() && backendIdentity.cacheOwnerKey() != ownerKey) {
            throw StaleSyncSessionException()
        }
    }
}
