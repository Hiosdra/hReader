package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

internal class ArticleContentPrefetchService(
    private val contentService: ArticleContentService,
    private val articleContentDao: ArticleContentDao,
    private val imageDownloads: ArticleImageDownloadCoordinator
) {
    suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        downloadAllImages: Boolean,
        syncMode: SyncMode,
        onProgress: (done: Int, total: Int) -> Unit
    ) = coroutineScope {
        val limitedEntries = if (limit != null) entries.take(limit) else entries
        val total = limitedEntries.size
        val done = AtomicInteger()
        val prefetchLimiter = Semaphore(syncMode.maxConcurrentArticleContent)
        val imageLimiter = Semaphore(syncMode.maxConcurrentArticleImages)
        for (batch in limitedEntries.chunked(syncMode.maxConcurrentArticleContent)) {
            batch.map { (entryId, url) ->
                async(Dispatchers.IO) {
                    prefetchLimiter.withPermit {
                        try {
                            val stored = articleContentDao.getArticleContent(entryId)
                            if (downloadAllImages || stored == null || stored.source != ArticleContentSource.FULL) {
                                contentService.getArticleContent(
                                    entryId = entryId,
                                    url = url,
                                    allowNetwork = true,
                                    downloadAllImages = downloadAllImages,
                                    imageLimiter = imageLimiter
                                )
                                imageDownloads.await(entryId)
                                if (downloadAllImages) {
                                    contentService.markAllImagesPreparedIfComplete(entryId)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to prefetch content for entry $entryId", e)
                        }
                    }
                    onProgress(done.incrementAndGet(), total)
                }
            }.awaitAll()
        }
        Unit
    }

    suspend fun downloadEnclosureImages(
        entries: List<Pair<Long, List<String>>>,
        syncMode: SyncMode
    ) = withContext(Dispatchers.IO) {
        imageDownloads.downloadEnclosureImages(entries, syncMode)
    }

    private companion object {
        const val TAG = "ArticleContentPrefetch"
    }
}
