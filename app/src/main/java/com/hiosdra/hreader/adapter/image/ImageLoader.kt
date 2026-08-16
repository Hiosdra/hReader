package com.hiosdra.hreader.adapter.image

import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageLoader(
    private val articleImageStore: ArticleImageStore,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) : ArticleImageLoader {
    override suspend fun getImagePath(entryId: Long, imageUrl: String): String = withContext(Dispatchers.IO) {
        val localPath = articleImageStore.getLocalImagePath(entryId, imageUrl)
        localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
        return@withContext imageUrl
    }

    override suspend fun getImageModel(entryId: Long, imageUrl: String, allowNetwork: Boolean): String? =
        withContext(Dispatchers.IO) {
            val localPath = articleImageStore.getLocalImagePath(entryId, imageUrl)
            localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
            imageUrl.takeIf { allowNetwork }
        }

    /** Where each of this article's images was downloaded, keyed by its published address. */
    override suspend fun getLocalImagePaths(entryId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        articleImageStore.getLocalImagePaths(entryId)
    }
}
