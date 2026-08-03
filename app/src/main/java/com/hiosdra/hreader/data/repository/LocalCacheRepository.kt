package com.hiosdra.hreader.data.repository

import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleCredibilityDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.data.local.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.remote.ServerConfig
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LocalCacheRepository(
    private val db: AppDatabase,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val articleContentDao: ArticleContentDao,
    private val articleImageDao: ArticleImageDao,
    private val articleCredibilityDao: ArticleCredibilityDao,
    private val articleAiOverviewDao: ArticleAiOverviewDao,
    private val articlePageSnapshotDao: ArticlePageSnapshotDao,
    private val preferencesManager: PreferencesManager,
    private val imagesDir: File,
    private val pagesDir: File,
    private val serverConfig: ServerConfig
) {
    private val ownerMutex = Mutex()

    suspend fun ensureCacheOwner(): Boolean = ownerMutex.withLock {
        val ownerKey = serverConfig.cacheOwnerKey()
        val storedOwner = preferencesManager.getCacheOwnerKey()
        if (storedOwner.isBlank()) {
            preferencesManager.setCacheOwnerKey(ownerKey)
            return@withLock false
        }
        if (storedOwner == ownerKey) return@withLock false
        clearBackendDataLocked()
        preferencesManager.setCacheOwnerKey(ownerKey)
        true
    }

    suspend fun ensureCacheOwnerWhenConfigured(): Boolean =
        if (serverConfig.isComplete()) ensureCacheOwner() else false

    suspend fun clearBackendData() = ownerMutex.withLock {
        clearBackendDataLocked()
    }

    private suspend fun clearBackendDataLocked() {
        withContext(Dispatchers.IO) {
            imagesDir.listFiles()?.forEach { it.delete() }
            pagesDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        db.withTransaction {
            articleCredibilityDao.clearAll()
            articleAiOverviewDao.clearAll()
            articleContentDao.clearAll()
            articleImageDao.clearAll()
            articleImageDao.clearExpectedImages()
            articlePageSnapshotDao.clearAll()
            articleDao.clearAll()
            feedDao.clearAll()
        }
        preferencesManager.setCacheOwnerKey("")
        // Both timestamps, or the next sync against a different backend would still consider the
        // cache recently reconciled against a full server state that was never this backend's.
        preferencesManager.setLastSyncTimestamp(0L)
        preferencesManager.setLastFullSyncTimestamp(0L)
    }
}
