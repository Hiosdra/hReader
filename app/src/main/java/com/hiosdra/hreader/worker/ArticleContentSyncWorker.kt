package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
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
            Log.d(TAG, "Cleaning up orphaned content before prefetch")
            syncPerformanceLogger.measureSyncTime("Orphaned content cleanup") {
                articleContentRepository.cleanupOrphanedContent()
            }
            Log.d(TAG, "Cleanup complete")

            Log.d(TAG, "Fetching local unread articles")
            val unreadArticles = articleRepository.getLocalUnreadArticles()
            Log.i(TAG, "Found ${unreadArticles.size} local unread articles")

            val entriesToFetch = unreadArticles.map { entry ->
                entry.id to entry.url
            }

            if (entriesToFetch.isNotEmpty()) {
                syncPerformanceLogger.logBatchInfo(entriesToFetch.size, entriesToFetch.size)
                Log.d(TAG, "Prefetching content for ${entriesToFetch.size} articles (background sync - no limit)")
                
                syncPerformanceLogger.measureSyncTime("Article content prefetch") {
                    articleContentRepository.prefetchArticleContent(entriesToFetch, limit = null)
                }
                Log.i(TAG, "Content prefetching completed")
            } else {
                Log.i(TAG, "No articles to prefetch")
            }

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            if (e is IOException) Result.retry() else Result.failure()
        }
    }
}
