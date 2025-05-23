package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleContent
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleContent(articleContent: ArticleContent)

    @Query("SELECT * FROM article_contents WHERE entryId = :entryId")
    suspend fun getArticleContent(entryId: Long): ArticleContent?

    @Query("SELECT * FROM article_contents WHERE entryId = :entryId")
    fun getArticleContentFlow(entryId: Long): Flow<ArticleContent?>

    @Query("DELETE FROM article_contents WHERE entryId = :entryId")
    suspend fun deleteArticleContent(entryId: Long)

    @Query("SELECT COUNT(*) FROM article_contents")
    suspend fun getArticleContentCount(): Int

    @Query("SELECT * FROM article_contents ORDER BY fetchedAt DESC")
    suspend fun getAllArticleContents(): List<ArticleContent>
}
