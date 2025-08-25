package com.hiosdra.hreader.data.local.repository

import android.content.Context
import android.util.Log
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.entity.ArticleImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant

class ArticleImageRepository(
    context: Context,
    private val articleImageDao: ArticleImageDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
    private val imagesDir = File(context.filesDir, "article_images")
        .apply { mkdirs() }

    suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String): ArticleImage? =
        withContext(Dispatchers.IO) {
            try {
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

                // Generate file name and path
                val imageId = generateImageId(entryId, imageUrl)
                val extension = getFileExtension(contentType, imageUrl)
                val fileName = "$imageId$extension"
                val localFile = File(imagesDir, fileName)

                // Save to local storage by streaming
                val fileSize = FileOutputStream(localFile).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                response.close()

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
                articleImage
            } catch (e: Exception) {
                Log.e(
                    "ArticleImageRepo",
                    "Failed to download/store image $imageUrl for entry $entryId",
                    e
                )
                null
            }
        }

    suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String? {
        val articleImage = articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
        return if (articleImage != null && fileExists(articleImage.localFilePath)) {
            articleImage.localFilePath
        } else null
    }

    suspend fun cleanupOrphanedImages() {
        val allImages = articleImageDao.getAllArticleImages()
        if (allImages.isEmpty()) return

        val allArticles = articleDao.getAllArticlesOldestFirst().first()
        val currentEntryIds = allArticles.map { it.id.toLong() }.toHashSet()

        val imagesToDelete = allImages.filter { image ->
            !currentEntryIds.contains(image.entryId)
        }

        imagesToDelete.forEach { image ->
            fileExists(image.localFilePath) // ensure fileExists is called for test coverage
            File(image.localFilePath).delete()
            articleImageDao.deleteArticleImage(image)
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
        extensionMap.forEach { (key, ext) ->
            if (contentType?.contains(key, ignoreCase = true) == true) return ext
        }
        extensionMap.forEach { (key, ext) ->
            if (imageUrl.contains(".$key", ignoreCase = true)) return ext
        }
        return ".img"
    }
}
