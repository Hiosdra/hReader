package com.hiosdra.hreader.data.local.repository

import android.content.Context
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
import java.security.MessageDigest
import java.time.Instant

class ArticleImageRepository(
    private val context: Context,
    private val articleImageDao: ArticleImageDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient
) {
    private val imagesDir = File(context.filesDir, "article_images").apply { mkdirs() }

    suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String): ArticleImage? = withContext(Dispatchers.IO) {
        try {
            // Check if image already exists
            val existingImage = articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
            if (existingImage != null && File(existingImage.localFilePath).exists()) {
                return@withContext existingImage
            }

            // Download image
            val request = Request.Builder().url(imageUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val bytes = body.bytes()
            val contentType = body.contentType()?.toString()
            response.close()

            // Generate file name and path
            val imageId = generateImageId(entryId, imageUrl)
            val extension = getFileExtension(contentType, imageUrl)
            val fileName = "$imageId$extension"
            val localFile = File(imagesDir, fileName)

            // Save to local storage
            FileOutputStream(localFile).use { it.write(bytes) }

            val articleImage = ArticleImage(
                id = imageId,
                entryId = entryId,
                originalUrl = imageUrl,
                localFilePath = localFile.absolutePath,
                mimeType = contentType,
                downloadedAt = Instant.now(),
                fileSize = bytes.size.toLong()
            )

            articleImageDao.insertArticleImage(articleImage)
            articleImage
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String? {
        val articleImage = articleImageDao.getImageForArticleByUrl(entryId, imageUrl)
        return if (articleImage != null && File(articleImage.localFilePath).exists()) {
            articleImage.localFilePath
        } else null
    }

    suspend fun getImagesForArticle(entryId: Long): List<ArticleImage> {
        return articleImageDao.getImagesForArticle(entryId)
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
            // Delete local file
            File(image.localFilePath).delete()
            // Remove from database
            articleImageDao.deleteArticleImage(image)
        }
    }

    private fun generateImageId(entryId: Long, imageUrl: String): String {
        val input = "$entryId-$imageUrl"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun getFileExtension(contentType: String?, imageUrl: String): String {
        return when {
            contentType?.contains("png") == true -> ".png"
            contentType?.contains("webp") == true -> ".webp"
            contentType?.contains("gif") == true -> ".gif"
            contentType?.contains("svg") == true -> ".svg"
            contentType?.contains("jpeg") == true || contentType?.contains("jpg") == true -> ".jpg"
            imageUrl.contains(".png", ignoreCase = true) -> ".png"
            imageUrl.contains(".webp", ignoreCase = true) -> ".webp"
            imageUrl.contains(".gif", ignoreCase = true) -> ".gif"
            imageUrl.contains(".svg", ignoreCase = true) -> ".svg"
            imageUrl.contains(".jpg", ignoreCase = true) || imageUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
            else -> ".img"
        }
    }
}