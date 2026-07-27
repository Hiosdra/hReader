package com.hiosdra.hreader.data.repository

import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalCacheRepository(
    private val db: AppDatabase,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val articleContentDao: ArticleContentDao,
    private val articleImageDao: ArticleImageDao,
    private val preferencesManager: PreferencesManager,
    private val imagesDir: File
) {
    suspend fun clearBackendData() {
        db.withTransaction {
            articleContentDao.clearAll()
            articleImageDao.clearAll()
            articleDao.clearAll()
            feedDao.clearAll()
        }
        withContext(Dispatchers.IO) {
            imagesDir.listFiles()?.forEach { it.delete() }
        }
        preferencesManager.setLastSyncTimestamp(0L)
    }
}
