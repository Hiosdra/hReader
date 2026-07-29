package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.util.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_RUN_ATTEMPTS = 5

/** How often stored progress is published. Per article it would be a write per download. */
private const val PROGRESS_REPORT_INTERVAL_MILLIS = 500L

class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ArticleContentSyncWorker"
    }

    private val done = AtomicInteger()
    private val total = AtomicInteger()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Downloading bodies and images is where the bandwidth and the radio time actually go, so
        // quiet hours have to cover it. Silencing only the article sync still left the prefetch
        // chained behind it running through the night.
        if (isSilenced()) {
            Log.i(TAG, "Inside quiet hours; skipping the prefetch")
            return@withContext Result.success()
        }

        Log.i(TAG, "Starting ArticleContentSyncWorker")
        try {
            performOrphanedContentCleanup()
            backfillPreviews()

            val targets = articleRepository.getPrefetchTargets()
            Log.i(TAG, "Found ${targets.size} local unread articles")

            if (targets.isEmpty()) {
                Log.i(TAG, "No articles to prefetch")
                return@withContext Result.success()
            }

            prefetchArticleContent(targets)
            downloadEnclosureImages(targets)

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            if (e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
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

    private suspend fun performOrphanedContentCleanup() {
        syncPerformanceLogger.measureSyncTime("Orphaned content cleanup") {
            articleContentRepository.cleanupOrphanedContent()
        }
    }

    /**
     * Articles cached before previews were stored have none, and deriving one needs an HTML parser
     * rather than SQL. Doing it here rather than in the migration keeps the upgrade instant and
     * spreads the work across the syncs that follow it.
     */
    private suspend fun backfillPreviews() {
        runCatching { articleRepository.backfillMissingPreviews() }
            .onFailure { Log.w(TAG, "Preview backfill failed: ${it.message}") }
    }

    /**
     * Progress is published on a timer rather than per article: the prefetch runs a hundred
     * downloads at a time, and a WorkManager write per completion would cost more than the work.
     */
    private suspend fun prefetchArticleContent(targets: List<PrefetchTarget>) = coroutineScope {
        val entriesToFetch = targets.map { it.id.toLong() to it.url }
        syncPerformanceLogger.logBatchInfo(entriesToFetch.size, entriesToFetch.size)
        Log.d(TAG, "Prefetching content for ${entriesToFetch.size} articles (background sync)")

        total.set(entriesToFetch.size)
        val reporter = launch {
            while (isActive) {
                publishProgress()
                delay(PROGRESS_REPORT_INTERVAL_MILLIS)
            }
        }
        try {
            syncPerformanceLogger.measureSyncTime("Article content prefetch") {
                articleContentRepository.prefetchArticleContent(
                    entries = entriesToFetch,
                    limit = null,
                    onProgress = { completed, _ -> done.set(completed) }
                )
            }
        } finally {
            reporter.cancel()
        }
        publishProgress()
    }

    private suspend fun publishProgress() {
        setProgress(
            workDataOf(
                KEY_PROGRESS_DONE to done.get(),
                KEY_PROGRESS_TOTAL to total.get()
            )
        )
    }

    private suspend fun downloadEnclosureImages(targets: List<PrefetchTarget>) {
        val enclosureImageEntries = targets.mapNotNull { target ->
            target.imageEnclosureUrls().takeIf { it.isNotEmpty() }?.let { urls ->
                target.id.toLong() to urls
            }
        }

        if (enclosureImageEntries.isNotEmpty()) {
            syncPerformanceLogger.measureSyncTime("Enclosure images download") {
                articleContentRepository.downloadEnclosureImages(enclosureImageEntries)
            }
            Log.i(TAG, "Enclosure images downloaded for ${enclosureImageEntries.size} articles")
        }
    }
}

private fun PrefetchTarget.imageEnclosureUrls(): List<String> =
    enclosures.filter { it.isImage }.map { it.url }
