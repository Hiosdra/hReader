package com.hiosdra.hreader.adapter.persistence

import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.core.application.port.out.CacheMaintenanceStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CacheDataCleaner(
    private val db: AppDatabase,
    private val imagesDir: File,
    private val pagesDir: File,
    private val maintenance: CacheMaintenanceStore? = null
) {
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            imagesDir.listFiles()?.forEach { it.deleteRecursively() }
            pagesDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        db.withTransaction {
            val articleImageDao = db.articleImageDao()
            val articleRecordDao = db.articleRecordDao()
            db.articleCredibilityDao().clearAll()
            db.articleAiOverviewDao().clearAll()
            db.articleContentDao().clearAll()
            articleImageDao.clearAll()
            articleImageDao.clearExpectedImages()
            db.articlePageSnapshotDao().clearAll()
            db.articleReadingPositionDao().clearAll()
            articleRecordDao.clearAll()
            articleRecordDao.clearSearchIndex()
            db.feedDao().clearAll()
            db.fullSyncSeenDao().deleteAll()
        }
    }

    suspend fun repair() {
        maintenance?.maintain()
    }
}
