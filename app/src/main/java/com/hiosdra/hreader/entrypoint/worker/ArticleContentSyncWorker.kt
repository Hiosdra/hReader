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
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.application.port.out.SyncPerformanceTracker
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.application.sync.SyncFailureStage
import com.hiosdra.hreader.core.domain.service.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_RUN_ATTEMPTS = 5

private const val PROGRESS_REPORT_INTERVAL_MILLIS = 1000L

private const val MAX_ARTICLES_PER_RUN = 500

private val IMAGE_STAGE_BUDGET_NANOS = TimeUnit.MINUTES.toNanos(3)

class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleMaintenanceStore,
    private val articleContentRepository: ArticleContentStore,
    private val syncPerformanceLogger: SyncPerformanceTracker,
    private val preferencesManager: SyncPreferences,
    private val errorReportingManager: ErrorReporter,
    private val clock: Clock,
    private val syncHealth: SyncHealthStore
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ArticleContentSyncWorker"
    }

    private val done = AtomicInteger()
    private val total = AtomicInteger()
    private var foregroundUnavailable = false

    override suspend fun getForegroundInfo(): ForegroundInfo =
        AppNotificationFactory.syncForegroundInfo(
            context = applicationContext,
            workerId = id,
            title = inputData.getString(KEY_OPERATION_TITLE)
                ?: applicationContext.getString(R.string.notification_sync_title),
            text = applicationContext.getString(R.string.notification_prefetch_text),
            done = done.get(),
            total = total.get()
        )

    override suspend fun doWork(): Result {
        if (isSilenced()) {
            Log.i(TAG, "Inside quiet hours; skipping the prefetch")
            return Result.success()
        }

        Log.i(TAG, "Starting ArticleContentSyncWorker")
        return try {
            if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()
            val downloadAllImages = inputData.getBoolean(KEY_DOWNLOAD_ALL_IMAGES, false)
            val syncMode = preferencesManager.getSyncMode()
            val contentTargets = articleRepository.getPrefetchTargets(
                limit = MAX_ARTICLES_PER_RUN,
                downloadAllImages = downloadAllImages
            )
            val imageTargets = if (downloadAllImages) {
                emptyList()
            } else {
                articleRepository.getPrefetchTargetsWithEnclosures(MAX_ARTICLES_PER_RUN)
            }
            val targets = (contentTargets + imageTargets).distinctBy { it.id }
            Log.i(TAG, "Found ${targets.size} local articles to prefetch")

            if (targets.isEmpty()) {
                Log.i(TAG, "No articles to prefetch")
                return Result.success()
            }

            downloadEnclosureImages(
                targets = targets,
                downloadAllImages = downloadAllImages,
                syncMode = syncMode
            )
            val remaining = prefetchArticleContent(targets, syncMode)

            if (remaining > 0 && shouldDrainRemaining() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                Log.i(TAG, "$remaining articles still without text; asking for another run")
                return Result.retry()
            }
            if (remaining > 0) Log.i(TAG, "$remaining articles still without text; left to the next sync")

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            val failure = e.toSyncFailure(SyncFailureStage.ARTICLE_CONTENT)
            val shouldRetry = failure.retryable && runAttemptCount < MAX_RUN_ATTEMPTS
            if (!shouldRetry) {
                syncHealth.recordStageFailure(
                    completedAt = clock.instant().toEpochMilli(),
                    failure = failure,
                    runId = inputData.getString(KEY_SYNC_RUN_ID).orEmpty()
                )
            }
            if (!shouldRetry) errorReportingManager.captureException(e, "article_content_sync")
            if (shouldRetry) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to applicationContext.getString(R.string.sync_article_content_failed)
                    )
                )
            }
        }
    }

    private fun shouldDrainRemaining(): Boolean = inputData.getBoolean(KEY_DRAIN_REMAINING, false)

    private fun isSilenced(): Boolean {
        if (inputData.getBoolean(KEY_IGNORE_QUIET_HOURS, false)) return false
        if (!preferencesManager.getQuietHoursEnabled()) return false
        return isWithinQuietHours(
            hour = LocalTime.now(clock).hour,
            startHour = preferencesManager.getQuietHoursStartHour(),
            endHour = preferencesManager.getQuietHoursEndHour()
        )
    }

    private suspend fun prefetchArticleContent(
        targets: List<PrefetchTarget>,
        syncMode: SyncMode
    ): Int = coroutineScope {
        val downloadAllImages = inputData.getBoolean(KEY_DOWNLOAD_ALL_IMAGES, false)
        val targetEntries = targets.map { it.id to it.url }
        val outstanding = if (downloadAllImages) {
            articleContentRepository.entriesMissingFullOfflinePreparation(targetEntries)
        } else {
            articleContentRepository.entriesMissingContent(targetEntries)
        }
        val batch = outstanding.take(MAX_ARTICLES_PER_RUN)
        if (batch.isEmpty()) return@coroutineScope 0

        syncPerformanceLogger.logBatchInfo(batch.size, outstanding.size)
        Log.d(TAG, "Prefetching content for ${batch.size} of ${outstanding.size} articles (background sync)")

        total.set(batch.size)
        val reporter = launch {
            while (isActive) {
                publishProgress()
                delay(PROGRESS_REPORT_INTERVAL_MILLIS)
            }
        }
        try {
            syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ARTICLE_CONTENT_PREFETCH) {
                articleContentRepository.prefetchArticleContent(
                    entries = batch,
                    limit = null,
                    downloadAllImages = downloadAllImages,
                    syncMode = syncMode,
                    onProgress = { completed, _ -> done.set(completed) }
                )
            }
        } finally {
            reporter.cancel()
        }
        publishProgress()
        outstanding.size - batch.size
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

    private suspend fun downloadEnclosureImages(
        targets: List<PrefetchTarget>,
        downloadAllImages: Boolean,
        syncMode: SyncMode
    ) {
        val enclosureImageEntries = targets.mapNotNull { target ->
            target.imageEnclosureUrls(downloadAllImages).takeIf { it.isNotEmpty() }?.let { urls ->
                target.id to urls
            }
        }
        if (enclosureImageEntries.isEmpty()) return

        val deadline = System.nanoTime() + IMAGE_STAGE_BUDGET_NANOS
        var handled = 0
        syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ENCLOSURE_IMAGES_DOWNLOAD) {
            for (chunk in enclosureImageEntries.chunked(syncMode.maxConcurrentArticleImages)) {
                if (System.nanoTime() > deadline) {
                    Log.i(TAG, "Image budget spent after $handled articles; the rest waits for the next run")
                    break
                }
                articleContentRepository.downloadEnclosureImages(chunk, syncMode)
                handled += chunk.size
            }
        }
        Log.i(TAG, "Enclosure images downloaded for $handled of ${enclosureImageEntries.size} articles")
    }
}

private fun PrefetchTarget.imageEnclosureUrls(downloadAllImages: Boolean): List<String> {
    val imageUrls = enclosures.filter { it.isImage }.map { it.url }
    return if (downloadAllImages) imageUrls else imageUrls.take(1)
}
