package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.ENTRIES_PAGE_LIMIT
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.domain.model.Entry
import kotlinx.coroutines.CancellationException

private const val MAX_BACKLOG_PAGES = 25

internal class OfflineBacklogTopUp(
    private val articleStats: com.hiosdra.hreader.adapter.persistence.room.dao.ArticleStatsDao,
    private val articleRecordDao: com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao,
    private val api: FeedBackend,
    private val persistence: ArticleSyncPersistence,
    private val preferences: SyncPreferences,
    private val performance: SyncPerformanceTracker,
    private val backendIdentity: BackendIdentity
) {
    suspend fun run(ownerKey: String) {
        val target = preferences.getOfflineBacklogTarget()
        if (target <= 0) return

        var storedCount = articleStats.countArticles()
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
                    val existingIds = articleRecordDao.getExistingIds(pageIds).toHashSet()
                    val missing = page.entries
                        .filterNot { it.id.toString() in existingIds }
                        .take(target - storedCount)
                    if (missing.isNotEmpty()) {
                        persistence.persistBacklogPage(missing)
                        storedCount += missing.size
                    }

                    pages++
                    cursor = page.cursor ?: break
                }
                Log.i(TAG, "Offline backlog holds $storedCount of $target articles")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Offline backlog top-up failed: ${error.message}")
        }
    }

    private fun checkSession(ownerKey: String) {
        if (ownerKey.isNotBlank() && backendIdentity.cacheOwnerKey() != ownerKey) {
            throw StaleSyncSessionException()
        }
    }

    private companion object {
        const val TAG = "OfflineBacklogTopUp"
    }
}
