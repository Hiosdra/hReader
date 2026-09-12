package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.core.application.exception.StaleSyncSessionException
import com.hiosdra.hreader.core.application.content.articlePreviewHtml
import com.hiosdra.hreader.core.application.content.ArticleHtmlTransformer
import com.hiosdra.hreader.core.application.content.leadImageUrl
import com.hiosdra.hreader.core.application.content.prepareArticleImages
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.SyncSession
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.ArticleText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArticleContentRepository(
    private val embeddedMediaLabel: () -> String,
    private val backend: FeedBackend,
    private val articleContentDao: ArticleContentDao,
    private val articleDao: ArticleDao,
    private val articleImageStore: ArticleImageStore,
    private val credibilityStore: CredibilityStore,
    private val articleAiOverviewStore: ArticleAiOverviewStore,
    private val articlePageStore: ArticlePageStore,
    private val imageScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) : ArticleContentStore {
    companion object {
        private const val TAG = "ArticleContentRepo"

        /**
         * Background prefetch used to submit every unread article at once, and each of those also
         * downloads the images it references. On a large backlog that put thousands of requests in
         * flight at the same time.
         */
        private const val MAX_CONCURRENT_PREFETCH = 8
        private const val PREFETCH_BATCH_SIZE = 32

        /** Below SQLite's 999 bound-variable ceiling on Android. */
        private const val DELETE_CHUNK = 500
        private const val IMAGE_URL_SEPARATOR = "\u001e"
        private const val EMPTY_IMAGE_MANIFEST = "\u0000"
    }

    private val prefetchLimiter = Semaphore(MAX_CONCURRENT_PREFETCH)
    private val imageJobsMutex = Mutex()
    private val imageJobs = mutableMapOf<Long, Job>()
    override suspend fun getArticleContent(
        entryId: Long,
        url: String,
        allowNetwork: Boolean,
        session: SyncSession?
    ): ArticleText {
        val activeSession = session ?: sessionGate.currentSession()
        checkSession(activeSession)
        return getArticleContent(
            entryId = entryId,
            url = url,
            allowNetwork = allowNetwork,
            downloadAllImages = true,
            session = activeSession
        )
    }

    private suspend fun getArticleContent(
        entryId: Long,
        url: String,
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        session: SyncSession
    ): ArticleText {
        val localContent = articleContentDao.getArticleContent(entryId)
        if (localContent != null && localContent.url == url && localContent.content.isNotBlank()) {
            if (localContent.source == ArticleContentSource.FULL) {
                return prepareStoredContent(entryId, localContent, allowNetwork, downloadAllImages, session)
            }

            if (allowNetwork) {
                val fullContent = fetchFullContent(entryId, url, session)
                if (fullContent != null) {
                    return storeContent(
                        entryId,
                        url,
                        fullContent,
                        ArticleContentSource.FULL,
                        true,
                        downloadAllImages,
                        session
                    )
                }
            }

            val cachedContent = getCachedArticleContent(entryId)
            if (cachedContent?.source == ArticleContentSource.FULL) {
                return storeContent(
                    entryId,
                    url,
                    cachedContent.content,
                    cachedContent.source,
                    allowNetwork,
                    downloadAllImages,
                    session
                )
            }
            return prepareStoredContent(entryId, localContent, allowNetwork, downloadAllImages, session)
        }

        if (allowNetwork) {
            val fullContent = fetchFullContent(entryId, url, session)
            if (fullContent != null) {
                return storeContent(
                    entryId,
                    url,
                    fullContent,
                    ArticleContentSource.FULL,
                    true,
                    downloadAllImages,
                    session
                )
            }
        }

        val cachedContent = getCachedArticleContent(entryId)
            ?: throw IllegalStateException("No content available for entry $entryId")
        return storeContent(
            entryId,
            url,
            cachedContent.content,
            cachedContent.source,
            allowNetwork,
            downloadAllImages,
            session
        )
    }

    private suspend fun fetchFullContent(
        entryId: Long,
        url: String,
        session: SyncSession
    ): String? {
        return try {
            sessionGate.withSession(session) {
                backend.fetchFullContent(entryId, url)?.takeIf { it.isNotBlank() }
            }
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
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        session: SyncSession
    ): ArticleText {
        val storedImageUrls = stored.imageUrls.toImageUrls()
        val hasImageManifest = stored.imageUrls.isNotEmpty()
        val prepared = if (stored.isPrepared && hasImageManifest) {
            val articleTitle = articleDao.getArticlesImmediate(listOf(entryId.toString()))
                .firstOrNull()
                ?.title
                .orEmpty()
            val normalizedContent = withContext(Dispatchers.Default) {
                ArticleHtmlTransformer.transform(
                    html = stored.content,
                    baseUrl = stored.url,
                    articleTitle = articleTitle,
                    embeddedMediaLabel = embeddedMediaLabel()
                )
            }
            PreparedArticle(normalizedContent, storedImageUrls, stored.leadImageUrl)
        } else {
            prepare(entryId, stored.content, stored.url)
        }
        val updated = stored.copy(
            content = prepared.html,
            isPrepared = true,
            leadImageUrl = prepared.leadImageUrl,
            imageUrls = prepared.imageUrls.toImageManifest()
        )
        sessionGate.withSession(session) {
            articleImageStore.setExpectedImages(entryId, prepared.expectedImageUrls(downloadAllImages))
            if (updated != stored) articleContentDao.insertArticleContent(updated)
        }
        if (allowNetwork) {
            scheduleImageDownloads(
                entryId,
                prepared.imageUrls,
                prepared.leadImageUrl,
                downloadAllImages,
                session
            )
        }
        return ArticleText(prepared.html, prepared.leadImageUrl, stored.source)
    }

    private suspend fun storeContent(
        entryId: Long,
        url: String,
        sourceContent: String,
        source: ArticleContentSource,
        allowNetwork: Boolean,
        downloadAllImages: Boolean,
        session: SyncSession
    ): ArticleText {
        val prepared = prepare(entryId, sourceContent, url)
        sessionGate.withSession(session) {
            credibilityStore.invalidateForEntries(listOf(entryId), session)
            articleImageStore.setExpectedImages(entryId, prepared.expectedImageUrls(downloadAllImages))
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
        }
        if (allowNetwork) {
            scheduleImageDownloads(
                entryId,
                prepared.imageUrls,
                prepared.leadImageUrl,
                downloadAllImages,
                session
            )
        }
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
                embeddedMediaLabel(),
                article?.title.orEmpty()
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

    private data class CachedArticleContent(
        val content: String,
        val source: ArticleContentSource
    )

    private suspend fun getCachedArticleContent(entryId: Long): CachedArticleContent? {
        val article = articleDao.getArticlesImmediate(listOf(entryId.toString())).firstOrNull() ?: return null
        article.fullContent?.takeIf { it.isNotBlank() }?.let {
            return CachedArticleContent(it, ArticleContentSource.FULL)
        }
        article.content?.takeIf { it.isNotBlank() }?.let {
            return CachedArticleContent(it, ArticleContentSource.FEED_FALLBACK)
        }
        articlePreviewHtml(article.preview)?.let {
            return CachedArticleContent(it, ArticleContentSource.FEED_FALLBACK)
        }
        return null
    }

    /**
     * The entries whose text is not stored yet, in the order given. Prefetching a bounded slice of
     * a large backlog only makes progress if the slice is taken from what is actually outstanding.
     */
    override suspend fun entriesMissingContent(
        entries: List<Pair<Long, String>>,
        session: SyncSession?
    ): List<Pair<Long, String>> {
        if (entries.isEmpty()) return emptyList()
        val activeSession = session ?: sessionGate.currentSession()
        return sessionGate.withSession(activeSession) {
            val full = entries
                .map { it.first }
                .chunked(DELETE_CHUNK)
                .flatMap { chunk ->
                    articleContentDao.getContentEntryIds(chunk, ArticleContentSource.FULL)
                }
                .toHashSet()
            entries.filterNot { (entryId, _) -> entryId in full }
        }
    }

    override suspend fun entriesMissingFullOfflinePreparation(
        entries: List<Pair<Long, String>>,
        session: SyncSession?
    ): List<Pair<Long, String>> {
        if (entries.isEmpty()) return emptyList()
        val activeSession = session ?: sessionGate.currentSession()
        return sessionGate.withSession(activeSession) {
            val prepared = entries
                .chunked(DELETE_CHUNK)
                .flatMap { chunk ->
                    articleContentDao.getFullyImagePreparedEntryIds(
                        chunk.map { it.first },
                        ArticleContentSource.FULL
                    )
                }
                .toHashSet()
            entries.filterNot { (entryId, _) -> entryId in prepared }
        }
    }

    /**
     * [onProgress] is called with the number of articles finished so far — successes and failures
     * alike, since what the reader waiting on "prepare for offline" wants to know is how much of
     * the queue is left, not how much of it worked.
     */
    override suspend fun prefetchArticleContent(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        downloadAllImages: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
        session: SyncSession?
    ) = coroutineScope {
        val activeSession = session ?: sessionGate.currentSession()
        checkSession(activeSession)
        val limitedEntries = if (limit != null) entries.take(limit) else entries
        val total = limitedEntries.size
        val done = AtomicInteger()
        for (batch in limitedEntries.chunked(PREFETCH_BATCH_SIZE)) {
            val deferredResults = batch.map { (entryId, url) ->
                async(Dispatchers.IO) {
                    prefetchLimiter.withPermit {
                        try {
                            checkSession(activeSession)
                            val stored = articleContentDao.getArticleContent(entryId)
                            if (downloadAllImages || stored == null || stored.source != ArticleContentSource.FULL) {
                                getArticleContent(
                                    entryId = entryId,
                                    url = url,
                                    allowNetwork = true,
                                    downloadAllImages = downloadAllImages,
                                    session = activeSession
                                )
                                awaitImageDownloads(entryId)
                                if (downloadAllImages) markAllImagesPreparedIfComplete(entryId, activeSession)
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
        Unit
    }

    override suspend fun downloadEnclosureImages(
        entries: List<Pair<Long, List<String>>>,
        session: SyncSession?
    ) = coroutineScope {
        val activeSession = session ?: sessionGate.currentSession()
        checkSession(activeSession)
        val deferredResults = entries.map { (entryId, imageUrls) ->
            async(Dispatchers.IO) {
                sessionGate.withSession(activeSession) {
                    prefetchLimiter.withPermit {
                        downloadImagesForEntry(entryId, imageUrls)
                    }
                }
            }
        }
        deferredResults.awaitAll()
        Unit
    }

    override suspend fun cleanupOrphanedContent() {
        val session = sessionGate.currentSession()
        sessionGate.withSession(session) {
            credibilityStore.cleanupOrphanedReports(session)
            articleAiOverviewStore.cleanupOrphaned(session)

            while (true) {
                val orphaned = articleContentDao.getOrphanedEntryIds(DELETE_CHUNK)
                if (orphaned.isEmpty()) break
                articleContentDao.deleteArticlesContent(orphaned)
            }

            articleImageStore.cleanupOrphanedImages()
            articlePageStore.cleanupOrphanedPages()
            articleImageStore.enforceCacheBudget()
        }
    }

    suspend fun getArticleContent(entryId: Long, url: String): ArticleText =
        getArticleContent(entryId, url, allowNetwork = true)

    suspend fun prefetchArticleContent(entries: List<Pair<Long, String>>): Unit =
        prefetchArticleContent(entries, limit = 50, onProgress = { _, _ -> })

    private suspend fun downloadImagesForEntry(entryId: Long, imageUrls: List<String>) {
        imageUrls.forEach { imageUrl ->
            try {
                articleImageStore.downloadAndStoreImage(entryId, imageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to download image $imageUrl for entry $entryId", e)
                }
            }
        }
    }

    private suspend fun scheduleImageDownloads(
        entryId: Long,
        imageUrls: List<String>,
        leadImageUrl: String?,
        downloadAllImages: Boolean,
        session: SyncSession
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
                sessionGate.withSession(session) {
                    downloadImagesForEntry(entryId, urls)
                }
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

    private suspend fun awaitImageDownloads(entryId: Long) {
        val job = imageJobsMutex.withLock { imageJobs[entryId] }
        job?.join()
    }

    private suspend fun markAllImagesPreparedIfComplete(entryId: Long, session: SyncSession) {
        sessionGate.withSession(session) {
            val content = articleContentDao.getArticleContent(entryId) ?: return@withSession
            val expectedUrls = (content.imageUrls.toImageUrls() + listOfNotNull(content.leadImageUrl)).distinct()
            val storedUrls = articleImageStore.getLocalImagePaths(entryId).keys
            if (expectedUrls.all { it in storedUrls }) {
                articleContentDao.markAllImagesPrepared(entryId)
            }
        }
    }

    private fun checkSession(session: SyncSession) {
        if (!sessionGate.isCurrent(session)) {
            throw StaleSyncSessionException()
        }
    }

    private fun String.toImageUrls(): List<String> =
        takeUnless { it == EMPTY_IMAGE_MANIFEST }
            ?.split(IMAGE_URL_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun List<String>.toImageManifest(): String =
        joinToString(IMAGE_URL_SEPARATOR).ifEmpty { EMPTY_IMAGE_MANIFEST }

    private fun PreparedArticle.expectedImageUrls(downloadAllImages: Boolean): List<String> =
        if (downloadAllImages) {
            (listOfNotNull(leadImageUrl) + imageUrls).distinct()
        } else {
            listOfNotNull(leadImageUrl)
        }
}
