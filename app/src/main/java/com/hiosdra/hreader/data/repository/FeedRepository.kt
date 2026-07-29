package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.dao.FeedDao
import com.hiosdra.hreader.data.local.entity.FeedEntity
import com.hiosdra.hreader.data.model.DiscoveredFeed
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.FeedBackend
import kotlinx.coroutines.flow.first

class FeedRepository(
    private val backend: FeedBackend,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao
) {
    /**
     * The subscription list as last synced. Serving it from the cache is what lets the screen open
     * without a connection at all: it used to go straight to the backend and show an error.
     */
    suspend fun getCachedFeeds(): List<Feed> = feedDao.getAllFeeds().first().map { it.toFeed() }

    /** Unread counts from the cached articles, so the list still adds up while offline. */
    suspend fun getCachedUnreadCounts(): Map<Long, Int> =
        articleDao.observeUnreadCountsPerFeed().first().associate { it.feedId to it.unreadCount }

    suspend fun refreshFeeds(): List<Feed> {
        val feeds = backend.getFeeds()
        feedDao.insertFeeds(feeds.map { it.toFeedEntity() })
        return feeds
    }

    suspend fun getUnreadCounts(): Map<Long, Int> = backend.getUnreadCounts()
    suspend fun createFeed(url: String) = backend.createFeed(url)
    suspend fun discoverFeeds(url: String): List<DiscoveredFeed> = backend.discoverFeeds(url)
    suspend fun verifyConnection(): Int = backend.verifyConnection()
}

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

private fun Feed.toFeedEntity(): FeedEntity = FeedEntity(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)
