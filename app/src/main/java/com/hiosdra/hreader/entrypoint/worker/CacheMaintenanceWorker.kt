package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import kotlinx.coroutines.CancellationException

private const val MAX_RUN_ATTEMPTS = 3
private const val PREVIEW_BACKFILL_LIMIT = 250

class CacheMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleMaintenanceStore,
    private val articleContentRepository: ArticleContentStore,
    private val syncPerformanceLogger: SyncPerformanceTracker,
    private val errorReportingManager: ErrorReporter,
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val session = sessionGate.currentSession()
        return sessionGate.withSession(session) {
            try {
                syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ORPHANED_CONTENT_CLEANUP) {
                    articleContentRepository.cleanupOrphanedContent()
                }
                articleRepository.backfillMissingPreviews(PREVIEW_BACKFILL_LIMIT)
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cache maintenance failed: ${e.message}", e)
                val shouldRetry = runAttemptCount < MAX_RUN_ATTEMPTS
                if (!shouldRetry) errorReportingManager.captureException(e, "cache_maintenance")
                if (shouldRetry) Result.retry() else Result.failure()
            }
        }
    }

    private companion object {
        const val TAG = "CacheMaintenanceWorker"
    }
}
