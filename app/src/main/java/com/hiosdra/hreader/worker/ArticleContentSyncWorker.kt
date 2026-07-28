package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.remote.isRetryable
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_RUN_ATTEMPTS = 5

class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository,
    private val syncPerformanceLogger: SyncPerformanceLogger
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ArticleContentSyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting ArticleContentSyncWorker")
        try {
            performOrphanedContentCleanup()
            
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

    private suspend fun performOrphanedContentCleanup() {
        syncPerformanceLogger.measureSyncTime("Orphaned content cleanup") {
            articleContentRepository.cleanupOrphanedContent()
        }
    }

    private suspend fun prefetchArticleContent(targets: List<PrefetchTarget>) {
        val entriesToFetch = targets.map { it.id.toLong() to it.url }
        syncPerformanceLogger.logBatchInfo(entriesToFetch.size, entriesToFetch.size)
        Log.d(TAG, "Prefetching content for ${entriesToFetch.size} articles (background sync)")

        syncPerformanceLogger.measureSyncTime("Article content prefetch") {
            articleContentRepository.prefetchArticleContent(entriesToFetch, limit = null)
        }
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
