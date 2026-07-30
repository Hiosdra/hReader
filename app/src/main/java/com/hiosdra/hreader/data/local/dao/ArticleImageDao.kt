package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleImage
import kotlinx.coroutines.flow.Flow

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

    /** Which articles have images stored, for orphan detection without reading every row. */
    @Query("SELECT DISTINCT entryId FROM article_images")
    suspend fun getAllImageEntryIds(): List<Long>

    @Query("SELECT localFilePath FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun getImagePathsForArticles(entryIds: List<Long>): List<String>

    @Query("DELETE FROM article_images")
    suspend fun clearAll()

    @Query("DELETE FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun deleteImagesForArticles(entryIds: List<Long>)

    @Delete
    suspend fun deleteArticleImage(articleImage: ArticleImage)

    @Query("DELETE FROM article_images WHERE entryId = :entryId")
    suspend fun deleteImagesForArticle(entryId: Long)

    @Query("SELECT COUNT(*) FROM article_images")
    fun observeImageCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM article_images")
    fun observeImageBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM article_images")
    suspend fun getTotalImageBytes(): Long

    /** Oldest first: what the cache budget evicts when it has to make room. */
    @Query("SELECT * FROM article_images ORDER BY downloadedAt ASC")
    suspend fun getImagesOldestFirst(): List<ArticleImage>
}