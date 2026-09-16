package com.hiosdra.hreader.adapter.persistence.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleCredibilityDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleAiOverviewDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMaintenanceDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleMutationDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleQueryDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRetentionDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleStatsDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FullSyncSeenDao
import com.hiosdra.hreader.adapter.persistence.room.dao.PendingChangeDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleCredibility
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleFts
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImage
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleImageManifest
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleAiOverview
import com.hiosdra.hreader.adapter.persistence.room.entity.FeedEntity
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticlePageSnapshot
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition
import com.hiosdra.hreader.adapter.persistence.room.entity.FullSyncSeenEntity

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
        ArticleReadingPosition::class,
        FullSyncSeenEntity::class
    ],
    version = 23
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleQueryDao(): ArticleQueryDao
    abstract fun articleRecordDao(): ArticleRecordDao
    abstract fun articleStatsDao(): ArticleStatsDao
    abstract fun articleMutationDao(): ArticleMutationDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun articleMaintenanceDao(): ArticleMaintenanceDao
    abstract fun articleRetentionDao(): ArticleRetentionDao
    abstract fun feedDao(): FeedDao
    abstract fun articleContentDao(): ArticleContentDao
    abstract fun articleImageDao(): ArticleImageDao
    abstract fun articleCredibilityDao(): ArticleCredibilityDao
    abstract fun articleAiOverviewDao(): ArticleAiOverviewDao
    abstract fun articlePageSnapshotDao(): ArticlePageSnapshotDao
    abstract fun articleReadingPositionDao(): ArticleReadingPositionDao
    abstract fun fullSyncSeenDao(): FullSyncSeenDao
}
