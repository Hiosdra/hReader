package com.hiosdra.hreader.worker

import android.content.Context
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

class FullPageSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articlePageRepository: ArticlePageRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {
    private val done = AtomicInteger()
    private val total = AtomicInteger()

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
            if (inputData.getBoolean(KEY_USER_VISIBLE, false)) setForeground(getForegroundInfo())
            articlePageRepository.cleanupOrphanedPages()
            val targets = articleRepository.getPrefetchTargets()
            val outstanding = articlePageRepository.entriesMissingPages(
                targets.map { it.id.toLong() to it.url }
            )
            val batch = outstanding.take(MAX_PAGES_PER_RUN)
            if (batch.isEmpty()) return@withContext Result.success()

            total.set(outstanding.size)
            val reporter = launch {
                while (isActive) {
                    publishProgress()
                    delay(PROGRESS_REPORT_INTERVAL_MILLIS)
                }
            }
            try {
                syncPerformanceLogger.measureSyncTime("Full page prefetch") {
                    articlePageRepository.prefetchPages(
                        entries = batch,
                        limit = null,
                        onProgress = { completed, _ -> done.set(completed) }
                    )
                }
            } finally {
                reporter.cancel()
            }
            publishProgress()

            val remaining = articlePageRepository.entriesMissingPages(
                targets.map { it.id.toLong() to it.url }
            ).size
            if (remaining > 0 && runAttemptCount < MAX_RUN_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Full offline sync failed.")))
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
        if (inputData.getBoolean(KEY_USER_VISIBLE, false)) setForeground(getForegroundInfo())
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
