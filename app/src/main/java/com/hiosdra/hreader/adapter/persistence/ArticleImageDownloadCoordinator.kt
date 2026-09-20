package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.sync.SyncMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

internal class ArticleImageDownloadCoordinator(
    private val imageStore: ArticleImageStore,
    private val imageScope: CoroutineScope
) {
    private val imageJobsMutex = Mutex()
    private val imageJobs = mutableMapOf<Long, Job>()

    suspend fun schedule(
        entryId: Long,
        imageUrls: List<String>,
        leadImageUrl: String?,
        downloadAllImages: Boolean,
        imageLimiter: Semaphore?
    ) {
        val urls = if (downloadAllImages) {
            (listOfNotNull(leadImageUrl) + imageUrls).distinct()
        } else {
            listOfNotNull(leadImageUrl).distinct()
        }
        if (urls.isEmpty()) return
        imageJobsMutex.withLock {
            if (imageJobs[entryId]?.isActive == true) return@withLock
            val job = imageScope.launch(start = CoroutineStart.UNDISPATCHED) {
                downloadImagesForEntry(entryId, urls, imageLimiter)
            }
            imageJobs[entryId] = job
            job.invokeOnCompletion {
                imageScope.launch {
                    imageJobsMutex.withLock {
                        if (imageJobs[entryId] === job) imageJobs.remove(entryId)
                    }
                }
            }
        }
    }

    suspend fun await(entryId: Long) {
        imageJobsMutex.withLock { imageJobs[entryId] }?.join()
    }

    suspend fun downloadEnclosureImages(
        entries: List<Pair<Long, List<String>>>,
        syncMode: SyncMode
    ) = coroutineScope {
        val imageLimiter = Semaphore(syncMode.maxConcurrentArticleImages)
        for (batch in entries.chunked(syncMode.maxConcurrentArticleImages)) {
            batch.map { (entryId, imageUrls) ->
                async {
                    downloadImagesForEntry(entryId, imageUrls, imageLimiter)
                }
            }.awaitAll()
        }
        Unit
    }

    private suspend fun downloadImagesForEntry(
        entryId: Long,
        imageUrls: List<String>,
        imageLimiter: Semaphore? = null
    ) {
        imageUrls.forEach { imageUrl ->
            try {
                if (imageLimiter == null) {
                    imageStore.downloadAndStoreImage(entryId, imageUrl)
                } else {
                    imageLimiter.withPermit {
                        imageStore.downloadAndStoreImage(entryId, imageUrl)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to download image $imageUrl for entry $entryId", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ArticleImageDownloadCoordinator"
    }
}
