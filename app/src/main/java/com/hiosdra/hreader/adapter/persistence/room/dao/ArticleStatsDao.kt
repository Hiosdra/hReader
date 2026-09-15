package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedUnreadCount
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleStatsDao {
    @Query("SELECT COUNT(*) FROM articles")
    suspend fun countArticles(): Int

    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE status IS NULL OR status != :readStatus")
    fun observeUnreadCount(readStatus: ArticleStatus = ArticleStatus.READ): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE backlogFetchedAt IS NOT NULL")
    fun observeBacklogCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM articles WHERE (status IS NULL OR status != 'READ') " +
            "OR backlogFetchedAt IS NOT NULL"
    )
    fun observeOfflineTargetCount(): Flow<Int>

    @Query(
        "SELECT feedId, COUNT(*) AS unreadCount FROM articles " +
            "WHERE status IS NULL OR status != :readStatus GROUP BY feedId"
    )
    fun observeUnreadCountsPerFeed(readStatus: ArticleStatus = ArticleStatus.READ): Flow<List<FeedUnreadCount>>
}
