package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.local.repository.ArticlePageRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.notification.AppNotificationFactory
import com.hiosdra.hreader.util.ErrorReportingManager
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.util.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_RUN_ATTEMPTS = 5
private const val MAX_PAGES_PER_RUN = 100
private const val PROGRESS_REPORT_INTERVAL_MILLIS = 500L
private const val TAG = "FullPageSyncWorker"

internal fun shouldRetryFullPageSync(
    remaining: Int,
    previousOutstanding: Int,
    runAttemptCount: Int
): Boolean = remaining > 0 &&
    (remaining < previousOutstanding || runAttemptCount < MAX_RUN_ATTEMPTS)

class FullPageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articlePageRepository: ArticlePageRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val preferencesManager: PreferencesManager,
    private val errorReportingManager: ErrorReportingManager
) : CoroutineWorker(appContext, params) {
    private val done = AtomicInteger()
    private val total = AtomicInteger()
    private var foregroundUnavailable = false

    override suspend fun getForegroundInfo(): ForegroundInfo =
        AppNotificationFactory.syncForegroundInfo(
            context = applicationContext,
            workerId = id,
            title = inputData.getString(KEY_OPERATION_TITLE)
                ?: applicationContext.getString(R.string.notification_full_offline_title),
            text = applicationContext.getString(R.string.notification_full_offline_text),
            done = done.get(),
            total = total.get()
        )

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        if (isSilenced()) return@withContext Result.success()

        try {
            if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()
            articlePageRepository.cleanupOrphanedPages()
            val targets = articleRepository.getPrefetchTargets()
            val outstanding = articlePageRepository.entriesMissingPages(
                targets.map { it.id.toLong() to it.url }
            )
            val batch = outstanding.take(MAX_PAGES_PER_RUN)
            if (batch.isEmpty()) return@withContext Result.success()

            val completedBeforeRun = (targets.size - outstanding.size).coerceAtLeast(0)
            total.set(targets.size)
            done.set(completedBeforeRun)
            val reporter = launch {
                while (isActive) {
                    publishProgress()
                    delay(PROGRESS_REPORT_INTERVAL_MILLIS)
                }
            }
            publishProgress()
            try {
                syncPerformanceLogger.measureSyncTime("Full page prefetch") {
                    articlePageRepository.prefetchPages(
                        entries = batch,
                        limit = null,
                        onProgress = { completed, _ -> done.set(completedBeforeRun + completed) }
                    )
                }
            } finally {
                reporter.cancel()
            }

            val remaining = articlePageRepository.entriesMissingPages(
                targets.map { it.id.toLong() to it.url }
            ).size
            done.set((targets.size - remaining).coerceIn(0, targets.size))
            publishProgress()
            when {
                remaining == 0 -> Result.success()
                shouldRetryFullPageSync(remaining, outstanding.size, runAttemptCount) -> Result.retry()
                else -> {
                    val message = "$remaining original pages could not be saved offline."
                    errorReportingManager.captureMessage(message, "full_page_sync")
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val shouldRetry = e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS
            if (!shouldRetry) errorReportingManager.captureException(e, "full_page_sync")
            if (shouldRetry) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Downloading original pages failed."))
            }
        }
    }

    private suspend fun publishProgress() {
        setProgress(
            workDataOf(
                KEY_PROGRESS_DONE to done.get(),
                KEY_PROGRESS_TOTAL to total.get()
            )
        )
        if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()
    }

    private suspend fun updateForeground() {
        if (foregroundUnavailable) return
        if (!setForegroundIfAllowed { setForeground(getForegroundInfo()) }) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

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
