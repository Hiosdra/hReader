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

    @Query(
        "SELECT id FROM articles WHERE id IN (:ids) AND status = :readStatus " +
            "AND readAt IS NOT NULL AND readAt <= :readBefore"
    )
    suspend fun getIdsReadNoLaterThan(
        ids: List<String>,
        readBefore: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>
}
