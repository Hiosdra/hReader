package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import androidx.room.withTransaction
import com.hiosdra.hreader.adapter.persistence.room.AppDatabase
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FullSyncSeenDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.FullSyncSeenEntity
import com.hiosdra.hreader.core.application.observability.ArticleSyncStats
import com.hiosdra.hreader.core.application.port.out.ArticleImageStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import kotlinx.coroutines.CancellationException

private const val DELETE_CHUNK = 500
private const val TAG = "ArticleSyncPersistence"

internal class ArticleSyncPersistence(
    private val articleRecordDao: ArticleRecordDao,
    private val articleContentDao: ArticleContentDao,
    private val feedDao: FeedDao,
    private val fullSyncSeenDao: FullSyncSeenDao,
    private val db: AppDatabase,
    private val imageStore: ArticleImageStore,
    private val credibilityStore: CredibilityStore
) {
    suspend fun clearFullSyncSeenState() = fullSyncSeenDao.deleteAll()

    suspend fun persistPage(entries: List<Entry>, fullSyncRunId: String?): ArticleSyncStats {
        val feeds = entries.associate { it.feed.id to it.feed.toArticleFeedEntity() }.values.toList()
        val articles = entries.map { it.toEntity() }
        val result = db.withTransaction {
            feedDao.insertFeeds(feeds.preservingFeedSettings())
            insertArticlesPreservingPendingStatus(articles).also { persistenceResult ->
                if (fullSyncRunId != null) {
                    fullSyncSeenDao.insertAll(
                        entries.map { entry ->
                            FullSyncSeenEntity(fullSyncRunId, entry.id.toString())
                        }
                    )
                }
                persistenceResult.invalidatedEntryIds
                    .chunked(DELETE_CHUNK)
                    .forEach { entryIds -> articleContentDao.deleteArticlesContent(entryIds) }
            }
        }
        for (entryId in result.invalidatedEntryIds) {
            try {
                imageStore.invalidateArticleImages(entryId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not invalidate cached assets for entry $entryId: ${error.message}")
            }
        }
        if (result.invalidatedEntryIds.isNotEmpty()) {
            credibilityStore.invalidateForEntries(result.invalidatedEntryIds)
        }
        return result.stats
    }

    suspend fun persistBacklogPage(entries: List<Entry>) {
        val now = java.time.Instant.now()
        val feeds = entries.associate { it.feed.id to it.feed.toArticleFeedEntity() }.values.toList()
        val articles = entries.map { entry ->
            entry.toEntity().copy(
                backlogFetchedAt = now,
                readAt = now.takeIf { entry.status == ArticleStatus.READ }
            )
        }
        db.withTransaction {
            feedDao.insertFeeds(feeds.preservingFeedSettings())
            articleRecordDao.insertArticles(articles)
        }
    }

    suspend fun reconcileFeeds(fetchedFeeds: List<FeedEntity>): Set<Long> {
        db.withTransaction {
            val incoming = fetchedFeeds.preservingFeedSettings()
            val incomingIds = incoming.mapTo(hashSetOf()) { it.id }
            val staleIds = feedDao.getAllIds().filterNot(incomingIds::contains)
            if (incoming.isNotEmpty()) feedDao.insertFeeds(incoming)
            staleIds.chunked(DELETE_CHUNK).forEach { feedIds ->
                articleRecordDao.deleteByFeedIds(feedIds)
                feedDao.deleteByIds(feedIds)
            }
        }
        return fetchedFeeds.mapTo(hashSetOf()) { it.id }
    }

    suspend fun finishFullSync(runId: String) {
        val stale = fullSyncSeenDao.getSyncedUnreadIdsMissingFrom(runId)
        if (stale.isNotEmpty()) {
            Log.d(TAG, "Dropping ${stale.size} locally unread articles the backend no longer returns")
            stale.chunked(DELETE_CHUNK).forEach { articleRecordDao.deleteByIds(it) }
        }
        fullSyncSeenDao.deleteRun(runId)
    }

    private suspend fun insertArticlesPreservingPendingStatus(
        fetchedArticles: List<ArticleEntity>
    ): PagePersistenceResult {
        val existingArticles = articleRecordDao
            .getArticlesImmediate(fetchedArticles.map { it.id })
            .associateBy { it.id }
        val now = java.time.Instant.now()
        val reconciled = fetchedArticles.map { fetched ->
            fetched to fetched.reconciledWith(existingArticles[fetched.id], now)
        }
        val changed = reconciled.filter { (fetched, merged) -> existingArticles[fetched.id] != merged }
        val invalidatedEntryIds = reconciled.mapNotNull { (fetched, _) ->
            val local = existingArticles[fetched.id] ?: return@mapNotNull null
            fetched.id.toLongOrNull()?.takeIf {
                local.url != fetched.url ||
                    local.content != fetched.content ||
                    local.enclosures != fetched.enclosures ||
                    local.leadImageUrl != fetched.leadImageUrl ||
                    local.title != fetched.title ||
                    local.author != fetched.author ||
                    local.publishedAt != fetched.publishedAt
            }
        }
        if (changed.isNotEmpty()) articleRecordDao.insertArticles(changed.map { it.second })
        val inserted = changed.count { (fetched, _) -> existingArticles[fetched.id] == null }
        return PagePersistenceResult(
            stats = ArticleSyncStats(
                fetched = fetchedArticles.size,
                unchanged = fetchedArticles.size - changed.size,
                inserted = inserted,
                updated = changed.size - inserted
            ),
            invalidatedEntryIds = invalidatedEntryIds
        )
    }

    private suspend fun List<FeedEntity>.preservingFeedSettings(): List<FeedEntity> {
        val existingSettings = feedDao.getAllFeedsImmediate().associateBy { it.id }
        return map { feed ->
            val existing = existingSettings[feed.id]
            feed.copy(
                preloadAiOverview = existing?.preloadAiOverview ?: feed.preloadAiOverview,
                autoMarkRead = existing?.autoMarkRead ?: feed.autoMarkRead
            )
        }
    }

    private data class PagePersistenceResult(
        val stats: ArticleSyncStats,
        val invalidatedEntryIds: List<Long>
    )
}
