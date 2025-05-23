package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleContent

@Dao
interface ArticleContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleContent(articleContent: ArticleContent)

    @Query("SELECT * FROM article_contents WHERE entryId = :entryId")
    suspend fun getArticleContent(entryId: Long): ArticleContent?

    @Query("DELETE FROM article_contents WHERE entryId IN (:entryIds)")
    suspend fun deleteArticlesContent(entryIds: List<Long>)

    @Query("SELECT * FROM article_contents ORDER BY fetchedAt DESC")
    suspend fun getAllArticleContents(): List<ArticleContent>
}
