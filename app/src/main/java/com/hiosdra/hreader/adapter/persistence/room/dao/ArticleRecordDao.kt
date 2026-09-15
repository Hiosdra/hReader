package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.core.domain.model.ArticleStatus

@Dao
interface ArticleRecordDao {
    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getArticlesImmediate(ids: List<String>): List<ArticleEntity>

    @Query("SELECT id FROM articles")
    suspend fun getAllIds(): List<String>

    @Query("SELECT id FROM articles WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @Query(
        "SELECT id FROM articles WHERE (status IS NULL OR status != :readStatus) " +
            "AND pendingSync = 0 AND backlogFetchedAt IS NULL"
    )
    suspend fun getSyncedUnreadIds(readStatus: ArticleStatus = ArticleStatus.READ): List<String>

    @Query("UPDATE articles SET fullContent = :content WHERE id = :id")
    suspend fun setFullContent(id: String, content: String)

    @Upsert
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    @Query("DELETE FROM articles_fts")
    suspend fun clearSearchIndex()

    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: Long)

    @Query("DELETE FROM articles WHERE feedId IN (:feedIds)")
    suspend fun deleteByFeedIds(feedIds: List<Long>)
}
