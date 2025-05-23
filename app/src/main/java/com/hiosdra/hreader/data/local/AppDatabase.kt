package com.hiosdra.hreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.entity.ArticleContent

@Database(
    entities = [
        ArticleEntity::class,
        FeedEntity::class,
        ArticleContent::class
    ],
    version = 2
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun feedDao(): FeedDao
    abstract fun articleContentDao(): ArticleContentDao
}
