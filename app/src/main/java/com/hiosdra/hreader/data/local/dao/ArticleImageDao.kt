package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleImage
import com.hiosdra.hreader.data.local.entity.ArticleImageManifest
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpectedImages(images: List<ArticleImageManifest>)

    @Query("DELETE FROM article_image_manifest WHERE entryId = :entryId")
    suspend fun deleteExpectedImagesForArticle(entryId: Long)

    @Query("DELETE FROM article_image_manifest WHERE entryId IN (:entryIds)")
    suspend fun deleteExpectedImagesForArticles(entryIds: List<Long>)

    @Query("DELETE FROM article_image_manifest")
    suspend fun clearExpectedImages()

    @Query(
        "SELECT COUNT(*) FROM article_image_manifest m INNER JOIN articles a " +
            "ON a.id = CAST(m.entryId AS TEXT) WHERE (a.status IS NULL OR a.status != 'READ') " +
            "OR a.backlogFetchedAt IS NOT NULL OR a.starred = 1"
    )
    fun observeOfflineExpectedImageCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM article_image_manifest m INNER JOIN article_images i " +
            "ON i.entryId = m.entryId AND i.originalUrl = m.originalUrl " +
            "INNER JOIN articles a ON a.id = CAST(m.entryId AS TEXT) " +
            "WHERE (a.status IS NULL OR a.status != 'READ') " +
            "OR a.backlogFetchedAt IS NOT NULL OR a.starred = 1"
    )
    fun observeOfflineStoredExpectedImageCount(): Flow<Int>

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
