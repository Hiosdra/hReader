package com.hiosdra.hreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.model.ArticleContentSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleContent(articleContent: ArticleContent)

    @Query("SELECT * FROM article_contents WHERE entryId = :entryId")
    suspend fun getArticleContent(entryId: Long): ArticleContent?

    @Query(
        "SELECT entryId FROM article_contents " +
            "WHERE source = :source"
    )
    suspend fun getContentEntryIds(source: ArticleContentSource): List<Long>

    @Query("DELETE FROM article_contents")
    suspend fun clearAll()

    @Query("DELETE FROM article_contents WHERE entryId IN (:entryIds)")
    suspend fun deleteArticlesContent(entryIds: List<Long>)

    /**
     * Which articles have their text stored. Orphan detection needs the ids and nothing else, and
     * every row here carries a full article body — reading them all to compare a number is how a
     * cache stocked for a long trip runs the worker out of memory.
     */
    @Query("SELECT entryId FROM article_contents")
    suspend fun getAllContentEntryIds(): List<Long>

    @Query("SELECT COUNT(*) FROM article_contents")
    fun observeContentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM article_contents WHERE source = :source")
    fun observeContentCount(source: ArticleContentSource): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM article_contents c INNER JOIN articles a " +
            "ON a.id = CAST(c.entryId AS TEXT) WHERE c.source = :source AND " +
            "((a.status IS NULL OR a.status != 'READ') OR " +
            "a.backlogFetchedAt IS NOT NULL OR a.starred = 1)"
    )
    fun observeOfflineContentCount(source: ArticleContentSource): Flow<Int>
}
