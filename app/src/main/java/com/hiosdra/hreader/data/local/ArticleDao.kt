package com.hiosdra.hreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt ASC")
    fun getAllArticlesOldestFirst(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    @Query("DELETE FROM articles WHERE id = :articleId")
    suspend fun deleteById(articleId: String)

    @Query("UPDATE articles SET status = :status WHERE id = :articleId")
    suspend fun updateStatus(articleId: String, status: String)
}
