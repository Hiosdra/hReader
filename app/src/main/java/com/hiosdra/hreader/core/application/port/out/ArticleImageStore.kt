package com.hiosdra.hreader.core.application.port.out

import kotlinx.coroutines.flow.Flow

interface ArticleImageStore {
    suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String?
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String>
    suspend fun getLocalImagePaths(entryIds: List<Long>): Map<Long, Map<String, String>> =
        entryIds.distinct().associateWith { entryId -> getLocalImagePaths(entryId) }
    fun observeLocalImagePaths(entryId: Long): Flow<Map<String, String>>
    suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String)
    suspend fun setExpectedImages(entryId: Long, imageUrls: List<String>)
    suspend fun invalidateArticleImages(entryId: Long)
    suspend fun cleanupOrphanedImages()
    suspend fun enforceCacheBudget()
}
