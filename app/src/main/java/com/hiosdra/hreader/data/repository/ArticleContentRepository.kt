package com.hiosdra.hreader.data.repository

import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Date

/**
 * Repository for managing article content, coordinating between the Miniflux API
 * and local database for offline storage.
 */
class ArticleContentRepository(
    private val minifluxApiRepository: MinifluxApiRepository,
    private val articleContentDao: ArticleContentDao
) {
    /**
     * Gets article content from local database if available, otherwise fetches from API
     * and stores in database.
     */
    suspend fun getArticleContent(entryId: Long, url: String): String {
        // Check if we have the content locally
        val localContent = articleContentDao.getArticleContent(entryId)

        // If content exists locally, return it
        if (localContent != null) {
            return localContent.content
        }

        // Otherwise fetch from API
        val originalContent = minifluxApiRepository.fetchOriginalContent(entryId)

        // Store in database
        val articleContent = ArticleContent(
            entryId = entryId,
            content = originalContent.content,
            fetchedAt = Date(),
            url = url
        )

        articleContentDao.insertArticleContent(articleContent)

        return originalContent.content
    }

    /**
     * Processes HTML content to find images and potentially save them locally
     * for true offline capability.
     *
     * Note: This is a placeholder for now, actual implementation will be added later.
     */
    private suspend fun processAndSaveImages(entryId: Long, htmlContent: String) {
        // This will be implemented later when we add proper image handling
        // For now, we just store the HTML as-is
    }

    suspend fun prefetchArticleContent(entries: List<Pair<Long, String>>) = coroutineScope {
        val deferredResults = entries.map { (entryId, url) ->
            async(Dispatchers.IO) {
                try {
                    if (articleContentDao.getArticleContent(entryId) == null) {
                        getArticleContent(entryId, url)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        deferredResults.awaitAll()
    }

    suspend fun deleteArticleContent(entryId: Long) {
        articleContentDao.deleteArticleContent(entryId)
    }

    suspend fun getAllArticleContents(): List<ArticleContent> {
        return articleContentDao.getAllArticleContents()
    }
}
