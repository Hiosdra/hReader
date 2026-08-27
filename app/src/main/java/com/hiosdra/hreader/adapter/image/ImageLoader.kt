package com.hiosdra.hreader.adapter.image

import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.RemoteResourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedHashMap

class ImageLoader(
    private val articleImageStore: ArticleImageStore,
    private val remoteResourcePolicy: RemoteResourcePolicy,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) : ArticleImageLoader {
    private data class ImageKey(val entryId: Long, val imageUrl: String)

    private val localPathCache = object : LinkedHashMap<ImageKey, String>(LOCAL_PATH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ImageKey, String>?): Boolean =
            size > LOCAL_PATH_CACHE_SIZE
    }
    private val cacheLock = Any()

    override suspend fun getImagePath(entryId: Long, imageUrl: String): String = withContext(Dispatchers.IO) {
        val localPath = findLocalPath(entryId, imageUrl)
        localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
        if (!remoteResourcePolicy.allows(imageUrl)) return@withContext ""
        return@withContext imageUrl
    }

    override suspend fun getImageModel(entryId: Long, imageUrl: String, allowNetwork: Boolean): String? =
        withContext(Dispatchers.IO) {
            val localPath = findLocalPath(entryId, imageUrl)
            localPath?.takeIf { fileExists(it) }?.let { return@withContext "file://$it" }
            imageUrl.takeIf { allowNetwork && remoteResourcePolicy.allows(it) }
        }

    /** Where each of this article's images was downloaded, keyed by its published address. */
    override suspend fun getLocalImagePaths(entryId: Long): Map<String, String> = withContext(Dispatchers.IO) {
        articleImageStore.getLocalImagePaths(entryId).also { paths ->
            synchronized(cacheLock) {
                localPathCache.entries.removeIf { it.key.entryId == entryId }
                paths.forEach { (url, path) -> localPathCache[ImageKey(entryId, url)] = path }
            }
        }
    }

    override suspend fun getLocalImagePaths(entryIds: List<Long>): Map<Long, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val distinctIds = entryIds.distinct()
            if (distinctIds.isEmpty()) return@withContext emptyMap()
            articleImageStore.getLocalImagePaths(distinctIds).also { pathsByEntry ->
                synchronized(cacheLock) {
                    localPathCache.entries.removeIf { it.key.entryId in distinctIds }
                    pathsByEntry.forEach { (entryId, paths) ->
                        paths.forEach { (url, path) ->
                            localPathCache[ImageKey(entryId, url)] = path
                        }
                    }
                }
            }
        }

    override fun observeLocalImagePaths(entryId: Long): Flow<Map<String, String>> =
        articleImageStore.observeLocalImagePaths(entryId)

    private suspend fun findLocalPath(entryId: Long, imageUrl: String): String? {
        val key = ImageKey(entryId, imageUrl)
        val cached = synchronized(cacheLock) { localPathCache[key] }
        if (cached != null) {
            if (fileExists(cached)) return cached
            synchronized(cacheLock) { localPathCache.remove(key) }
        }
        val loaded = articleImageStore.getLocalImagePath(entryId, imageUrl)
            ?.takeIf(fileExists)
        if (loaded != null) synchronized(cacheLock) { localPathCache[key] = loaded }
        return loaded
    }

    private companion object {
        private const val LOCAL_PATH_CACHE_SIZE = 256
    }
}
