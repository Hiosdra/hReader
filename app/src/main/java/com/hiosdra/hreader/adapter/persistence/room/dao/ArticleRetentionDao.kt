package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

@Dao
interface ArticleRetentionDao {
    @Query(
        "DELETE FROM articles WHERE status = :readStatus AND pendingSync = 0 " +
            "AND readAt IS NOT NULL AND readAt < :readBefore"
    )
    suspend fun deleteReadArticlesBefore(
        readBefore: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int
}
