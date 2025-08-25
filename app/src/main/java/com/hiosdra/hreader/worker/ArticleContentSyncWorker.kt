package com.hiosdra.hreader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiosdra.hreader.data.local.repository.ArticleContentRepository
import com.hiosdra.hreader.data.local.repository.ArticleRepository
import com.hiosdra.hreader.data.model.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
        try {
            articleContentRepository.cleanupOrphanedContent()
            
            val unreadArticles = articleRepository.getLocalUnreadArticles()
            if (unreadArticles.isEmpty()) {
                return@withContext Result.success()
            }

            val entriesToFetch = unreadArticles.map { entry ->
                entry.id to entry.url
            }

            articleContentRepository.prefetchArticleContent(entriesToFetch, limit = null)

            // Download enclosure images for articles with image enclosures
            val enclosureImageEntries = unreadArticles.mapNotNull { entry ->
                entry.getImageEnclosureUrls()?.let { urls ->
                    if (urls.isNotEmpty()) entry.id.toLong() to urls else null
                }
            }

            if (enclosureImageEntries.isNotEmpty()) {
                articleContentRepository.downloadEnclosureImages(enclosureImageEntries)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ArticleContentSyncWorker failed: ${e.message}", e)
            if (e is IOException) Result.retry() else Result.failure()
        }
    }
}

private fun Entry.getImageEnclosureUrls(): List<String>? =
    enclosures?.filter { it.mimeType?.startsWith("image/") == true }?.map { it.url }
