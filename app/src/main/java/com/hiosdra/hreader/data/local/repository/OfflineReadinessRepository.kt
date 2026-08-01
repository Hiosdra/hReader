package com.hiosdra.hreader.data.local.repository

import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.model.ArticleContentSource
import com.hiosdra.hreader.data.model.OfflineReadiness
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        articleDao.observeBacklogCount(),
        articleDao.observeOfflineTargetCount()
    ) { articles, unread, backlog, target ->
        OfflineBase(articles, unread, backlog, target)
    }.combine(
        combine(
            articleContentDao.observeOfflineContentCount(ArticleContentSource.FEED_FALLBACK),
            articleContentDao.observeOfflineContentCount(ArticleContentSource.FULL),
            ::Pair
        ),
        ::Pair
    ).combine(
        combine(
            articleImageDao.observeImageCount(),
            articleImageDao.observeImageBytes(),
            articleImageDao.observeOfflineExpectedImageCount(),
            articleImageDao.observeOfflineStoredExpectedImageCount()
        ) { imageCount, imageBytes, expected, storedExpected ->
            ImageReadiness(imageCount, imageBytes, expected, storedExpected)
        }
    ) { baseAndContent, images ->
        val (base, content) = baseAndContent
        ReadinessBase(
            articleCount = base.articleCount,
            unreadCount = base.unreadCount,
            backlogCount = base.backlogCount,
            storedContentCount = content.first + content.second,
            storedImageCount = images.imageCount,
            storedImageBytes = images.imageBytes,
            offlineTargetCount = base.offlineTargetCount,
            storedFullContentCount = content.second,
            expectedImageCount = images.expectedImageCount,
            storedExpectedImageCount = images.storedExpectedImageCount
        )
    }.combine(preferencesManager.observeLastSyncTimestamp()) { readiness, lastSync ->
        readiness.toOfflineReadiness(lastSync)
    }.distinctUntilChanged()

    private data class OfflineBase(
        val articleCount: Int,
        val unreadCount: Int,
        val backlogCount: Int,
        val offlineTargetCount: Int
    )

    private data class ReadinessBase(
        val articleCount: Int,
        val unreadCount: Int,
        val backlogCount: Int,
        val storedContentCount: Int,
        val storedImageCount: Int,
        val storedImageBytes: Long,
        val offlineTargetCount: Int,
        val storedFullContentCount: Int,
        val expectedImageCount: Int,
        val storedExpectedImageCount: Int
    )

    private data class ImageReadiness(
        val imageCount: Int,
        val imageBytes: Long,
        val expectedImageCount: Int,
        val storedExpectedImageCount: Int
    )

    private fun ReadinessBase.toOfflineReadiness(lastSync: Long): OfflineReadiness = OfflineReadiness(
        articleCount = articleCount,
        unreadCount = unreadCount,
        backlogCount = backlogCount,
        storedContentCount = storedContentCount,
        storedImageCount = storedImageCount,
        storedImageBytes = storedImageBytes,
        offlineTargetCount = offlineTargetCount,
        storedFullContentCount = storedFullContentCount,
        expectedImageCount = expectedImageCount,
        storedExpectedImageCount = storedExpectedImageCount,
        lastSyncAt = lastSync.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    )
}
