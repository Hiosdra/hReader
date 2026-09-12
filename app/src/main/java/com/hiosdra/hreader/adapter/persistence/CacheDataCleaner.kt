package com.hiosdra.hreader.adapter.persistence

import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageFile
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CacheDataCleaner(
    private val db: AppDatabase,
    private val imagesDir: File,
    private val pagesDir: File,
    private val contentStore: ArticleContentStore? = null
) {
    private companion object {
        const val CLEANUP_BATCH_SIZE = 500
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            imagesDir.listFiles()?.forEach { it.deleteRecursively() }
            pagesDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        db.withTransaction {
            val articleImageDao = db.articleImageDao()
            db.articleCredibilityDao().clearAll()
            db.articleAiOverviewDao().clearAll()
            db.articleContentDao().clearAll()
            articleImageDao.clearAll()
            articleImageDao.clearExpectedImages()
            db.articlePageSnapshotDao().clearAll()
            db.articleReadingPositionDao().clearAll()
            db.articleDao().clearAll()
            db.feedDao().clearAll()
        }
    }

    suspend fun repair() {
        contentStore?.cleanupOrphanedContent()
        val articleImageDao = db.articleImageDao()
        val referencedPaths = mutableSetOf<String>()
        var afterId = ""
        while (true) {
            val images = articleImageDao.getImageFilesAfterId(afterId, CLEANUP_BATCH_SIZE)
            if (images.isEmpty()) break
            afterId = images.last().id
            val missing = images.filterNot { image -> File(image.localFilePath).isFile }
            val missingIds = missing.mapTo(hashSetOf()) { it.id }
            missing.chunked(CLEANUP_BATCH_SIZE).forEach { chunk ->
                articleImageDao.deleteByIds(chunk.map(ArticleImageFile::id))
            }
            images.asSequence()
                .filterNot { it.id in missingIds }
                .mapNotNull { image -> runCatching { File(image.localFilePath).canonicalPath }.getOrNull() }
                .forEach(referencedPaths::add)
        }
        withContext(Dispatchers.IO) {
            imagesDir.listFiles()?.forEach { file ->
                val path = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
                if (file.isFile && !path.endsWith(".tmp") && path !in referencedPaths) {
                    file.delete()
                }
            }
            pagesDir.listFiles()
                ?.filter { it.name.startsWith(".staging-") || it.name.startsWith(".backup-") }
                ?.forEach(File::deleteRecursively)
        }
    }
}
