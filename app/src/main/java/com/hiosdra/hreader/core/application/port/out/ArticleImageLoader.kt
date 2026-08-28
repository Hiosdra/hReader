package com.hiosdra.hreader.core.application.port.out

import kotlinx.coroutines.flow.Flow

interface ArticleImageLoader {
    suspend fun getImagePath(entryId: Long, imageUrl: String): String
    suspend fun getImageModel(entryId: Long, imageUrl: String, allowNetwork: Boolean): String?
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String>
    suspend fun getLocalImagePaths(entryIds: List<Long>): Map<Long, Map<String, String>> =
        entryIds.distinct().associateWith { entryId -> getLocalImagePaths(entryId) }
    fun observeLocalImagePaths(entryId: Long): Flow<Map<String, String>>
}
