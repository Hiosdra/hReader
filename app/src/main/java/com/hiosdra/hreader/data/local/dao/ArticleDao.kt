package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.PendingStatus
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.model.ArticleStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt ASC")
    fun getAllArticlesOldestFirst(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    /**
     * The only way to change a status: it always queues the change for the backend. [readAt] is
     * the moment the article was read, or null when it is being marked unread again. An article
     * that was already read keeps its original read time — "mark all as read" sweeps over entries
     * that are already read, and restamping them would keep pushing back their retention.
     */
    @Query(
        "UPDATE articles SET status = :status, pendingSync = 1, " +
            "readAt = CASE WHEN :readAt IS NULL THEN NULL ELSE COALESCE(readAt, :readAt) END " +
            "WHERE id IN (:ids)"
    )
    suspend fun updateStatusForIds(ids: List<String>, status: ArticleStatus, readAt: Instant?)

    /**
     * Dequeues only rows still holding the status that was pushed. If the user flipped the status
     * again while the request was in flight, the newer value stays queued instead of being lost.
     */
    @Query("UPDATE articles SET pendingSync = 0 WHERE id IN (:ids) AND status = :pushedStatus")
    suspend fun clearPendingSync(ids: List<String>, pushedStatus: ArticleStatus)

    @Query("SELECT id, status FROM articles WHERE pendingSync = 1")
    suspend fun getPendingStatuses(): List<PendingStatus>

    @Query("SELECT id FROM articles WHERE status != :readStatus AND pendingSync = 0")
    suspend fun getSyncedUnreadIds(readStatus: ArticleStatus = ArticleStatus.READ): List<String>

    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query(
        "DELETE FROM articles WHERE status = :readStatus AND pendingSync = 0 " +
            "AND readAt IS NOT NULL AND readAt < :readBefore"
    )
    suspend fun deleteArticlesReadBefore(
        readBefore: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query("SELECT * FROM articles WHERE id IN (:ids) ORDER BY publishedAt ASC")
    fun getArticlesByIds(ids: List<String>): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt ASC")
    fun getAllArticlesForFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT id, url, enclosures FROM articles WHERE status != :readStatus")
    suspend fun getPrefetchTargets(readStatus: ArticleStatus = ArticleStatus.READ): List<PrefetchTarget>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getArticlesImmediate(ids: List<String>): List<ArticleEntity>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE status != :readStatus")
    fun observeUnreadCount(readStatus: ArticleStatus = ArticleStatus.READ): Flow<Int>
}
