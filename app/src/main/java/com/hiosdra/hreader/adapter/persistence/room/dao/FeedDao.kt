package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<FeedEntity>)

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    suspend fun getFeedById(feedId: Long): FeedEntity?

    @Query("SELECT * FROM feeds")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllFeedsImmediate(): List<FeedEntity>

    @Query("SELECT id FROM feeds")
    suspend fun getAllIds(): List<Long>

    @Query("UPDATE feeds SET title = :title WHERE id = :feedId")
    suspend fun updateTitle(feedId: Long, title: String)

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteById(feedId: Long)

    @Query("DELETE FROM feeds")
    suspend fun clearAll()
}
