package com.hiosdra.hreader.data.local.repository

import android.util.Log
import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.FeedBackend
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private const val ENTRIES_BATCH_LIMIT = 200

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val api: FeedBackend,
    private val db: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val syncPerformanceLogger: SyncPerformanceLogger
) {
    fun getAllArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllArticlesOldestFirst().map { it.mapToEntries() }

    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesByIds(ids.map { it.toString() }).map { it.mapToEntries() }

    suspend fun getAllArticlesForFeed(feedId: Long): Flow<List<Entry>> {
        val feed = feedDao.getFeedById(feedId) ?: throw IllegalStateException("Feed not found")
        return articleDao.getAllArticlesForFeed(feedId).map { articles ->
            articles.map { it.toEntry(feed) }
        }
    }

    suspend fun refreshArticles() {
        val limit = ENTRIES_BATCH_LIMIT
        val fetchedArticles = mutableListOf<ArticleEntity>()
        val feedsMap = mutableMapOf<Long, FeedEntity>()
        val syncStartTime = System.currentTimeMillis()

        val useIncrementalSync = shouldUseIncrementalSync(syncStartTime)
        syncPerformanceLogger.logSyncMode(useIncrementalSync, getLastSyncTime().takeIf { it > 0 })

        var cursor: String? = null
        while (true) {
            val page = fetchArticleBatch(useIncrementalSync, limit, cursor)

            fetchedArticles += page.entries.map { it.toEntity() }
            page.entries.forEach { feedsMap[it.feed.id] = it.feed.toFeedEntity() }

            cursor = page.cursor
            if (cursor == null || page.entries.isEmpty()) break
        }

        api.getFeeds().forEach { feedsMap[it.id] = it.toFeedEntity() }

        syncPerformanceLogger.logBatchInfo(limit, fetchedArticles.size)

        syncPerformanceLogger.measureSyncTime("Database transaction") {
            db.withTransaction {
                feedDao.insertFeeds(feedsMap.values.toList())
                insertArticlesWithStatusPreservation(fetchedArticles)
            }
        }

        preferencesManager.setLastSyncTimestamp(syncStartTime)
    }

    private fun shouldUseIncrementalSync(syncStartTime: Long): Boolean {
        val lastSyncTimestamp = getLastSyncTime()
        return lastSyncTimestamp > 0 && (syncStartTime - lastSyncTimestamp) < java.time.Duration.ofHours(24).toMillis()
    }

    private fun getLastSyncTime(): Long = preferencesManager.getLastSyncTimestamp()

    private suspend fun fetchArticleBatch(useIncremental: Boolean, limit: Int, cursor: String?) =
        if (useIncremental) {
            val changedAfter = Instant.ofEpochMilli(getLastSyncTime())
            Log.d("ArticleRepository", "Using incremental sync since: $changedAfter")
            api.getUnreadEntriesChangedAfter(changedAfter, limit = limit, cursor = cursor)
        } else {
            Log.d("ArticleRepository", "Using full sync")
            api.getUnreadEntries(limit = limit, cursor = cursor)
        }

    private suspend fun insertArticlesWithStatusPreservation(fetchedArticles: List<ArticleEntity>) {
        val articleIds = fetchedArticles.map { it.id }
        val existingArticles = articleDao.getArticlesImmediate(articleIds).associateBy { it.id }
        val articlesToInsert = fetchedArticles.map { remote ->
            val existing = existingArticles[remote.id]
            if (existing?.status == ArticleStatus.READ && remote.status != ArticleStatus.READ) {
                remote.copy(status = ArticleStatus.READ)
            } else remote
        }
        articleDao.insertArticles(articlesToInsert)
    }

    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) {
        articleDao.updateStatusForIds(articleIds, newStatus)
        try {
            api.updateEntriesStatus(articleIds.map { it.toLong() }, newStatus)
        } catch (e: Exception) {
            Log.w("ArticleRepository", "Failed to push bulk status update; will retry on next sync: ${e.message}")
        }
    }

    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    suspend fun getLocalUnreadArticles(): List<Entry> =
        articleDao.getArticlesByStatus(ArticleStatus.UNREAD).mapToEntries()

    suspend fun getFeed(feedId: Long): Feed? {
        val entity = feedDao.getFeedById(feedId) ?: return null
        return entity.toFeed()
    }

    private suspend fun List<ArticleEntity>.mapToEntries(): List<Entry> = map { article ->
        val feed = feedDao.getFeedById(article.feedId) ?: throw IllegalStateException("Feed not found")
        article.toEntry(feed)
    }
}

private fun ArticleEntity.toEntry(feedEntity: FeedEntity): Entry = Entry(
    id = id.toLong(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feed = feedEntity.toFeed(),
    readingTime = readingTime,
    enclosures = enclosures,
    status = status ?: ArticleStatus.UNREAD
)

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

private fun Entry.toEntity(): ArticleEntity = ArticleEntity(
    id = id.toString(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feedId = feed.id,
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)

private fun Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)
