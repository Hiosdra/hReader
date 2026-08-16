package com.hiosdra.hreader.adapter.persistence

import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleCredibilityDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.BackendIdentity
import com.hiosdra.hreader.core.application.port.out.CacheStore
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
    private val articleReadingPositionDao: ArticleReadingPositionDao,
    private val preferencesManager: AppPreferences,
    private val imagesDir: File,
    private val pagesDir: File,
    private val backendIdentity: BackendIdentity
) : CacheStore {
    private val ownerMutex = Mutex()

    override suspend fun ensureCacheOwner(): Boolean = ownerMutex.withLock {
        val ownerKey = backendIdentity.cacheOwnerKey()
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

    override suspend fun ensureCacheOwnerWhenConfigured(): Boolean =
        if (backendIdentity.isComplete()) ensureCacheOwner() else false

    override suspend fun clearBackendData() = ownerMutex.withLock {
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
            articleReadingPositionDao.clearAll()
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
