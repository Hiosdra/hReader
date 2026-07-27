package com.hiosdra.hreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.ArticleImage
import com.hiosdra.hreader.data.local.entity.FeedEntity

@Database(
    entities = [
        ArticleEntity::class,
        FeedEntity::class,
        ArticleContent::class,
        ArticleImage::class
    ],
    version = 4
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun feedDao(): FeedDao
    abstract fun articleContentDao(): ArticleContentDao
    abstract fun articleImageDao(): ArticleImageDao
}
