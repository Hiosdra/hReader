package com.hiosdra.hreader.adapter.storage

import android.content.Context
import android.os.StatFs
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ArticleTtsPlayer
import com.hiosdra.hreader.core.application.port.out.GemmaModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.GemmaModelGateway
import com.hiosdra.hreader.core.application.port.out.GemmaModelLifecycle
import com.hiosdra.hreader.core.application.port.out.StorageStore
import com.hiosdra.hreader.core.application.port.out.TtsModelDownloadRequester
import com.hiosdra.hreader.core.application.port.out.TtsModelGateway
import com.hiosdra.hreader.core.application.storage.StorageCategory
import com.hiosdra.hreader.core.application.storage.StorageCategoryUsage
import com.hiosdra.hreader.core.application.storage.StorageCleanupAction
import com.hiosdra.hreader.core.application.storage.StorageCleanupProgress
import com.hiosdra.hreader.core.application.storage.StorageCleanupResult
import com.hiosdra.hreader.core.application.storage.StorageSnapshot
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsModelCatalog
import com.hiosdra.hreader.core.application.tts.TtsModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class StorageRepository(
    context: Context,
    private val database: AppDatabase,
    private val images: ArticleImageStore,
    private val pages: ArticlePageStore,
    private val ttsModels: TtsModelGateway,
    private val ttsDownloads: TtsModelDownloadRequester,
    private val gemmaModel: GemmaModelGateway,
    private val gemmaDownloads: GemmaModelDownloadRequester,
    private val gemmaLifecycle: GemmaModelLifecycle,
    private val ttsPlayer: ArticleTtsPlayer
) : StorageStore {
    private val appContext = context.applicationContext
    private val cleanupMutex = Mutex()
    private val databaseName = "hreader-db"
    private val imagesDirectory = File(appContext.filesDir, "article_images")
    private val pagesDirectory = File(appContext.filesDir, "article_pages")
    private val ttsDirectory = File(appContext.filesDir, "tts_models")
    private val aiDirectory = File(appContext.filesDir, "ai_models")
    private val webViewCacheDirectories = listOf(
        File(appContext.dataDir, "app_webview/Default/Cache"),
        File(appContext.dataDir, "app_webview/Default/GPUCache"),
        File(appContext.dataDir, "app_webview/Default/Code Cache")
    )

    override suspend fun inspect(): StorageSnapshot = withContext(Dispatchers.IO) {
        inspectOnIo()
    }

    override suspend fun cleanup(
        action: StorageCleanupAction,
        onProgress: suspend (StorageCleanupProgress) -> Unit
    ): StorageCleanupResult = cleanupMutex.withLock {
        withContext(Dispatchers.IO) {
            val before = inspectOnIo()
            val beforeBytes = before.usage(action).bytes
            val totalItems = cleanupItemCount(action, before).coerceAtLeast(
                if (beforeBytes > 0L) 1 else 0
            )
            onProgress(StorageCleanupProgress(action, 0, totalItems))

            var failedItems = 0
            try {
                failedItems = when (action) {
                    StorageCleanupAction.DOWNLOADED_IMAGES -> {
                        images.clearAll()
                        0
                    }
                    StorageCleanupAction.OFFLINE_PAGES -> {
                        pages.clearAll()
                        0
                    }
                    StorageCleanupAction.TEMPORARY_CACHE -> clearTemporaryCache()
                    StorageCleanupAction.TTS_MODELS -> clearTtsModels()
                    StorageCleanupAction.ON_DEVICE_AI_MODEL -> clearAiModel()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failedItems = totalItems.coerceAtLeast(1)
            }

            val after = inspectOnIo()
            val afterUsage = after.usage(action)
            val remaining = afterUsage.bytes
            if (remaining > 0L || afterUsage.itemCount > 0) {
                failedItems = failedItems.coerceAtLeast(1)
            }
            failedItems = failedItems.coerceIn(0, totalItems)
            onProgress(
                StorageCleanupProgress(
                    action = action,
                    completedItems = (totalItems - failedItems).coerceAtLeast(0),
                    totalItems = totalItems
                )
            )
            StorageCleanupResult(
                action = action,
                reclaimedBytes = (beforeBytes - remaining).coerceAtLeast(0L),
                totalItems = totalItems,
                failedItems = failedItems
            )
        }
    }

    private suspend fun clearTtsModels(): Int {
        withContext(Dispatchers.Main.immediate) { ttsPlayer.stop() }
        val downloadedModels = TtsModelCatalog.models
            .filterNot(TtsModel::bundled)
            .filter { model ->
                ttsModels.statuses.value[model] !is TtsModelStatus.NotInstalled
            }
        var failed = 0
        downloadedModels.forEach { model ->
            ttsDownloads.cancelDownload(model)
            try {
                ttsModels.remove(model)
            } catch (_: Exception) {
                failed++
            }
        }
        if (clearDirectoryContents(ttsDirectory) > 0) failed++
        return failed
    }

    private suspend fun clearAiModel(): Int {
        gemmaDownloads.cancelDownload()
        gemmaLifecycle.close()
        return try {
            gemmaModel.remove()
            if (aiDirectory.exists() && sizeOf(aiDirectory) > 0L) 1 else 0
        } catch (_: Exception) {
            1
        }
    }

    private suspend fun inspectOnIo(): StorageSnapshot {
        val databaseBytes = databaseFiles().sumOf(::sizeOf)
        val imagesBytes = sizeOf(imagesDirectory)
        val pagesBytes = sizeOf(pagesDirectory)
        val ttsBytes = sizeOf(ttsDirectory)
        val aiBytes = sizeOf(aiDirectory)
        val temporaryBytes = sizeOf(appContext.cacheDir) + webViewCacheDirectories.sumOf(::sizeOf)
        val appBytes = sizeOf(appContext.dataDir)
        val knownBytes = databaseBytes + imagesBytes + pagesBytes + ttsBytes + aiBytes + temporaryBytes
        val otherBytes = (appBytes - knownBytes).coerceAtLeast(0L)
        val articleCount = database.articleDao().countArticles()
        val feedCount = database.feedDao().countFeeds()
        val storedContentCount = database.articleContentDao().countContent()
        val readingPositionCount = database.articleReadingPositionDao().countPositions()
        val imageCount = database.articleImageDao().countImages()
        val pageCount = database.articlePageSnapshotDao().countPages()
        val ttsCount = ttsDirectory.listFiles()?.count { !it.name.startsWith(".") } ?: 0
        val aiCount = if (aiBytes > 0L) 1 else 0
        val temporaryCount = countFiles(appContext.cacheDir) + webViewCacheDirectories.sumOf(::countFiles)

        return StorageSnapshot(
            appBytes = appBytes,
            totalDeviceBytes = deviceBytes(total = true),
            availableDeviceBytes = deviceBytes(total = false),
            categories = listOf(
                StorageCategoryUsage(
                    category = StorageCategory.ARTICLE_DATA,
                    bytes = databaseBytes,
                    itemCount = articleCount + feedCount + storedContentCount + readingPositionCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.OFFLINE_PAGES,
                    bytes = pagesBytes,
                    itemCount = pageCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.DOWNLOADED_IMAGES,
                    bytes = imagesBytes,
                    itemCount = imageCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.TTS_MODELS,
                    bytes = ttsBytes,
                    itemCount = ttsCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.ON_DEVICE_AI_MODEL,
                    bytes = aiBytes,
                    itemCount = aiCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.TEMPORARY_CACHE,
                    bytes = temporaryBytes,
                    itemCount = temporaryCount
                ),
                StorageCategoryUsage(
                    category = StorageCategory.OTHER,
                    bytes = otherBytes,
                    itemCount = 0
                )
            ),
            articleCount = articleCount,
            feedCount = feedCount,
            storedContentCount = storedContentCount,
            readingPositionCount = readingPositionCount
        )
    }

    private fun cleanupItemCount(action: StorageCleanupAction, snapshot: StorageSnapshot): Int =
        snapshot.usage(action).itemCount

    private fun clearDirectory(directory: File): Int = clearDirectoryContents(directory)

    private fun clearTemporaryCache(): Int =
        clearDirectory(appContext.cacheDir) + webViewCacheDirectories.sumOf(::clearDirectory)

    private fun clearDirectoryContents(directory: File): Int {
        var failed = 0
        directory.listFiles()?.forEach { file ->
            if (!file.deleteRecursively() && file.exists()) failed++
        }
        return failed
    }

    private fun databaseFiles(): List<File> =
        appContext.getDatabasePath(databaseName).parentFile
            ?.listFiles { file ->
                file.name == databaseName || file.name.startsWith("$databaseName-")
            }
            ?.toList()
            .orEmpty()

    private fun countFiles(directory: File): Int =
        directory.walkTopDown().count(File::isFile)

    private fun sizeOf(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    private fun deviceBytes(total: Boolean): Long = runCatching {
        val stat = StatFs(appContext.dataDir.path)
        if (total) stat.blockCountLong * stat.blockSizeLong else stat.availableBytes
    }.getOrDefault(0L)
}

private fun StorageSnapshot.usage(action: StorageCleanupAction): StorageCategoryUsage =
    categories.first { it.category == action.category }
