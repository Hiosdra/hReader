package com.hiosdra.hreader.data.local

import com.hiosdra.hreader.data.model.Entry
import com.hiosdra.hreader.data.remote.MinifluxApiService
import com.hiosdra.hreader.data.remote.UpdateEntriesStatusRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val api: MinifluxApiService
) {
    fun getAllArticlesOldestFirst(): Flow<List<Entry>> =
        articleDao.getAllArticlesOldestFirst().map { list -> list.map { it.toEntry() } }

    suspend fun refreshArticles() {
        val response = api.getEntries()
        val articles = response.entries.map { entry -> entry.toEntity() }
        articleDao.clearAll()
        articleDao.insertArticles(articles)
    }

    suspend fun updateReadStatus(articleId: String, newStatus: String) {
        api.updateEntriesStatus(UpdateEntriesStatusRequest(listOf(articleId.toLong()), newStatus))
        articleDao.updateStatus(articleId, newStatus)
    }
}

private fun ArticleEntity.toEntry(): Entry = Entry(
    id = id.toLong(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feed = feed,
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)

private fun Entry.toEntity(): ArticleEntity = ArticleEntity(
    id = id.toString(),
    title = title,
    author = author,
    url = url,
    publishedAt = publishedAt,
    content = content,
    feed = feed,
    readingTime = readingTime,
    enclosures = enclosures,
    status = status
)
