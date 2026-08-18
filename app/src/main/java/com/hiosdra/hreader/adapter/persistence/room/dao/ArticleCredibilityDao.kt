package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleCredibility

@Dao
interface ArticleCredibilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credibility: ArticleCredibility)

    @Query("SELECT * FROM article_credibility WHERE entryId = :entryId AND modelId = :modelId")
    suspend fun getForEntry(entryId: Long, modelId: String): ArticleCredibility?

    @Query("SELECT * FROM article_credibility WHERE entryId IN (:entryIds) AND modelId = :modelId")
    suspend fun getForEntries(entryIds: List<Long>, modelId: String): List<ArticleCredibility>

    @Query("DELETE FROM article_credibility")
    suspend fun clearAll()

    @Query("SELECT DISTINCT entryId FROM article_credibility")
    suspend fun getAllEntryIds(): List<Long>

    @Query("DELETE FROM article_credibility WHERE entryId IN (:entryIds)")
    suspend fun deleteAll(entryIds: List<Long>)
}
