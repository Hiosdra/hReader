package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

@Dao
interface ArticleMutationDao {
    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :markedAt " +
            "WHERE (status IS NULL OR status != :readStatus) " +
            "AND (:feedId IS NULL OR feedId = :feedId)"
    )
    suspend fun markUnreadAsRead(
        feedId: Long?,
        markedAt: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :markedAt " +
            "WHERE (status IS NULL OR status != :readStatus) " +
            "AND (:feedId IS NULL OR feedId = :feedId) " +
            "AND (publishedAt < :publishedAt OR (publishedAt = :publishedAt AND id <= :articleId))"
    )
    suspend fun markUnreadAsReadUpTo(
        feedId: Long?,
        articleId: String,
        publishedAt: Instant,
        markedAt: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query(
        "UPDATE articles SET status = :unreadStatus, pendingSync = 1, readAt = NULL " +
            "WHERE status = :readStatus AND readAt = :markedAt " +
            "AND (:feedId IS NULL OR feedId = :feedId)"
    )
    suspend fun undoBulkRead(
        feedId: Long?,
        markedAt: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ,
        unreadStatus: ArticleStatus = ArticleStatus.UNREAD
    ): Int

    @Query(
        "UPDATE articles SET status = :status, pendingSync = 1, " +
            "readAt = CASE WHEN :readAt IS NULL THEN NULL ELSE COALESCE(readAt, :readAt) END " +
            "WHERE id IN (:ids)"
    )
    suspend fun updateStatusForIds(ids: List<String>, status: ArticleStatus, readAt: Instant?)

}
