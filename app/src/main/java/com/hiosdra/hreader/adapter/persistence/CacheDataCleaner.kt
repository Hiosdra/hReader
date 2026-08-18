package com.hiosdra.hreader.adapter.persistence

import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CacheDataCleaner(
    private val db: AppDatabase,
    private val imagesDir: File,
    private val pagesDir: File
) {
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
}
