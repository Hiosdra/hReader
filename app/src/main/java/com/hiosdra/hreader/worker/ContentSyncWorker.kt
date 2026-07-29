package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.CancellationException

private const val MAX_RUN_ATTEMPTS = 5

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: ArticleRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val syncScheduler: SyncScheduler
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ContentSyncWorker"
    }

    override suspend fun doWork(): Result = try {
        val forceFullSync = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)
        Log.i(TAG, "Starting ContentSyncWorker (forceFullSync=$forceFullSync)")

        syncPerformanceLogger.measureSyncTime("Article refresh") {
            repository.refreshArticles(forceFullSync)
        }

        // Only when this worker runs on its own. Callers that chain a prefetch behind it already
        // have one queued, and enqueueing a second would replace the chained request mid-run.
        if (!inputData.getBoolean(KEY_PREFETCH_CHAINED, false)) syncScheduler.enqueuePrefetch()

        Log.i(TAG, "ContentSyncWorker completed successfully")
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "ContentSyncWorker failed: ${e.message}", e)
        // A 5xx or a dropped connection is worth another attempt; a 4xx (bad token, bad request)
        // will fail identically every time, so it waits for the next period instead.
        if (e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
    }
}
