package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.notification.AppNotificationFactory
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.util.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import java.time.LocalTime

private const val MAX_RUN_ATTEMPTS = 5

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: ArticleRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val syncScheduler: SyncScheduler,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ContentSyncWorker"
    }

    private var foregroundUnavailable = false

    override suspend fun getForegroundInfo(): ForegroundInfo =
        AppNotificationFactory.syncForegroundInfo(
            context = applicationContext,
            workerId = id,
            title = inputData.getString(KEY_OPERATION_TITLE)
                ?: applicationContext.getString(R.string.notification_sync_title),
            text = applicationContext.getString(R.string.notification_sync_text)
        )

    override suspend fun doWork(): Result {
        if (isSilenced()) {
            Log.i(TAG, "Inside quiet hours; skipping this run")
            return Result.success()
        }
        return runSync()
    }

    private suspend fun runSync(): Result = try {
        val forceFullSync = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)
        Log.i(TAG, "Starting ContentSyncWorker (forceFullSync=$forceFullSync)")
        if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()

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
        if (e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Article synchronization failed."))
            )
        }
    }

    private suspend fun updateForeground() {
        if (foregroundUnavailable) return
        if (!setForegroundIfAllowed { setForeground(getForegroundInfo()) }) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    /**
     * Reported as success rather than retried: a retry would keep waking the radio through the
     * night, which is precisely what the setting exists to prevent. The periodic worker fires
     * again after the window closes.
     */
    private fun isSilenced(): Boolean {
        if (inputData.getBoolean(KEY_IGNORE_QUIET_HOURS, false)) return false
        if (!preferencesManager.getQuietHoursEnabled()) return false
        return isWithinQuietHours(
            hour = LocalTime.now().hour,
            startHour = preferencesManager.getQuietHoursStartHour(),
            endHour = preferencesManager.getQuietHoursEndHour()
        )
    }
}
