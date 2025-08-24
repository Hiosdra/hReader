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
        if (localPath != null && fileExists(localPath)) {
            return@withContext "file://$localPath"
        }
        return@withContext imageUrl
    }
}
