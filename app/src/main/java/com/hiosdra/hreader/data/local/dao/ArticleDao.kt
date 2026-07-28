package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.model.ArticleStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt ASC")
    fun getAllArticlesOldestFirst(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    @Query("UPDATE articles SET status = :status WHERE id = :articleId")
    suspend fun updateStatus(articleId: String, status: ArticleStatus)

    @Query("UPDATE articles SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusForIds(ids: List<String>, status: ArticleStatus)

    @Query("SELECT * FROM articles WHERE id IN (:ids) ORDER BY publishedAt ASC")
    fun getArticlesByIds(ids: List<String>): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt ASC")
    fun getAllArticlesForFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status")
    suspend fun getArticlesByStatus(status: ArticleStatus): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getArticlesImmediate(ids: List<String>): List<ArticleEntity>
}
