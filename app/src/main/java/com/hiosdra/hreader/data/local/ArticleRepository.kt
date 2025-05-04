package com.hiosdra.hreader.data.local

import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.model.Feed
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import com.hiosdra.hreader.data.remote.UpdateEntriesStatusRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val api: MinifluxApiRepository
) {
    fun getAllArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllArticlesOldestFirst().map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId) ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }

    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesByIds(ids.map { it.toString() }).map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId) ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }

    fun getAllArticlesForFeed(feedId: Long): Flow<List<Entry>> {
        return articleDao.getAllArticlesForFeed(feedId).map { list ->
            list.map { article ->
                val feed = feedDao.getFeedById(article.feedId) ?: throw IllegalStateException("Feed not found")
                article.toEntry(feed)
            }
        }
    }

    suspend fun refreshArticles() {
        val limit = 50
        var offset = 0
        val allArticles = mutableListOf<ArticleEntity>()
        val allFeeds = mutableMapOf<Long, FeedEntity>()
        while (true) {
            val response = api.getEntries(limit = limit, offset = offset)
            val articles = response.entries.map { entry -> entry.toEntity() }
            val feeds = response.entries.map { entry ->
                val apiFeed = entry.feed
                FeedEntity(
                    id = apiFeed.id,
                    title = apiFeed.title,
                    siteUrl = apiFeed.siteUrl,
                    feedUrl = apiFeed.feedUrl,
                )
            }
            articles.forEach { allArticles.add(it) }
            feeds.forEach { allFeeds[it.id] = it }
            if (articles.size < limit) break
            offset += limit
        }
        feedDao.insertFeeds(allFeeds.values.toList())
        articleDao.clearAll()
        articleDao.insertArticles(allArticles)
    }

    suspend fun updateReadStatus(articleId: String, newStatus: String) {
        api.updateEntriesStatus(UpdateEntriesStatusRequest(listOf(articleId.toLong()), newStatus))
        articleDao.updateStatus(articleId, newStatus)
    }
}

private fun ArticleEntity.toEntry(feedEntity: FeedEntity): Entry = Entry(
    id = id.toLong(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feed = feedEntity.toFeed(),
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)

private fun FeedEntity.toFeed(): Feed = Feed(
    id = id,
    title = title,
    siteUrl = siteUrl,
    feedUrl = feedUrl
)

private fun Entry.toEntity(): ArticleEntity = ArticleEntity(
    id = id.toString(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feedId = feed.id,
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)
