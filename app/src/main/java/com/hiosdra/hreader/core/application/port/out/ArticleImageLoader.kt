package com.hiosdra.hreader.core.application.port.out

interface ArticleImageLoader {
    suspend fun getImagePath(entryId: Long, imageUrl: String): String
    suspend fun getImageModel(entryId: Long, imageUrl: String, allowNetwork: Boolean): String?
    suspend fun getLocalImagePaths(entryId: Long): Map<String, String>
}
