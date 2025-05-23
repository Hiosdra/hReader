package com.hiosdra.hreader.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.repository.ArticleContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ArticleContentSyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting ArticleContentSyncWorker")
        try {
            Log.d(TAG, "Fetching unread articles")
            val unreadArticles = articleRepository.getUnreadArticles()
            Log.i(TAG, "Found ${unreadArticles.size} unread articles")

            val entriesToFetch = unreadArticles.map { entry ->
                entry.id to entry.url
            }

            if (entriesToFetch.isNotEmpty()) {
                Log.d(TAG, "Prefetching content for ${entriesToFetch.size} articles")
                articleContentRepository.prefetchArticleContent(entriesToFetch)
                Log.i(TAG, "Content prefetching completed")
            } else {
                Log.i(TAG, "No articles to prefetch")
            }

            Log.d(TAG, "Starting cleanup of orphaned content")
            articleContentRepository.cleanupOrphanedContent()
            Log.i(TAG, "Content cleanup completed")

            Log.i(TAG, "ArticleContentSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
