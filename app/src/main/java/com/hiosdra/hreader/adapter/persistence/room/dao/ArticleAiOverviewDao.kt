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

    @Query("SELECT entryId FROM article_ai_overviews")
    suspend fun getAllEntryIds(): List<Long>

    @Query("DELETE FROM article_ai_overviews WHERE entryId IN (:entryIds)")
    suspend fun deleteForEntries(entryIds: List<Long>)
}
