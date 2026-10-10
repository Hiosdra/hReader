package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiSummary

@Dao
interface ArticleAiSummaryDao {
    @Query(
        "SELECT * FROM article_ai_summaries WHERE entryId = :entryId " +
            "AND modelId = :modelId AND contentHash = :contentHash"
    )
    suspend fun get(entryId: Long, modelId: String, contentHash: String): ArticleAiSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: ArticleAiSummary)

    @Query("DELETE FROM article_ai_summaries")
    suspend fun clearAll()

    @Query("SELECT DISTINCT entryId FROM article_ai_summaries")
    suspend fun getAllEntryIds(): List<Long>

    @Query("DELETE FROM article_ai_summaries WHERE entryId IN (:entryIds)")
    suspend fun deleteForEntries(entryIds: List<Long>)
}
