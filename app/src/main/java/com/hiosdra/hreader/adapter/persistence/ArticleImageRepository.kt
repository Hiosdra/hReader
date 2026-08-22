package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.BuildConfig
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageManifest
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class ArticleImageRepository(
    context: Context,
    private val articleImageDao: ArticleImageDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    private val preferencesManager: SyncPreferences,
    private val remoteResourcePolicy: RemoteResourcePolicy,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) : ArticleImageStore {
    companion object {
        private const val TAG = "ArticleImageRepo"

        /**
         * A single hero image this large is a photographer's export, not something a phone screen
         * needs, and one of them can eat a noticeable slice of the whole cache budget.
         */
        private const val MAX_IMAGE_BYTES = 2L * 1024 * 1024

        private const val BYTES_PER_MEGABYTE = 1024L * 1024

        /** Below SQLite's 999 bound-variable ceiling on Android. */
        private const val DELETE_CHUNK = 500

        private val MIME_TYPE_EXTENSIONS = mapOf(
            "image/gif" to ".gif",
            "image/jpeg" to ".jpg",
            "image/png" to ".png",
            "image/svg+xml" to ".svg",
            "image/webp" to ".webp"
        )

        private val URL_EXTENSIONS = mapOf(
            "gif" to ".gif",
            "jpeg" to ".jpg",
            "jpg" to ".jpg",
            "png" to ".png",
            "svg" to ".svg",
            "webp" to ".webp"
        )
    }

    private val imagesDir = File(context.filesDir, "article_images").also { directory ->
        directory.mkdirs()
        directory.listFiles { file ->
            file.name.startsWith(".") && file.name.endsWith(".tmp")
        }?.forEach(File::delete)
    }

    private val cacheBudgetMutex = Mutex()
    private val safeHttpClient = okHttpClient.newBuilder()
        .addNetworkInterceptor { chain ->
            if (!remoteResourcePolicy.allows(chain.request().url.toString())) {
                throw IOException("Blocked remote resource URL")
            }
            chain.proceed(chain.request())
        }
        .dns(remoteResourcePolicy.dns())
        .build()

    override suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String): Unit =
        withContext(Dispatchers.IO) {
            var localFile: File? = null
            var stored = false
            try {
                if (!preferencesManager.getImageDownloadEnabled()) return@withContext
                if (!remoteResourcePolicy.allows(imageUrl)) return@withContext

                val existingImage = articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
                if (existingImage != null && fileExists(existingImage.localFilePath)) {
                    return@withContext
                }

                // Download image
                val request = Request.Builder().url(imageUrl).build()
                safeHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    if (!remoteResourcePolicy.allows(response.request.url.toString())) return@withContext

                    val body = response.body
                    val contentType = body.contentType()?.toString()
                    if (contentType?.startsWith("image/", ignoreCase = true) != true) return@withContext

                    // A declared length settles it without spending any bandwidth at all. Most
                    // CDNs send the image chunked and declare nothing, which is what the streaming
                    // cap below is for.
                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_IMAGE_BYTES) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Skipping $imageUrl: $declaredLength bytes exceeds the per-image cap")
                        }
                        return@withContext
                    }

                    val imageId = generateImageId(entryId, imageUrl)
                    val extension = getFileExtension(contentType, imageUrl)
                    val target = File(imagesDir, "$imageId$extension")
                    val staging = File(imagesDir, ".$imageId-${UUID.randomUUID()}.tmp")
                    localFile = staging

                    // Streamed with the cap applied as it goes. A response without a declared
                    // length — anything chunked, which is most CDNs — used to sail past the check
                    // above and be downloaded in full before its size could be objected to.
                    val fileSize = copyAtMost(body.byteStream(), staging, MAX_IMAGE_BYTES)
                    if (fileSize == null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Discarding $imageUrl: larger than the per-image cap")
                        }
                        return@withContext
                    }

                    moveIntoCache(staging, target)
                    localFile = target

                    val articleImage = ArticleImage(
                        id = imageId,
                        entryId = entryId,
                        originalUrl = imageUrl,
                        localFilePath = target.absolutePath,
                        mimeType = contentType,
                        downloadedAt = Instant.now(),
                        fileSize = fileSize
                    )

                    articleImageDao.insertArticleImage(articleImage)
                    stored = true
                    enforceCacheBudget()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to download/store image $imageUrl for entry $entryId", e)
                }
            } finally {
                if (!stored) localFile?.delete()
            }
        }

    override suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String? =
        articleImageDao.getImageForArticleByUrl(entryId, imageUrl)?.let { image ->
            if (fileExists(image.localFilePath)) image.localFilePath
            else {
                articleImageDao.deleteArticleImage(image)
                null
            }
        }

    /** Every downloaded image of one article, keyed by the address it was published under. */
    override suspend fun getLocalImagePaths(entryId: Long): Map<String, String> =
        articleImageDao.getImagesForArticle(entryId).mapNotNull { image ->
            if (fileExists(image.localFilePath)) {
                image.originalUrl to image.localFilePath
            } else {
                articleImageDao.deleteArticleImage(image)
                null
            }
        }.toMap()

    override suspend fun setExpectedImages(entryId: Long, imageUrls: List<String>) {
        articleImageDao.deleteExpectedImagesForArticle(entryId)
        val expected = imageUrls.distinct().map { url -> ArticleImageManifest(entryId, url) }
        if (expected.isNotEmpty()) articleImageDao.insertExpectedImages(expected)
    }

    /**
     * Copies [input] into [target], stopping and reporting null once it goes past [limit]. Returns
     * how many bytes were written.
     */
    private fun copyAtMost(input: InputStream, target: File, limit: Long): Long? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        FileOutputStream(target).use { output ->
            input.use { source ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    written += read
                    if (written > limit) return null
                    output.write(buffer, 0, read)
                }
            }
        }
        return written
    }

    private fun moveIntoCache(staging: File, target: File) {
        try {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    /**
     * Keeps the image directory under the configured budget by dropping the oldest downloads
     * first. Without it a large backlog fills the device, and offline is exactly when the user
     * cannot free space by re-downloading anything.
     *
     * Serialized: prefetching runs a hundred downloads at a time and each one asks for this
     * afterwards. Concurrently, every caller read the same total, walked the same oldest-first list
     * and deleted the same files, cutting the cache to a fraction of its budget — including images
     * downloaded moments earlier for the trip the reader was packing for.
     */
    override suspend fun enforceCacheBudget(): Unit = cacheBudgetMutex.withLock {
        val budgetBytes = preferencesManager.getImageCacheBudgetMegabytes() * BYTES_PER_MEGABYTE
        if (budgetBytes <= 0) return@withLock

        var storedBytes = articleImageDao.getTotalImageBytes()
        if (storedBytes <= budgetBytes) return@withLock

        for (image in articleImageDao.getImagesOldestFirst()) {
            if (storedBytes <= budgetBytes) break
            File(image.localFilePath).delete()
            articleImageDao.deleteArticleImage(image)
            storedBytes -= image.fileSize ?: 0L
        }
        Log.i(TAG, "Image cache trimmed to $storedBytes bytes")
    }

    /**
     * Drops what is stored for articles the cache no longer holds. Reads the article ids rather
     * than the image rows: retention and full-sync reconciliation can orphan thousands at once.
     */
    override suspend fun cleanupOrphanedImages() {
        val storedEntryIds = (
            articleImageDao.getAllImageEntryIds() + articleImageDao.getAllExpectedImageEntryIds()
            ).distinct()
        if (storedEntryIds.isEmpty()) return

        val currentEntryIds = articleDao.getAllIds().mapNotNull { it.toLongOrNull() }.toHashSet()
        val orphaned = storedEntryIds.filterNot { currentEntryIds.contains(it) }
        if (orphaned.isEmpty()) return

        orphaned.chunked(DELETE_CHUNK).forEach { chunk ->
            articleImageDao.getImagePathsForArticles(chunk).forEach { File(it).delete() }
            articleImageDao.deleteImagesForArticles(chunk)
            articleImageDao.deleteExpectedImagesForArticles(chunk)
        }
    }

    private fun generateImageId(entryId: Long, imageUrl: String): String {
        val input = "$entryId-$imageUrl"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
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
}
