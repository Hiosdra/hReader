package com.hiosdra.hreader.data.local.repository

import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.model.OfflineReadiness
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant

class OfflineReadinessRepository(
    private val articleDao: ArticleDao,
    private val articleContentDao: ArticleContentDao,
    private val articleImageDao: ArticleImageDao,
    private val preferencesManager: PreferencesManager
) {
    fun observe(): Flow<OfflineReadiness> = combine(
        articleDao.observeArticleCount(),
        articleDao.observeUnreadCount(),
        articleContentDao.observeContentCount(),
        combine(articleImageDao.observeImageCount(), articleImageDao.observeImageBytes(), ::Pair)
    ) { articles, unread, contents, images ->
        OfflineReadiness(
            articleCount = articles,
            unreadCount = unread,
            storedContentCount = contents,
            storedImageCount = images.first,
            storedImageBytes = images.second,
            lastSyncAt = preferencesManager.getLastSyncTimestamp()
                .takeIf { it > 0 }
                ?.let { Instant.ofEpochMilli(it) }
        )
    }
}
