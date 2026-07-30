package com.hiosdra.hreader.data.local.repository

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.entity.ArticleImage
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant

class ArticleImageRepository(
    context: Context,
    private val articleImageDao: ArticleImageDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    private val preferencesManager: PreferencesManager,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
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
    }

    private val imagesDir = File(context.filesDir, "article_images")
        .apply { mkdirs() }

    private val cacheBudgetMutex = Mutex()

    suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String): ArticleImage? =
        withContext(Dispatchers.IO) {
            try {
                if (!preferencesManager.getImageDownloadEnabled()) return@withContext null

                val existingImage = articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
                if (existingImage != null && fileExists(existingImage.localFilePath)) {
                    return@withContext existingImage
                }

                // Download image
                val request = Request.Builder().url(imageUrl).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext null
                }

                val body = response.body
                val contentType = body.contentType()?.toString()

                // A declared length settles it without spending any bandwidth at all. Most CDNs
                // send the image chunked and declare nothing, which is what the streaming cap
                // below is for.
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_IMAGE_BYTES) {
                    Log.d(TAG, "Skipping $imageUrl: $declaredLength bytes exceeds the per-image cap")
                    response.close()
                    return@withContext null
                }

                // Generate file name and path
                val imageId = generateImageId(entryId, imageUrl)
                val extension = getFileExtension(contentType, imageUrl)
                val fileName = "$imageId$extension"
                val localFile = File(imagesDir, fileName)

                // Streamed with the cap applied as it goes. A response without a declared length —
                // anything chunked, which is most CDNs — used to sail past the check above and be
                // downloaded in full before its size could be objected to.
                val fileSize = response.use { copyAtMost(body.byteStream(), localFile, MAX_IMAGE_BYTES) }
                if (fileSize == null) {
                    Log.d(TAG, "Discarding $imageUrl: larger than the per-image cap")
                    localFile.delete()
                    return@withContext null
                }

                val articleImage = ArticleImage(
                    id = imageId,
                    entryId = entryId,
                    originalUrl = imageUrl,
                    localFilePath = localFile.absolutePath,
                    mimeType = contentType,
                    downloadedAt = Instant.now(),
                    fileSize = fileSize
                )

                articleImageDao.insertArticleImage(articleImage)
                enforceCacheBudget()
                articleImage
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download/store image $imageUrl for entry $entryId", e)
                null
            }
        }

    suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String? =
        articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
            ?.takeIf { fileExists(it.localFilePath) }
            ?.localFilePath

    /** Every downloaded image of one article, keyed by the address it was published under. */
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String> =
        articleImageDao.getImagesForArticle(entryId)
            .filter { fileExists(it.localFilePath) }
            .associate { it.originalUrl to it.localFilePath }

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
    suspend fun enforceCacheBudget(): Unit = cacheBudgetMutex.withLock {
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
    suspend fun cleanupOrphanedImages() {
        val storedEntryIds = articleImageDao.getAllImageEntryIds()
        if (storedEntryIds.isEmpty()) return

        val currentEntryIds = articleDao.getAllIds().mapNotNull { it.toLongOrNull() }.toHashSet()
        val orphaned = storedEntryIds.filterNot { currentEntryIds.contains(it) }
        if (orphaned.isEmpty()) return

        orphaned.chunked(DELETE_CHUNK).forEach { chunk ->
            articleImageDao.getImagePathsForArticles(chunk).forEach { File(it).delete() }
            articleImageDao.deleteImagesForArticles(chunk)
        }
    }

    private fun generateImageId(entryId: Long, imageUrl: String): String {
        val input = "$entryId-$imageUrl"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getFileExtension(contentType: String?, imageUrl: String): String {
        val extensionMap = mapOf(
            "png" to ".png",
            "webp" to ".webp", 
            "gif" to ".gif",
            "svg" to ".svg",
            "jpeg" to ".jpg",
            "jpg" to ".jpg"
        )
        
        // Check content type first
        extensionMap.forEach { (key, ext) ->
            if (contentType?.contains(key, ignoreCase = true) == true) return ext
        }
        
        // Fallback to URL extension
        extensionMap.forEach { (key, ext) ->
            if (imageUrl.contains(".$key", ignoreCase = true)) return ext
        }
        
        return ".img"
    }
}
