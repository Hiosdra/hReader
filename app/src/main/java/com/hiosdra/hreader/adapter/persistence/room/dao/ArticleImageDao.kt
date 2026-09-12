package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageFile
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageManifest
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleImage(articleImage: ArticleImage)

    @Query("SELECT * FROM article_images WHERE entryId = :entryId")
    suspend fun getImagesForArticle(entryId: Long): List<ArticleImage>

    @Query("SELECT * FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun getImagesForArticles(entryIds: List<Long>): List<ArticleImage>

    @Query(
        "SELECT id, localFilePath FROM article_images " +
            "WHERE id > :afterId ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getImageFilesAfterId(afterId: String, limit: Int): List<ArticleImageFile>

    @Query("DELETE FROM article_images WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM article_images WHERE entryId = :entryId")
    fun observeImagesForArticle(entryId: Long): Flow<List<ArticleImage>>

    @Query("SELECT * FROM article_images WHERE entryId = :entryId AND originalUrl = :originalUrl LIMIT 1")
    suspend fun getImageForArticleByUrl(entryId: Long, originalUrl: String): ArticleImage?

    @Query(
        "SELECT i.id, i.localFilePath FROM article_images i " +
            "LEFT JOIN articles a ON a.id = CAST(i.entryId AS TEXT) " +
            "WHERE a.id IS NULL ORDER BY i.id ASC LIMIT :limit"
    )
    suspend fun getOrphanedImageFiles(limit: Int): List<ArticleImageFile>

    @Query(
        "SELECT DISTINCT m.entryId FROM article_image_manifest m " +
            "LEFT JOIN articles a ON a.id = CAST(m.entryId AS TEXT) " +
            "WHERE a.id IS NULL ORDER BY m.entryId ASC LIMIT :limit"
    )
    suspend fun getOrphanedExpectedEntryIds(limit: Int): List<Long>

    @Query("SELECT localFilePath FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun getImagePathsForArticles(entryIds: List<Long>): List<String>

    @Query("DELETE FROM article_images")
    suspend fun clearAll()

    @Query("DELETE FROM article_images WHERE entryId IN (:entryIds)")
    suspend fun deleteImagesForArticles(entryIds: List<Long>)

    @Delete
    suspend fun deleteArticleImage(articleImage: ArticleImage)

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
            "OR a.backlogFetchedAt IS NOT NULL"
    )
    fun observeOfflineExpectedImageCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM article_image_manifest m INNER JOIN article_images i " +
            "ON i.entryId = m.entryId AND i.originalUrl = m.originalUrl " +
            "INNER JOIN articles a ON a.id = CAST(m.entryId AS TEXT) " +
            "WHERE (a.status IS NULL OR a.status != 'READ') " +
            "OR a.backlogFetchedAt IS NOT NULL"
    )
    fun observeOfflineStoredExpectedImageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM article_images")
    fun observeImageCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM article_images")
    fun observeImageBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM article_images")
    suspend fun getTotalImageBytes(): Long

    /** Oldest first: what the cache budget evicts when it has to make room. */
    @Query("SELECT * FROM article_images ORDER BY downloadedAt ASC LIMIT :limit")
    suspend fun getImagesOldestFirst(limit: Int): List<ArticleImage>
}
