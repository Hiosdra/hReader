package com.hiosdra.hreader.data.local.repository

import android.util.Log
import androidx.room.withTransaction
import com.hiosdra.hreader.data.local.AppDatabase
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.preferences.PreferencesManager
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import com.hiosdra.hreader.data.remote.dto.UpdateEntriesStatusRequest
import com.hiosdra.hreader.util.SyncPerformanceLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.format.DateTimeFormatter

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val api: MinifluxApiRepository,
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
        val limit = 200
        var offset = 0
        val fetchedArticles = mutableListOf<ArticleEntity>()
        val feedsMap = mutableMapOf<Long, FeedEntity>()
        val syncStartTime = System.currentTimeMillis()
        
        val useIncrementalSync = shouldUseIncrementalSync(syncStartTime)
        syncPerformanceLogger.logSyncMode(useIncrementalSync, getLastSyncTime().takeIf { it > 0 })
        
        while (true) {
            val response = fetchArticleBatch(useIncrementalSync, limit, offset)
            val articles = response.entries.map { it.toEntity() }
            val feeds = response.entries.map { entry -> entry.feed.toFeedEntity() }
            
            fetchedArticles += articles
            feeds.forEach { feedsMap[it.id] = it }
            
            if (articles.size < limit) break
            offset += limit
        }
        
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

    private suspend fun fetchArticleBatch(useIncremental: Boolean, limit: Int, offset: Int) =
        if (useIncremental) {
            val lastSyncIso = Instant.ofEpochMilli(getLastSyncTime())
                .atZone(java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT)
            Log.d("ArticleRepository", "Using incremental sync since: $lastSyncIso")
            api.getEntriesChangedAfter(lastSyncIso, limit = limit, offset = offset)
        } else {
            Log.d("ArticleRepository", "Using full sync")
            api.getEntries(limit = limit, offset = offset)
        }

    private suspend fun insertArticlesWithStatusPreservation(fetchedArticles: List<ArticleEntity>) {
        val articleIds = fetchedArticles.map { it.id }
        val existingArticles = articleDao.getArticlesImmediate(articleIds).associateBy { it.id }
        
        val articlesToInsert = fetchedArticles.map { remote ->
            val existing = existingArticles[remote.id]
            if (existing?.status == "read" && remote.status != "read") {
                remote.copy(status = "read")
            } else {
                remote
            }
        }
        articleDao.insertArticles(articlesToInsert)
    }

    suspend fun updateReadStatus(articleIds: List<String>, newStatus: String) {
        articleDao.updateStatusForIds(articleIds, newStatus)
        try {
            api.updateEntriesStatus(
                UpdateEntriesStatusRequest(
                    articleIds.map { it.toLong() },
                    newStatus
                )
            )
        } catch (e: Exception) {
            Log.w("ArticleRepository", "Failed to push bulk status update; will retry on next sync: ${e.message}")
        }
    }

    suspend fun updateReadStatus(articleId: String, newStatus: String) {
        updateReadStatus(listOf(articleId), newStatus)
    }

    suspend fun getLocalUnreadArticles(): List<Entry> =
        articleDao.getArticlesByStatus("unread").mapToEntries()

    fun getUnreadArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllUnreadArticlesOldestFirst().map { it.mapToEntries() }

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
    status = status
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

private fun com.hiosdra.hreader.data.model.Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)