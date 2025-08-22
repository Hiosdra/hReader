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
    private val preferencesManager: PreferencesManager
) {
    fun getAllArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllArticlesOldestFirst().map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId)
                    ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }

    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesByIds(ids.map { it.toString() }).map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId)
                    ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }

    suspend fun getAllArticlesForFeed(feedId: Long): Flow<List<Entry>> {
        val feed = feedDao.getFeedById(feedId) ?: throw IllegalStateException("Feed not found")
        return articleDao.getAllArticlesForFeed(feedId).map { list ->
            list.map { article ->
                article.toEntry(feed)
            }
        }
    }

    suspend fun refreshArticles() {
        val limit = 200
        var offset = 0
        val fetchedArticles = mutableListOf<ArticleEntity>()
        val feedsMap = mutableMapOf<Long, FeedEntity>()
        val syncStartTime = System.currentTimeMillis()
        
        // Try incremental sync first if we have a previous sync timestamp
        val lastSyncTimestamp = preferencesManager.getLastSyncTimestamp()
        val useIncrementalSync = lastSyncTimestamp > 0 && 
                                 (syncStartTime - lastSyncTimestamp) < 24 * 60 * 60 * 1000L // Last sync within 24h
        
        SyncPerformanceLogger.logSyncMode(useIncrementalSync, lastSyncTimestamp.takeIf { it > 0 })
        
        while (true) {
            val response = if (useIncrementalSync) {
                val lastSyncIso = Instant.ofEpochMilli(lastSyncTimestamp)
                    .atZone(java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)
                Log.d("ArticleRepository", "Using incremental sync since: $lastSyncIso")
                api.getEntriesChangedAfter(lastSyncIso, limit = limit, offset = offset)
            } else {
                Log.d("ArticleRepository", "Using full sync")
                api.getEntries(limit = limit, offset = offset)
            }
            
            val articles = response.entries.map { it.toEntity() }
            val feeds = response.entries.map { entry ->
                val f = entry.feed
                FeedEntity(
                    id = f.id,
                    title = f.title,
                    siteUrl = f.siteUrl,
                    feedUrl = f.feedUrl,
                )
            }
            fetchedArticles += articles
            feeds.forEach { feedsMap[it.id] = it }
            if (articles.size < limit) break
            offset += limit
        }
        
        SyncPerformanceLogger.logBatchInfo(limit, fetchedArticles.size)
        
        SyncPerformanceLogger.measureSyncTime("Database transaction") {
            db.withTransaction {
                feedDao.insertFeeds(feedsMap.values.toList())
                // Batch query for existing articles to optimize database access
                val articleIds = fetchedArticles.map { it.id }
                val existingArticles = articleDao.getArticlesImmediate(articleIds).associateBy { it.id }
                
                val toInsert = fetchedArticles.map { remote ->
                    val existing = existingArticles[remote.id]
                    if (existing?.status == "read" && remote.status != "read") remote.copy(status = "read") else remote
                }
                articleDao.insertArticles(toInsert)
            }
        }
        
        // Update last sync timestamp
        preferencesManager.setLastSyncTimestamp(syncStartTime)
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
        articleDao.updateStatus(articleId, newStatus)
        try {
            api.updateEntriesStatus(UpdateEntriesStatusRequest(listOf(articleId.toLong()), newStatus))
        } catch (e: Exception) {
            Log.w("ArticleRepository", "Failed to push status update for $articleId; keeping local optimistic state: ${e.message}")
        }
    }

    suspend fun getLocalUnreadArticles(): List<Entry> {
        val unread = articleDao.getArticlesByStatus("unread")
        return unread.map { entity ->
            val feed = feedDao.getFeedById(entity.feedId) ?: throw IllegalStateException("Feed not found")
            entity.toEntry(feed)
        }
    }

    fun getUnreadArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllUnreadArticlesOldestFirst().map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId)
                    ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }

    suspend fun getFeed(feedId: Long): Feed? {
        val entity = feedDao.getFeedById(feedId) ?: return null
        return entity.toFeed()
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
