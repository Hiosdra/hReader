package com.hiosdra.hreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<FeedEntity>)

    @Query("SELECT * FROM feeds WHERE id = :feedId")
    suspend fun getFeedById(feedId: Long): FeedEntity?

    @Query("SELECT * FROM feeds")
    fun getAllFeeds(): Flow<List<FeedEntity>>
}
