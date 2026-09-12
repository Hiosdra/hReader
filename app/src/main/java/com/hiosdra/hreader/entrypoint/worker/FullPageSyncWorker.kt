package com.hiosdra.hreader.entrypoint.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.entrypoint.notification.AppNotificationFactory
import com.hiosdra.hreader.core.application.observability.SyncPerformanceOperation
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.application.sync.SyncFailure
import com.hiosdra.hreader.core.application.sync.SyncFailureReason
import com.hiosdra.hreader.core.application.sync.SyncFailureStage
import com.hiosdra.hreader.core.application.sync.toSyncFailure
import com.hiosdra.hreader.core.domain.service.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_RUN_ATTEMPTS = 5
private const val MAX_PAGES_PER_RUN = 100
private const val PROGRESS_REPORT_INTERVAL_MILLIS = 1000L
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
    private val articlePageRepository: ArticlePageStore,
    private val syncPerformanceLogger: SyncPerformanceTracker,
    private val preferencesManager: SyncPreferences,
    private val errorReportingManager: ErrorReporter,
    private val clock: Clock,
    private val syncHealth: SyncHealthStore,
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
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

    override suspend fun doWork(): Result {
        if (isSilenced()) return Result.success()

        val session = sessionGate.currentSession()
        return sessionGate.withSession(session) {
            try {
                if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()
                articlePageRepository.cleanupOrphanedPages()
                val totalTargets = articlePageRepository.countMissingPageTargets()
                val batch = articlePageRepository.getMissingPageTargets(MAX_PAGES_PER_RUN)
                if (batch.isEmpty()) return@withSession Result.success()

                total.set(totalTargets)
                done.set(0)
                coroutineScope {
                    val reporter = launch {
                        while (isActive) {
                            publishProgress()
                            delay(PROGRESS_REPORT_INTERVAL_MILLIS)
                        }
                    }
                    publishProgress()
                    try {
                        syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.FULL_PAGE_PREFETCH) {
                            articlePageRepository.prefetchPages(
                                entries = batch,
                                limit = null,
                                onProgress = { completed, _ -> done.set(completed) }
                            )
                        }
                    } finally {
                        reporter.cancel()
                    }
                }
                val remaining = articlePageRepository.countMissingPageTargets()
                done.set((totalTargets - remaining).coerceIn(0, totalTargets))
                publishProgress()
                when {
                    remaining == 0 -> Result.success()
                    shouldRetryFullPageSync(remaining, totalTargets, runAttemptCount) -> Result.retry()
                    else -> {
                        syncHealth.recordStageFailure(
                            completedAt = clock.instant().toEpochMilli(),
                            failure = SyncFailure(
                                stage = SyncFailureStage.FULL_PAGE,
                                reason = SyncFailureReason.UNKNOWN,
                                retryable = false
                            ),
                            runId = inputData.getString(KEY_SYNC_RUN_ID).orEmpty()
                        )
                        val message = applicationContext.resources.getQuantityString(
                            R.plurals.offline_original_pages_failed_count,
                            remaining,
                            remaining
                        )
                        errorReportingManager.captureMessage(message, "full_page_sync")
                        Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.toSyncFailure(SyncFailureStage.FULL_PAGE)
                val shouldRetry = failure.retryable && runAttemptCount < MAX_RUN_ATTEMPTS
                if (!shouldRetry) {
                    syncHealth.recordStageFailure(
                        completedAt = clock.instant().toEpochMilli(),
                        failure = failure,
                        runId = inputData.getString(KEY_SYNC_RUN_ID).orEmpty()
                    )
                }
                if (!shouldRetry) errorReportingManager.captureException(e, "full_page_sync")
                if (shouldRetry) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            KEY_ERROR_MESSAGE to applicationContext.getString(
                                R.string.offline_original_pages_download_failed
                            )
                        )
                    )
                }
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
        if (!setForegroundIfAllowed({ getForegroundInfo() }, { setForeground(it) })) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    private fun isSilenced(): Boolean {
        if (inputData.getBoolean(KEY_IGNORE_QUIET_HOURS, false)) return false
        if (!preferencesManager.getQuietHoursEnabled()) return false
        return isWithinQuietHours(
            hour = LocalTime.now(clock).hour,
            startHour = preferencesManager.getQuietHoursStartHour(),
            endHour = preferencesManager.getQuietHoursEndHour()
        )
    }
}
