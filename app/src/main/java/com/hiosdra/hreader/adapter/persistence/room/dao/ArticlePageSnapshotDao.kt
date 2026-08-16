package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticlePageSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ArticlePageSnapshot)

    @Query("SELECT * FROM article_page_snapshots WHERE entryId = :entryId")
    suspend fun get(entryId: Long): ArticlePageSnapshot?

    @Query("SELECT * FROM article_page_snapshots")
    suspend fun getAll(): List<ArticlePageSnapshot>

    @Query("DELETE FROM article_page_snapshots WHERE entryId IN (:entryIds)")
    suspend fun deleteForEntries(entryIds: List<Long>)

    @Query("DELETE FROM article_page_snapshots")
    suspend fun clearAll()

    @Query(
        "SELECT COUNT(*) FROM article_page_snapshots s INNER JOIN articles a " +
            "ON a.id = s.entryId AND s.originalUrl = a.url " +
            "WHERE s.isComplete = 1 AND " +
            "((a.status IS NULL OR a.status != 'READ') OR " +
            "a.backlogFetchedAt IS NOT NULL OR a.starred = 1)"
    )
    fun observeOfflineCompleteCount(): Flow<Int>
}
