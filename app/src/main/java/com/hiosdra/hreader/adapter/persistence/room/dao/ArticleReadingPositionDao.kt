package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition

@Dao
interface ArticleReadingPositionDao {
    @Query("SELECT * FROM article_reading_positions WHERE articleId IN (:articleIds)")
    suspend fun getForArticles(articleIds: List<String>): List<ArticleReadingPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ArticleReadingPosition)

    @Query("DELETE FROM article_reading_positions WHERE articleId = :articleId")
    suspend fun deleteForArticle(articleId: String)

    @Query("DELETE FROM article_reading_positions")
    suspend fun clearAll()
}
