package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleImage

@Dao
interface ArticleImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleImage(articleImage: ArticleImage)

    @Query("SELECT * FROM article_images WHERE entryId = :entryId")
    suspend fun getImagesForArticle(entryId: Long): List<ArticleImage>

    @Query("SELECT * FROM article_images WHERE originalUrl = :originalUrl LIMIT 1")
    suspend fun getImageByUrl(originalUrl: String): ArticleImage?

    @Query("SELECT * FROM article_images WHERE entryId = :entryId AND originalUrl = :originalUrl LIMIT 1")
    suspend fun getImageForArticleByUrl(entryId: Long, originalUrl: String): ArticleImage?

    @Query("SELECT * FROM article_images")
    suspend fun getAllArticleImages(): List<ArticleImage>

    @Query("DELETE FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun deleteImagesForArticles(entryIds: List<Long>)

    @Delete
    suspend fun deleteArticleImage(articleImage: ArticleImage)

    @Query("DELETE FROM article_images WHERE entryId = :entryId")
    suspend fun deleteImagesForArticle(entryId: Long)
}