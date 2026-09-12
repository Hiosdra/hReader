package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleContent(articleContent: ArticleContent)

    @Query("SELECT * FROM article_contents WHERE entryId = :entryId")
    suspend fun getArticleContent(entryId: Long): ArticleContent?

    @Query(
        "SELECT entryId FROM article_contents " +
            "WHERE entryId IN (:entryIds) AND source = :source"
    )
    suspend fun getContentEntryIds(
        entryIds: List<Long>,
        source: ArticleContentSource
    ): List<Long>

    @Query(
        "SELECT entryId FROM article_contents " +
            "WHERE entryId IN (:entryIds) AND source = :source AND allImagesPrepared = 1"
    )
    suspend fun getFullyImagePreparedEntryIds(
        entryIds: List<Long>,
        source: ArticleContentSource = ArticleContentSource.FULL
    ): List<Long>

    @Query(
        "UPDATE article_contents SET allImagesPrepared = 1 " +
            "WHERE entryId = :entryId AND source = :source"
    )
    suspend fun markAllImagesPrepared(
        entryId: Long,
        source: ArticleContentSource = ArticleContentSource.FULL
    )

    @Query("DELETE FROM article_contents")
    suspend fun clearAll()

    @Query("DELETE FROM article_contents WHERE entryId IN (:entryIds)")
    suspend fun deleteArticlesContent(entryIds: List<Long>)

    @Query(
        "SELECT c.entryId FROM article_contents c " +
            "LEFT JOIN articles a ON a.id = CAST(c.entryId AS TEXT) " +
            "WHERE a.id IS NULL ORDER BY c.entryId ASC LIMIT :limit"
    )
    suspend fun getOrphanedEntryIds(limit: Int): List<Long>

    @Query("SELECT COUNT(*) FROM article_contents")
    fun observeContentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM article_contents WHERE source = :source")
    fun observeContentCount(source: ArticleContentSource): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM article_contents c INNER JOIN articles a " +
            "ON a.id = CAST(c.entryId AS TEXT) WHERE c.source = :source AND " +
            "((a.status IS NULL OR a.status != 'READ') OR " +
            "a.backlogFetchedAt IS NOT NULL)"
    )
    fun observeOfflineContentCount(source: ArticleContentSource): Flow<Int>
}
