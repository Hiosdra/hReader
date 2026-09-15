package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleImageDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleStatsDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.core.application.port.out.StorageDatabaseStats
import com.hiosdra.hreader.core.application.port.out.StorageDatabaseStatsStore

internal class RoomStorageDatabaseStatsStore(
    private val articleStatsDao: ArticleStatsDao,
    private val feedDao: FeedDao,
    private val articleContentDao: ArticleContentDao,
    private val articleReadingPositionDao: ArticleReadingPositionDao,
    private val articleImageDao: ArticleImageDao,
    private val articlePageSnapshotDao: ArticlePageSnapshotDao
) : StorageDatabaseStatsStore {
    override suspend fun getStats(): StorageDatabaseStats = StorageDatabaseStats(
        articleCount = articleStatsDao.countArticles(),
        feedCount = feedDao.countFeeds(),
        storedContentCount = articleContentDao.countContent(),
        readingPositionCount = articleReadingPositionDao.countPositions(),
        imageCount = articleImageDao.countImages(),
        pageCount = articlePageSnapshotDao.countPages()
    )
}
