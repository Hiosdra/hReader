package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

@Dao
interface ArticleMutationDao {
    @Query(
        "UPDATE articles SET status = :status, pendingSync = 1, " +
            "readAt = CASE WHEN :readAt IS NULL THEN NULL ELSE COALESCE(readAt, :readAt) END " +
            "WHERE id IN (:ids)"
    )
    suspend fun updateStatusForIds(ids: List<String>, status: ArticleStatus, readAt: Instant?)

    @Query("SELECT publishedAt FROM articles WHERE id = :articleId")
    suspend fun getPublishedAt(articleId: String): Instant?

    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :readAt " +
            "WHERE (:feedId IS NULL OR feedId = :feedId) " +
            "AND (status IS NULL OR status != :readStatus)"
    )
    suspend fun markScopeRead(
        feedId: Long?,
        readStatus: ArticleStatus,
        readAt: Instant
    ): Int

    @Query(
        "UPDATE articles SET status = :unreadStatus, pendingSync = 1, readAt = NULL " +
            "WHERE (:feedId IS NULL OR feedId = :feedId) " +
            "AND status = :readStatus"
    )
    suspend fun markScopeUnread(
        feedId: Long?,
        readStatus: ArticleStatus,
        unreadStatus: ArticleStatus
    ): Int

    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :readAt " +
            "WHERE id IN (" +
            "SELECT a.id FROM articles a " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (a.status IS NULL OR a.status != :readStatus) " +
            "AND (a.publishedAt < :publishedAt OR " +
            "(a.publishedAt = :publishedAt AND a.id <= :articleId))" +
            ")"
    )
    suspend fun markScopeReadThrough(
        feedId: Long?,
        readStatus: ArticleStatus,
        readAt: Instant,
        articleId: String,
        publishedAt: Instant
    ): Int

    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :readAt " +
            "WHERE id IN (" +
            "SELECT a.id FROM articles a " +
            "LEFT JOIN feeds f ON f.id = a.feedId " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (a.status IS NULL OR a.status != :readStatus) " +
            "AND (a.rowid IN (SELECT rowid FROM articles_fts WHERE articles_fts MATCH :ftsQuery) " +
            "OR LOWER(f.title) LIKE :titleQuery)" +
            ")"
    )
    suspend fun markSearchRead(
        feedId: Long?,
        readStatus: ArticleStatus,
        readAt: Instant,
        ftsQuery: String,
        titleQuery: String
    ): Int

    @Query(
        "UPDATE articles SET status = :unreadStatus, pendingSync = 1, readAt = NULL " +
            "WHERE id IN (" +
            "SELECT a.id FROM articles a " +
            "LEFT JOIN feeds f ON f.id = a.feedId " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND a.status = :readStatus " +
            "AND (a.rowid IN (SELECT rowid FROM articles_fts WHERE articles_fts MATCH :ftsQuery) " +
            "OR LOWER(f.title) LIKE :titleQuery)" +
            ")"
    )
    suspend fun markSearchUnread(
        feedId: Long?,
        readStatus: ArticleStatus,
        unreadStatus: ArticleStatus,
        ftsQuery: String,
        titleQuery: String
    ): Int

    @Query(
        "UPDATE articles SET status = :readStatus, pendingSync = 1, readAt = :readAt " +
            "WHERE id IN (" +
            "SELECT a.id FROM articles a " +
            "LEFT JOIN feeds f ON f.id = a.feedId " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (a.status IS NULL OR a.status != :readStatus) " +
            "AND (a.publishedAt < :publishedAt OR " +
            "(a.publishedAt = :publishedAt AND a.id <= :articleId)) " +
            "AND (a.rowid IN (SELECT rowid FROM articles_fts WHERE articles_fts MATCH :ftsQuery) " +
            "OR LOWER(f.title) LIKE :titleQuery)" +
            ")"
    )
    suspend fun markSearchReadThrough(
        feedId: Long?,
        readStatus: ArticleStatus,
        readAt: Instant,
        articleId: String,
        publishedAt: Instant,
        ftsQuery: String,
        titleQuery: String
    ): Int

    @Query(
        "UPDATE articles SET status = :unreadStatus, pendingSync = 1, readAt = NULL " +
            "WHERE status = :readStatus AND readAt = :readAt"
    )
    suspend fun undoReadStatus(
        readAt: Instant,
        readStatus: ArticleStatus,
        unreadStatus: ArticleStatus
    ): Int
}
