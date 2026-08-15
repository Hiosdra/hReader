package com.hiosdra.hreader.data.local.repository

import android.util.Log
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.model.ArticleContentSource
import com.hiosdra.hreader.data.model.ArticleText
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.util.leadImageUrl
import com.hiosdra.hreader.util.prepareArticleImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ArticleContentRepository(
    private val embeddedMediaLabel: () -> String,
    private val backend: FeedBackend,
    private val articleContentDao: ArticleContentDao,
    private val articleDao: ArticleDao,
    private val articleImageRepository: ArticleImageRepository,
    private val credibilityRepository: CredibilityRepository,
    private val articleAiOverviewRepository: ArticleAiOverviewRepository,
    private val articlePageRepository: ArticlePageRepository
) {
    companion object {
        private const val TAG = "ArticleContentRepo"

        /**
         * Background prefetch used to submit every unread article at once, and each of those also
         * downloads the images it references. On a large backlog that put thousands of requests in
         * flight at the same time.
         */
        private const val MAX_CONCURRENT_PREFETCH = 100

        /** Below SQLite's 999 bound-variable ceiling on Android. */
        private const val DELETE_CHUNK = 500
        private const val IMAGE_URL_SEPARATOR = "\u001e"
        private const val EMPTY_IMAGE_MANIFEST = "\u0000"
    }

    private val prefetchLimiter = Semaphore(MAX_CONCURRENT_PREFETCH)
    suspend fun getArticleContent(
        entryId: Long,
        url: String,
        allowNetwork: Boolean = true
    ): ArticleText {
        val localContent = articleContentDao.getArticleContent(entryId)
        if (localContent != null && localContent.url == url) {
            if (localContent.source == ArticleContentSource.FULL || !allowNetwork) {
                return prepareStoredContent(entryId, localContent, allowNetwork)
            }

            val fullContent = fetchFullContent(entryId, url)
            if (fullContent != null) {
                return storeContent(entryId, url, fullContent, ArticleContentSource.FULL, allowNetwork)
            }
            return prepareStoredContent(entryId, localContent, allowNetwork)
        }

        if (allowNetwork) {
            val fullContent = fetchFullContent(entryId, url)
            if (fullContent != null) {
                return storeContent(entryId, url, fullContent, ArticleContentSource.FULL, true)
            }
        }

        val feedContent = articleDao.getArticlesImmediate(listOf(entryId.toString()))
            .firstOrNull()
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No content available for entry $entryId")
        return storeContent(entryId, url, feedContent, ArticleContentSource.FEED_FALLBACK, allowNetwork)
    }

    private suspend fun fetchFullContent(entryId: Long, url: String): String? {
        return try {
            backend.fetchFullContent(entryId, url)?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backend could not provide full content for entry $entryId: ${e.message}")
            null
        }
    }

    private suspend fun prepareStoredContent(
        entryId: Long,
        stored: ArticleContent,
        allowNetwork: Boolean
    ): ArticleText {
        val storedImageUrls = stored.imageUrls.toImageUrls()
        val hasImageManifest = stored.imageUrls.isNotEmpty()
        val prepared = if (stored.isPrepared && hasImageManifest) {
            PreparedArticle(stored.content, storedImageUrls, stored.leadImageUrl)
        } else {
            prepare(entryId, stored.content, stored.url)
        }
        articleImageRepository.setExpectedImages(entryId, prepared.imageUrls)
        if (allowNetwork) downloadImagesForEntry(entryId, prepared.imageUrls)
        val updated = stored.copy(
            content = prepared.html,
            isPrepared = true,
            leadImageUrl = prepared.leadImageUrl,
            imageUrls = prepared.imageUrls.toImageManifest()
        )
        if (updated != stored) articleContentDao.insertArticleContent(updated)
        return ArticleText(prepared.html, prepared.leadImageUrl, stored.source)
    }

    private suspend fun storeContent(
        entryId: Long,
        url: String,
        sourceContent: String,
        source: ArticleContentSource,
        allowNetwork: Boolean
    ): ArticleText {
        val prepared = prepare(entryId, sourceContent, url)
        articleImageRepository.setExpectedImages(entryId, prepared.imageUrls)
        if (allowNetwork) downloadImagesForEntry(entryId, prepared.imageUrls)
        articleContentDao.insertArticleContent(
            ArticleContent(
                entryId = entryId,
                content = prepared.html,
                fetchedAt = Instant.now(),
                url = url,
                source = source,
                isPrepared = true,
                leadImageUrl = prepared.leadImageUrl,
                imageUrls = prepared.imageUrls.toImageManifest()
            )
        )
        if (source == ArticleContentSource.FULL) articleDao.setFullContent(entryId.toString(), sourceContent)
        return ArticleText(prepared.html, prepared.leadImageUrl, source)
    }

    /**
     * Everything the reader's side would otherwise work out each time the article is opened: the
     * body with its image addresses resolved, which of them to download, and the picture that leads
     * the article. One reading of the document answers all three.
     */
    private suspend fun prepare(entryId: Long, content: String, baseUri: String): PreparedArticle {
        val article = articleDao.getArticlesImmediate(listOf(entryId.toString())).firstOrNull()
        return withContext(Dispatchers.Default) {
            val images = prepareArticleImages(
                content,
                baseUri,
                embeddedMediaLabel()
            )
            PreparedArticle(
                html = images.html,
                imageUrls = images.imageUrls,
                leadImageUrl = leadImageUrl(
                    enclosureUrl = article?.enclosures?.firstOrNull { it.isImage }?.url,
                    feedContent = article?.content,
                    bodyImageUrls = images.imageUrls,
                    baseUri = baseUri
                )
            )
        }
    }

    private data class PreparedArticle(
        val html: String,
        val imageUrls: List<String>,
        val leadImageUrl: String?
    )

    /**
     * The entries whose text is not stored yet, in the order given. Prefetching a bounded slice of
     * a large backlog only makes progress if the slice is taken from what is actually outstanding.
     */
    suspend fun entriesMissingContent(entries: List<Pair<Long, String>>): List<Pair<Long, String>> {
        val full = articleContentDao.getContentEntryIds(ArticleContentSource.FULL).toHashSet()
        return entries.filterNot { (entryId, _) -> entryId in full }
    }

    /**
     * [onProgress] is called with the number of articles finished so far — successes and failures
     * alike, since what the reader waiting on "prepare for offline" wants to know is how much of
     * the queue is left, not how much of it worked.
     */
    suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int? = 50,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) = coroutineScope {
        val limitedEntries = if (limit != null) entries.take(limit) else entries
        val total = limitedEntries.size
        val done = AtomicInteger()
        val deferredResults = limitedEntries.map { (entryId, url) ->
            async(Dispatchers.IO) {
                prefetchLimiter.withPermit {
                    try {
                        val stored = articleContentDao.getArticleContent(entryId)
                        if (stored == null || stored.source != ArticleContentSource.FULL) {
                            getArticleContent(entryId, url)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to prefetch content for entry $entryId", e)
                    }
                }
                onProgress(done.incrementAndGet(), total)
            }
        }
        deferredResults.awaitAll()
    }

    suspend fun downloadEnclosureImages(entries: List<Pair<Long, List<String>>>) = coroutineScope {
        val deferredResults = entries.map { (entryId, imageUrls) ->
            async(Dispatchers.IO) {
                prefetchLimiter.withPermit {
                    downloadImagesForEntry(entryId, imageUrls)
                }
            }
        }
        deferredResults.awaitAll()
    }

    suspend fun cleanupOrphanedContent() {
        // No early return on an empty article set: retention and full-sync reconciliation both
        // delete articles now, so "no articles left" is precisely when everything stored here has
        // become an orphan.
        val currentEntryIds = articleDao.getAllIds().mapNotNull { it.toLongOrNull() }.toHashSet()
        credibilityRepository.cleanupOrphanedReports(currentEntryIds)
        articleAiOverviewRepository.cleanupOrphaned(currentEntryIds)

        // Only the ids: every row here holds a full article body, and reading all of them to
        // compare a number put the entire offline cache in memory inside a background worker.
        val orphanedContent = articleContentDao.getAllContentEntryIds()
            .filterNot { currentEntryIds.contains(it) }
        // Chunked: retention and full-sync reconciliation can orphan thousands of rows at once,
        // and one statement for all of them would exceed SQLite's bound-variable ceiling.
        orphanedContent.chunked(DELETE_CHUNK).forEach { chunk ->
            articleContentDao.deleteArticlesContent(chunk)
        }

        // Runs unconditionally: images outlive their content rows, and bailing out when no
        // articles are left is exactly when every stored image has become an orphan.
        articleImageRepository.cleanupOrphanedImages()
        articlePageRepository.cleanupOrphanedPages()
        // Also here, not only after each download: lowering the budget in settings has to shrink a
        // cache that is already over it, even when nothing new is being fetched.
        articleImageRepository.enforceCacheBudget()
    }

    private suspend fun downloadImagesForEntry(entryId: Long, imageUrls: List<String>) {
        imageUrls.forEach { imageUrl ->
            try {
                articleImageRepository.downloadAndStoreImage(entryId, imageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download image $imageUrl for entry $entryId", e)
            }
        }
    }

    private fun String.toImageUrls(): List<String> =
        takeUnless { it == EMPTY_IMAGE_MANIFEST }
            ?.split(IMAGE_URL_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun List<String>.toImageManifest(): String =
        joinToString(IMAGE_URL_SEPARATOR).ifEmpty { EMPTY_IMAGE_MANIFEST }
}
