package com.hiosdra.hreader.core.application.port.out

interface ArticleImageStore {
    suspend fun getLocalImagePath(entryId: Long, imageUrl: String): String?
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String>
    suspend fun downloadAndStoreImage(entryId: Long, imageUrl: String)
    suspend fun setExpectedImages(entryId: Long, imageUrls: List<String>)
    suspend fun cleanupOrphanedImages()
    suspend fun enforceCacheBudget()
}
