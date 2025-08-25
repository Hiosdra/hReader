package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
            
            val unreadArticles = articleRepository.getLocalUnreadArticles()
            Log.i(TAG, "Found ${unreadArticles.size} local unread articles")
            
            if (unreadArticles.isEmpty()) {
                Log.i(TAG, "No articles to prefetch")
                return@withContext Result.success()
            }

            prefetchArticleContent(unreadArticles)
            downloadEnclosureImages(unreadArticles)

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            if (e is IOException) Result.retry() else Result.failure()
        }
    }

    private suspend fun performOrphanedContentCleanup() {
        syncPerformanceLogger.measureSyncTime("Orphaned content cleanup") {
            articleContentRepository.cleanupOrphanedContent()
        }
    }

    private suspend fun prefetchArticleContent(unreadArticles: List<Entry>) {
        val entriesToFetch = unreadArticles.map { it.id to it.url }
        syncPerformanceLogger.logBatchInfo(entriesToFetch.size, entriesToFetch.size)
        Log.d(TAG, "Prefetching content for ${entriesToFetch.size} articles (background sync)")
        
        syncPerformanceLogger.measureSyncTime("Article content prefetch") {
            articleContentRepository.prefetchArticleContent(entriesToFetch, limit = null)
        }
    }

    private suspend fun downloadEnclosureImages(unreadArticles: List<Entry>) {
        val enclosureImageEntries = unreadArticles.mapNotNull { entry ->
            entry.getImageEnclosureUrls()?.takeIf { it.isNotEmpty() }?.let { urls ->
                entry.id.toLong() to urls
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

private fun Entry.getImageEnclosureUrls(): List<String>? =
    enclosures?.filter { it.mimeType?.startsWith("image/") == true }?.map { it.url }
