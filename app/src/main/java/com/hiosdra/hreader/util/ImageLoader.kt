package com.hiosdra.hreader.util

import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageLoader(
    private val articleImageRepository: ArticleImageRepository,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
    suspend fun getImagePath(entryId: Long, imageUrl: String): String = withContext(Dispatchers.IO) {
        val localPath = articleImageRepository.getLocalImagePath(entryId, imageUrl)
        localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
        return@withContext imageUrl
    }

    suspend fun getImageModel(entryId: Long, imageUrl: String, allowNetwork: Boolean): String? =
        withContext(Dispatchers.IO) {
            val localPath = articleImageRepository.getLocalImagePath(entryId, imageUrl)
            localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
            imageUrl.takeIf { allowNetwork }
        }

    /** Where each of this article's images was downloaded, keyed by its published address. */
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        articleImageRepository.getLocalImagePaths(entryId)
    }
}
