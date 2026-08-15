package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hiosdra.hreader.R
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.notification.AppNotificationFactory
import com.hiosdra.hreader.util.ErrorReportingManager
import com.hiosdra.hreader.util.SyncPerformanceLogger
import com.hiosdra.hreader.util.SyncPerformanceOperation
import com.hiosdra.hreader.util.isWithinQuietHours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_RUN_ATTEMPTS = 5

/** How often stored progress is published. Per article it would be a write per download. */
private const val PROGRESS_REPORT_INTERVAL_MILLIS = 500L

/**
 * How many article bodies one run downloads.
 *
 * WorkManager stops a worker after ten minutes. Submitting the whole backlog meant a large cache
 * never reached the end of the queue, so the enclosure images that follow it never ran at all —
 * every run was killed partway through the same first stage. A bounded run finishes, and what is
 * left over is picked up by the next one, because articles already stored are skipped.
 */
private const val MAX_ARTICLES_PER_RUN = 500

/** How long the image stage may run before it hands the rest of the window to article bodies. */
private val IMAGE_STAGE_BUDGET_NANOS = TimeUnit.MINUTES.toNanos(3)

/** How often the image stage looks at the clock. Per article it would be a syscall per skip. */
private const val IMAGE_CHUNK = 50

class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger,
    private val preferencesManager: PreferencesManager,
    private val errorReportingManager: ErrorReportingManager
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
            if (inputData.getBoolean(KEY_USER_VISIBLE, false)) updateForeground()
            performOrphanedContentCleanup()
            backfillPreviews()

            val targets = articleRepository.getPrefetchTargets()
            Log.i(TAG, "Found ${targets.size} local unread articles")

            if (targets.isEmpty()) {
                Log.i(TAG, "No articles to prefetch")
                return@withContext Result.success()
            }

            // Images first. They are what the list and the opened article show, they are small, and
            // behind an unbounded article-text stage they never ran at all.
            downloadEnclosureImages(targets)
            val remaining = prefetchArticleContent(targets)

            // Only when the reader asked for the whole cache. A background run leaves the rest to
            // the next sync rather than spending backoff and radio time chasing a backlog nobody
            // is waiting on — and what it stored is kept either way, so the next run starts from
            // where this one stopped.
            if (remaining > 0 && shouldDrainRemaining() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                Log.i(TAG, "$remaining articles still without text; asking for another run")
                return@withContext Result.retry()
            }
            if (remaining > 0) Log.i(TAG, "$remaining articles still without text; left to the next sync")

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            val shouldRetry = e.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS
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
            hour = LocalTime.now().hour,
            startHour = preferencesManager.getQuietHoursStartHour(),
            endHour = preferencesManager.getQuietHoursEndHour()
        )
    }

    private suspend fun performOrphanedContentCleanup() {
            syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ORPHANED_CONTENT_CLEANUP) {
            articleContentRepository.cleanupOrphanedContent()
        }
    }

    /**
     * Articles cached before previews were stored have none, and deriving one needs an HTML parser
     * rather than SQL. Doing it here rather than in the migration keeps the upgrade instant and
     * spreads the work across the syncs that follow it.
     */
    private suspend fun backfillPreviews() {
        try {
            articleRepository.backfillMissingPreviews()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Preview backfill failed: ${e.message}")
        }
    }

    /**
     * Progress is published on a timer rather than per article: the prefetch runs a hundred
     * downloads at a time, and a WorkManager write per completion would cost more than the work.
     *
     * Returns how many articles are still without stored text once this run's slice is done.
     */
    private suspend fun prefetchArticleContent(targets: List<PrefetchTarget>): Int = coroutineScope {
        val outstanding = articleContentRepository.entriesMissingContent(targets.map { it.id.toLong() to it.url })
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
        if (!setForegroundIfAllowed { setForeground(getForegroundInfo()) }) {
            foregroundUnavailable = true
            Log.w(TAG, "Foreground notification unavailable; continuing without it")
        }
    }

    /**
     * Bounded by the clock rather than by a count. Images already on disk are skipped in a single
     * indexed read, so each run reaches further into the queue than the last — a fixed count would
     * spend its whole allowance re-skipping the same first articles and never move.
     *
     * The budget is what keeps this stage from consuming the run: it goes first so that it is not
     * starved, and stops in time to leave the article bodies their share.
     */
    private suspend fun downloadEnclosureImages(targets: List<PrefetchTarget>) {
        val enclosureImageEntries = targets.mapNotNull { target ->
            target.imageEnclosureUrls().takeIf { it.isNotEmpty() }?.let { urls ->
                target.id.toLong() to urls
            }
        }
        if (enclosureImageEntries.isEmpty()) return

        val deadline = System.nanoTime() + IMAGE_STAGE_BUDGET_NANOS
        var handled = 0
        syncPerformanceLogger.measureSyncTime(SyncPerformanceOperation.ENCLOSURE_IMAGES_DOWNLOAD) {
            for (chunk in enclosureImageEntries.chunked(IMAGE_CHUNK)) {
                if (System.nanoTime() > deadline) {
                    Log.i(TAG, "Image budget spent after $handled articles; the rest waits for the next run")
                    break
                }
                articleContentRepository.downloadEnclosureImages(chunk)
                handled += chunk.size
            }
        }
        Log.i(TAG, "Enclosure images downloaded for $handled of ${enclosureImageEntries.size} articles")
    }
}

private fun PrefetchTarget.imageEnclosureUrls(): List<String> =
    enclosures.filter { it.isImage }.map { it.url }
