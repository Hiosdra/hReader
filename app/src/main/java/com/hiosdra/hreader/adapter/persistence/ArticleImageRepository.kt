package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

internal class ArticleImageRepository(
    private val index: ArticleImageIndex,
    private val downloader: ArticleImageRemoteDownloader,
    private val files: ArticleImageFileStore,
    private val preferences: SyncPreferences
) : ArticleImageStore {
    constructor(
        context: Context,
        articleImageDao: ArticleImageDao,
        okHttpClient: OkHttpClient,
        preferencesManager: SyncPreferences,
        remoteResourcePolicy: RemoteResourcePolicyAdapter,
        fileExists: (String) -> Boolean = { path -> File(path).exists() }
    ) : this(
        index = ArticleImageIndex(articleImageDao),
        downloader = ArticleImageRemoteDownloader(okHttpClient, remoteResourcePolicy),
        files = ArticleImageFileStore(context, fileExists),
        preferences = preferencesManager
    )

    private val storageMutex = Mutex()

    override suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String): Unit =
        withContext(Dispatchers.IO) {
            if (!preferences.getImageDownloadEnabled()) return@withContext

            val existingImage = index.getImageForArticleByUrl(entryId, imageUrl)
            if (existingImage != null && files.exists(existingImage.localFilePath)) {
                return@withContext
            }

            val imageId = ArticleImageFileStore.imageId(entryId, imageUrl)
            val staging = files.staging(imageId)
            var stored = false
            var target: File? = null
            try {
                val downloaded = downloader.download(imageUrl, staging) ?: return@withContext
                val targetFile = files.target(imageId, getFileExtension(downloaded.contentType, imageUrl))
                target = targetFile
                storageMutex.withLock {
                    files.move(staging, targetFile)
                    index.insert(
                        ArticleImage(
                            id = imageId,
                            entryId = entryId,
                            originalUrl = imageUrl,
                            localFilePath = targetFile.absolutePath,
                            mimeType = downloaded.contentType,
                            downloadedAt = Instant.now(),
                            fileSize = downloaded.fileSize
                        )
                    )
                    stored = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to index image $imageUrl for entry $entryId", e)
                }
            } finally {
                if (!stored) {
                    files.delete(staging)
                    target?.let { files.delete(it) }
                }
            }
        }

    override suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String? =
        index.getImageForArticleByUrl(entryId, imageUrl)?.let { image ->
            if (files.exists(image.localFilePath)) image.localFilePath
            else {
                index.delete(image)
                null
            }
        }

    override suspend fun getLocalImagePaths(entryId: Long): Map<String, String> =
        index.getImagesForArticle(entryId).mapNotNull { image ->
            if (files.exists(image.localFilePath)) {
                image.originalUrl to image.localFilePath
            } else {
                index.delete(image)
                null
            }
        }.toMap()

    override suspend fun getLocalImagePaths(entryIds: List<Long>): Map<Long, Map<String, String>> {
        if (entryIds.isEmpty()) return emptyMap()
        val images = index.getImagesForArticles(entryIds.distinct())
        val missingImageIds = images.filterNot { image -> files.exists(image.localFilePath) }
            .map { it.id }
        if (missingImageIds.isNotEmpty()) index.deleteByIds(missingImageIds)
        return images.asSequence()
            .filter { it.id !in missingImageIds }
            .groupBy { it.entryId }
            .mapValues { (_, articleImages) ->
                articleImages.associate { image -> image.originalUrl to image.localFilePath }
            }
    }

    override fun observeLocalImagePaths(entryId: Long): Flow<Map<String, String>> =
        index.observeImagesForArticle(entryId).map { images ->
            images.mapNotNull { image ->
                val path = image.localFilePath.takeIf(files::exists) ?: return@mapNotNull null
                image.originalUrl to path
            }.toMap()
        }

    override suspend fun setExpectedImages(entryId: Long, imageUrls: List<String>) {
        index.setExpectedImages(entryId, imageUrls)
    }

    override suspend fun invalidateArticleImages(entryId: Long) = withContext(Dispatchers.IO) {
        index.getImagePathsForArticles(listOf(entryId)).forEach { path -> File(path).delete() }
        index.deleteImagesForArticles(listOf(entryId))
        index.deleteExpectedImagesForArticles(listOf(entryId))
    }

    override suspend fun clearAll(): Unit = storageMutex.withLock {
        withContext(Dispatchers.IO) {
            index.clearAll()
            files.clearAll()
        }
    }

    private fun getFileExtension(contentType: String?, imageUrl: String): String {
        val mimeType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        MIME_TYPE_EXTENSIONS[mimeType]?.let { return it }

        val urlExtension = runCatching { URI(imageUrl).path.orEmpty().substringAfterLast('.', "") }
            .getOrDefault("")
            .lowercase()
        return URL_EXTENSIONS[urlExtension] ?: ".img"
    }

    private companion object {
        const val TAG = "ArticleImageRepo"
        val MIME_TYPE_EXTENSIONS = mapOf(
            "image/gif" to ".gif",
            "image/jpeg" to ".jpg",
            "image/png" to ".png",
            "image/svg+xml" to ".svg",
            "image/webp" to ".webp"
        )
        val URL_EXTENSIONS = mapOf(
            "gif" to ".gif",
            "jpeg" to ".jpg",
            "jpg" to ".jpg",
            "png" to ".png",
            "svg" to ".svg",
            "webp" to ".webp"
        )
    }
}
