package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiOverview

@Dao
interface ArticleAiOverviewDao {
    @Query(
        "SELECT * FROM article_ai_overviews WHERE entryId = :entryId " +
            "AND modelId = :modelId AND contentHash = :contentHash"
    )
    suspend fun get(
        entryId: Long,
        modelId: String,
        contentHash: String
    ): ArticleAiOverview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(overview: ArticleAiOverview)

    @Query("DELETE FROM article_ai_overviews")
    suspend fun clearAll()

    @Query(
        "SELECT DISTINCT o.entryId FROM article_ai_overviews o " +
            "LEFT JOIN articles a ON a.id = CAST(o.entryId AS TEXT) " +
            "WHERE a.id IS NULL ORDER BY o.entryId ASC LIMIT :limit"
    )
    suspend fun getOrphanedEntryIds(limit: Int): List<Long>

    @Query("DELETE FROM article_ai_overviews WHERE entryId IN (:entryIds)")
    suspend fun deleteForEntries(entryIds: List<Long>)
}
