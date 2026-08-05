package com.hiosdra.hreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleCredibilityDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.ArticleImageDao
import com.hiosdra.hreader.data.local.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.data.local.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.data.local.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.local.entity.ArticleCredibility
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.ArticleFts
import com.hiosdra.hreader.data.local.entity.ArticleImage
import com.hiosdra.hreader.data.local.entity.ArticleImageManifest
import com.hiosdra.hreader.data.local.entity.ArticleAiOverview
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.local.entity.ArticlePageSnapshot
import com.hiosdra.hreader.data.local.entity.ArticleReadingPosition

@Database(
    entities = [
        ArticleEntity::class,
        ArticleFts::class,
        FeedEntity::class,
        ArticleContent::class,
        ArticleImage::class,
        ArticleImageManifest::class,
        ArticleCredibility::class,
        ArticleAiOverview::class,
        ArticlePageSnapshot::class,
        ArticleReadingPosition::class
    ],
    version = 15
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun feedDao(): FeedDao
    abstract fun articleContentDao(): ArticleContentDao
    abstract fun articleImageDao(): ArticleImageDao
    abstract fun articleCredibilityDao(): ArticleCredibilityDao
    abstract fun articleAiOverviewDao(): ArticleAiOverviewDao
    abstract fun articlePageSnapshotDao(): ArticlePageSnapshotDao
    abstract fun articleReadingPositionDao(): ArticleReadingPositionDao
}
