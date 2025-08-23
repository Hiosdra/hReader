package com.hiosdra.hreader.util

import com.hiosdra.hreader.data.local.repository.ArticleImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageLoader(private val articleImageRepository: ArticleImageRepository) {
    
    suspend fun getImagePath(entryId: Long, imageUrl: String): String = withContext(Dispatchers.IO) {
        // First try to get local image path
        val localPath = articleImageRepository.getLocalImagePath(entryId, imageUrl)
        if (localPath != null && File(localPath).exists()) {
            return@withContext "file://$localPath"
        }
        
        // Fall back to original URL
        return@withContext imageUrl
    }
    
    suspend fun getImagePathForEnclosure(entryId: Long, imageUrl: String): String = withContext(Dispatchers.IO) {
        val localPath = articleImageRepository.getLocalImagePath(entryId, imageUrl)
        if (localPath != null && File(localPath).exists()) {
            return@withContext "file://$localPath"
        }
        return@withContext imageUrl
    }
}