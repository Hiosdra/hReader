package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMaintenanceDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.sync.SyncMode
import com.hiosdra.hreader.core.domain.model.OfflinePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

private data class ArticlePageComponents(
    val files: ArticlePageFileStore,
    val archiver: ArticlePageArchiver
)

private fun createArticlePageComponents(
    context: Context,
    httpClient: OkHttpClient,
    remoteResourcePolicy: RemoteResourcePolicyAdapter
): ArticlePageComponents {
    val files = ArticlePageFileStore(context)
    return ArticlePageComponents(
        files = files,
        archiver = ArticlePageArchiver(files, httpClient, remoteResourcePolicy)
    )
}

internal class ArticlePageRepository internal constructor(
    private val snapshotDao: ArticlePageSnapshotDao,
    private val articleMaintenanceDao: ArticleMaintenanceDao,
    private val files: ArticlePageFileStore,
    private val archiver: ArticlePageArchiver
) : ArticlePageStore {
    private constructor(
        snapshotDao: ArticlePageSnapshotDao,
        articleMaintenanceDao: ArticleMaintenanceDao,
        components: ArticlePageComponents
    ) : this(snapshotDao, articleMaintenanceDao, components.files, components.archiver)

    constructor(
        context: Context,
        snapshotDao: ArticlePageSnapshotDao,
        articleMaintenanceDao: ArticleMaintenanceDao,
        @Suppress("UNUSED_PARAMETER") articleRecordDao: ArticleRecordDao,
        httpClient: OkHttpClient,
        remoteResourcePolicy: RemoteResourcePolicyAdapter
    ) : this(
        snapshotDao = snapshotDao,
        articleMaintenanceDao = articleMaintenanceDao,
        components = createArticlePageComponents(context, httpClient, remoteResourcePolicy)
    )

    override suspend fun getOfflinePage(entryId: Long, originalUrl: String): OfflinePage? =
        withContext(Dispatchers.IO) {
            val snapshot = snapshotDao.get(entryId) ?: return@withContext null
            if (snapshot.originalUrl != originalUrl) return@withContext null

            val directory = files.pageDirectory(entryId, snapshot.directoryPath) ?: run {
                snapshotDao.deleteForEntries(listOf(entryId))
                return@withContext null
            }
            val htmlFile = directory.resolve(ArticlePageFiles.INDEX_FILE)
            if (!htmlFile.isFile) {
                snapshotDao.deleteForEntries(listOf(entryId))
                directory.deleteRecursively()
                return@withContext null
            }
            if (htmlFile.length() > ArticlePageFiles.MAX_HTML_BYTES) {
                snapshotDao.deleteForEntries(listOf(entryId))
                directory.deleteRecursively()
                return@withContext null
            }

            val html = runCatching { htmlFile.readText(Charsets.UTF_8) }.getOrNull()
                ?: return@withContext null
            OfflinePage(
                entryId = entryId,
                originalUrl = snapshot.originalUrl,
                baseUrl = ArticlePageFiles.baseUrl(entryId),
                html = html,
                resourceDirectory = directory.absolutePath,
                isComplete = snapshot.isComplete,
                finalUrl = snapshot.finalUrl,
                fetchedAt = snapshot.fetchedAt
            )
        }

    override suspend fun entriesMissingPages(entries: List<Pair<Long, String>>): List<Pair<Long, String>> =
        withContext(Dispatchers.IO) {
            val stored = snapshotDao.getAll()
                .filter { snapshot ->
                    snapshot.isComplete &&
                        files.pageDirectory(snapshot.entryId, snapshot.directoryPath)
                            ?.resolve(ArticlePageFiles.INDEX_FILE)
                            ?.isFile == true
                }
                .associate { it.entryId to it.originalUrl }
            entries.filterNot { (entryId, url) -> stored[entryId] == url }
        }

    override suspend fun getMissingPageTargets(limit: Int): List<Pair<Long, String>> =
        articleMaintenanceDao.getPrefetchTargetsMissingPages(limit).mapNotNull { target ->
            target.id.toLongOrNull()?.let { it to target.url }
        }

    override suspend fun countMissingPageTargets(): Int =
        articleMaintenanceDao.countPrefetchTargetsMissingPages()

    override suspend fun prefetchPages(
        entries: List<Pair<Long, String>>,
        limit: Int?,
        syncMode: SyncMode,
        onProgress: (done: Int, total: Int) -> Unit
    ) = coroutineScope {
        val selected = if (limit == null) entries else entries.take(limit)
        val total = selected.size
        val done = AtomicInteger()
        val pageLimiter = Semaphore(syncMode.maxConcurrentFullPages)
        for (batch in selected.chunked(syncMode.maxConcurrentFullPages)) {
            batch.map { (entryId, url) ->
                async(Dispatchers.IO) {
                    pageLimiter.withPermit {
                        try {
                            val archived = archiver.archive(
                                entryId = entryId,
                                originalUrl = url,
                                maxConcurrentResources = syncMode.maxConcurrentPageResources
                            )
                            if (archived != null) {
                                snapshotDao.insert(
                                    ArticlePageSnapshot(
                                        entryId = entryId,
                                        originalUrl = url,
                                        finalUrl = archived.finalUrl,
                                        directoryPath = archived.directoryPath,
                                        fetchedAt = Instant.now(),
                                        byteSize = archived.byteSize,
                                        isComplete = archived.isComplete
                                    )
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to archive page for entry $entryId", e)
                        }
                    }
                    onProgress(done.incrementAndGet(), total)
                }
            }.awaitAll()
        }
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        snapshotDao.clearAll()
        files.clearAll()
    }

    suspend fun prefetchPages(entries: List<Pair<Long, String>>): Unit =
        prefetchPages(entries, limit = null, onProgress = { _, _ -> })

    companion object {
        const val OFFLINE_PAGE_HOST = ArticlePageFiles.OFFLINE_PAGE_HOST

        fun baseUrl(entryId: Long): String = ArticlePageFiles.baseUrl(entryId)
        const val TAG = "ArticlePageRepository"
    }
}
