package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.domain.model.OfflineReadiness
import com.hiosdra.hreader.core.application.port.out.OfflineReadinessStore
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant

class OfflineReadinessRepository(
    private val articleDao: ArticleDao,
    private val articleContentDao: ArticleContentDao,
    private val articleImageDao: ArticleImageDao,
    private val articlePageSnapshotDao: ArticlePageSnapshotDao,
    private val preferencesManager: SyncPreferences
) : OfflineReadinessStore {
    private val articleReadiness = combine(
        articleDao.observeArticleCount(),
        articleDao.observeUnreadCount(),
        articleDao.observeBacklogCount(),
        articleDao.observeOfflineTargetCount()
    ) { articleCount, unreadCount, backlogCount, offlineTargetCount ->
        ArticleReadiness(articleCount, unreadCount, backlogCount, offlineTargetCount)
    }

    private val contentReadiness = combine(
        articleContentDao.observeOfflineContentCount(ArticleContentSource.FEED_FALLBACK),
        articleContentDao.observeOfflineContentCount(ArticleContentSource.FULL)
    ) { feedContentCount, fullContentCount ->
        ContentReadiness(feedContentCount, fullContentCount)
    }

    private val imageReadiness = combine(
        articleImageDao.observeImageCount(),
        articleImageDao.observeImageBytes(),
        articleImageDao.observeOfflineExpectedImageCount(),
        articleImageDao.observeOfflineStoredExpectedImageCount()
    ) { imageCount, imageBytes, expectedImageCount, storedExpectedImageCount ->
        ImageReadiness(imageCount, imageBytes, expectedImageCount, storedExpectedImageCount)
    }

    override fun observe(): Flow<OfflineReadiness> = combine(
        articleReadiness,
        contentReadiness,
        imageReadiness,
        articlePageSnapshotDao.observeOfflineCompleteCount(),
        preferencesManager.observeLastSyncTimestamp()
    ) { articles, content, images, storedFullPageCount, lastSync ->
        OfflineReadiness(
            articleCount = articles.articleCount,
            unreadCount = articles.unreadCount,
            backlogCount = articles.backlogCount,
            storedContentCount = content.feedContentCount + content.fullContentCount,
            storedImageCount = images.imageCount,
            storedImageBytes = images.imageBytes,
            offlineTargetCount = articles.offlineTargetCount,
            storedFullContentCount = content.fullContentCount,
            expectedImageCount = images.expectedImageCount,
            storedExpectedImageCount = images.storedExpectedImageCount,
            storedFullPageCount = storedFullPageCount,
            lastSyncAt = lastSync.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        )
    }.distinctUntilChanged()

    private data class ArticleReadiness(
        val articleCount: Int,
        val unreadCount: Int,
        val backlogCount: Int,
        val offlineTargetCount: Int
    )

    private data class ContentReadiness(
        val feedContentCount: Int,
        val fullContentCount: Int
    )

    private data class ImageReadiness(
        val imageCount: Int,
        val imageBytes: Long,
        val expectedImageCount: Int,
        val storedExpectedImageCount: Int
    )
}
