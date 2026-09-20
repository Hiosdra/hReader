package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageFile
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class ArticleImageMaintenance(
    private val index: ArticleImageIndex,
    private val files: ArticleImageFileStore,
    private val preferences: SyncPreferences
) {
    suspend fun cleanupOrphaned() {
        while (true) {
            val orphaned = index.getOrphanedImageEntryIds(DELETE_CHUNK)
            if (orphaned.isEmpty()) break
            index.getImagePathsForArticles(orphaned).forEach { path -> File(path).delete() }
            index.deleteImagesForArticles(orphaned)
            index.deleteExpectedImagesForArticles(orphaned)
        }
        while (true) {
            val orphaned = index.getOrphanedExpectedEntryIds(DELETE_CHUNK)
            if (orphaned.isEmpty()) break
            index.deleteExpectedImagesForArticles(orphaned)
        }
    }

    suspend fun enforceBudget() {
        val budgetBytes = preferences.getImageCacheBudgetMegabytes() * BYTES_PER_MEGABYTE
        if (budgetBytes <= 0) return

        var storedBytes = index.getTotalImageBytes()
        if (storedBytes <= budgetBytes) return

        while (storedBytes > budgetBytes) {
            val images = index.getImagesOldestFirst(DELETE_CHUNK)
            if (images.isEmpty()) break
            for (image in images) {
                if (storedBytes <= budgetBytes) break
                File(image.localFilePath).delete()
                index.delete(image)
                storedBytes -= image.fileSize ?: 0L
            }
        }
        Log.i(TAG, "Image cache trimmed to $storedBytes bytes")
    }

    suspend fun repairFiles() {
        val referencedPaths = mutableSetOf<String>()
        var afterId = ""
        while (true) {
            val images = index.getImageFilesAfterId(afterId, DELETE_CHUNK)
            if (images.isEmpty()) break
            val missingIds = images.filterNot { File(it.localFilePath).isFile }.map(ArticleImageFile::id)
            if (missingIds.isNotEmpty()) index.deleteByIds(missingIds)
            images.asSequence()
                .filterNot { it.id in missingIds }
                .mapNotNull { runCatching { File(it.localFilePath).canonicalPath }.getOrNull() }
                .forEach(referencedPaths::add)
            afterId = images.last().id
        }
        withContext(Dispatchers.IO) {
            files.files().forEach { file ->
                val path = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
                if (file.isFile && !path.endsWith(".tmp") && path !in referencedPaths) {
                    file.delete()
                }
            }
        }
    }

    private companion object {
        const val DELETE_CHUNK = 500
        const val BYTES_PER_MEGABYTE = 1024L * 1024
        const val TAG = "ArticleImageMaintenance"
    }
}
