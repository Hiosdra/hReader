package com.hiosdra.hreader.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.repository.ArticleContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that fetches original article content for offline reading.
 * This worker is responsible for downloading full content of unread articles
 * so they can be read offline later.
 */
class ArticleContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val articleRepository: ArticleRepository,
    private val articleContentRepository: ArticleContentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get unread articles that don't have their content downloaded yet
            val unreadArticles = articleRepository.getUnreadArticles()

            // Format the entries for prefetching
            val entriesToFetch = unreadArticles.map { entry ->
                entry.id to entry.url
            }

            // Prefetch content for these articles
            if (entriesToFetch.isNotEmpty()) {
                articleContentRepository.prefetchArticleContent(entriesToFetch)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retry on failure
            Result.retry()
        }
    }
}
