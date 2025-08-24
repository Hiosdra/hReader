package com.hiosdra.hreader.data.local.repository

import android.util.Log
import com.hiosdra.hreader.data.local.dao.ArticleContentDao
import com.hiosdra.hreader.data.local.dao.ArticleDao
import com.hiosdra.hreader.data.local.entity.ArticleContent
import com.hiosdra.hreader.data.remote.MinifluxApiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.jsoup.Jsoup
import java.time.Instant

class ArticleContentRepository(
    private val minifluxApiRepository: MinifluxApiRepository,
    private val articleContentDao: ArticleContentDao,
    private val articleDao: ArticleDao,
    private val articleImageRepository: ArticleImageRepository
) {
    suspend fun getArticleContent(entryId: Long, url: String): String {
        val localContent = articleContentDao.getArticleContent(entryId)
        if (localContent != null) {
            return localContent.content
        }

        val originalContent = minifluxApiRepository.fetchOriginalContent(entryId)

        // Download images from content
        processAndSaveImages(entryId, originalContent.content, url)

        val articleContent = ArticleContent(
            entryId = entryId,
            content = originalContent.content,
            fetchedAt = Instant.now(),
            url = url
        )
        articleContentDao.insertArticleContent(articleContent)
        return originalContent.content
    }

    private suspend fun processAndSaveImages(entryId: Long, htmlContent: String, baseUri: String) {
        // Extract image URLs from HTML content using Jsoup for robustness
        val imageUrls = Jsoup.parse(htmlContent, baseUri).select("img[src]")
            .map { it.attr("abs:src") }
            .filterNot { it.isBlank() }
            .distinct()
            .toList()

        // Download each image
        imageUrls.forEach { imageUrl ->
            try {
                articleImageRepository.downloadAndStoreImage(entryId, imageUrl)
            } catch (e: Exception) {
                Log.e("ArticleContentRepo", "Failed to download image $imageUrl for entry $entryId", e)
            }
        }
    }

    suspend fun prefetchArticleContent(entries: List<Pair<Long, String>>, limit: Int? = 50) = coroutineScope {
        // Apply limit if specified (null means no limit for background sync)
        val limitedEntries = if (limit != null) entries.take(limit) else entries

        val deferredResults = limitedEntries.map { (entryId, url) ->
            async(Dispatchers.IO) {
                try {
                    if (articleContentDao.getArticleContent(entryId) == null) {
                        getArticleContent(entryId, url)
                    }
                } catch (e: Exception) {
                    Log.e("ArticleContentRepo", "Failed to prefetch content for entry $entryId", e)
                }
            }
        }

        deferredResults.awaitAll()
    }

    suspend fun downloadEnclosureImages(entries: List<Pair<Long, List<String>>>) = coroutineScope {
        val deferredResults = entries.map { (entryId, imageUrls) ->
            async(Dispatchers.IO) {
                imageUrls.forEach { imageUrl ->
                    try {
                        articleImageRepository.downloadAndStoreImage(entryId, imageUrl)
                    } catch (e: Exception) {
                        Log.e("ArticleContentRepo", "Failed to download enclosure image $imageUrl for entry $entryId", e)
                    }
                }
            }
        }
        deferredResults.awaitAll()
    }

    suspend fun cleanupOrphanedContent() {
        // Cleanup orphaned article content
        val allContent = articleContentDao.getAllArticleContents()
        if (allContent.isEmpty()) return
        val allArticles = articleDao.getAllArticlesOldestFirst().first()
        val currentEntryIds = allArticles.map { it.id.toLong() }.toHashSet()
        if (currentEntryIds.isEmpty()) return
        val contentToDelete = allContent.filter { content ->
            !currentEntryIds.contains(content.entryId)
        }
        if (contentToDelete.isEmpty()) return
        articleContentDao.deleteArticlesContent(contentToDelete.map { it.entryId })

        // Cleanup orphaned images
        articleImageRepository.cleanupOrphanedImages()
    }
}
