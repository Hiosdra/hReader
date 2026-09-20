package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleContent
import com.hiosdra.hreader.core.application.port.out.FeedBackend
import com.hiosdra.hreader.core.domain.model.ArticleContentDelivery
import com.hiosdra.hreader.core.domain.model.ArticleContentSource
import com.hiosdra.hreader.core.application.content.articlePreviewHtml
import kotlinx.coroutines.CancellationException

internal sealed interface ArticleContentSelection {
    data class Stored(val content: ArticleContent) : ArticleContentSelection

    data class Acquired(
        val content: String,
        val source: ArticleContentSource,
        val delivery: ArticleContentDelivery
    ) : ArticleContentSelection
}

internal class ArticleContentReader(
    private val backend: FeedBackend,
    private val articleContentDao: ArticleContentDao,
    private val articleRecordDao: ArticleRecordDao
) {
    suspend fun read(entryId: Long, url: String, allowNetwork: Boolean): ArticleContentSelection {
        val stored = articleContentDao.getArticleContent(entryId)
        if (stored != null && stored.url == url && stored.content.isNotBlank()) {
            if (stored.source == ArticleContentSource.FULL) {
                return ArticleContentSelection.Stored(stored)
            }

            if (allowNetwork) {
                fetchFullContent(entryId, url)?.let { content ->
                    return ArticleContentSelection.Acquired(
                        content = content,
                        source = ArticleContentSource.FULL,
                        delivery = ArticleContentDelivery.NETWORK
                    )
                }
            }

            cachedContent(entryId)?.takeIf { it.source == ArticleContentSource.FULL }?.let { cached ->
                return ArticleContentSelection.Acquired(
                    content = cached.content,
                    source = cached.source,
                    delivery = ArticleContentDelivery.LOCAL_STORAGE
                )
            }
            return ArticleContentSelection.Stored(stored)
        }

        if (allowNetwork) {
            fetchFullContent(entryId, url)?.let { content ->
                return ArticleContentSelection.Acquired(
                    content = content,
                    source = ArticleContentSource.FULL,
                    delivery = ArticleContentDelivery.NETWORK
                )
            }
        }

        val cached = cachedContent(entryId)
            ?: throw IllegalStateException("No content available for entry $entryId")
        return ArticleContentSelection.Acquired(
            content = cached.content,
            source = cached.source,
            delivery = ArticleContentDelivery.LOCAL_STORAGE
        )
    }

    private suspend fun fetchFullContent(entryId: Long, url: String): String? = try {
        backend.fetchFullContent(entryId, url)?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Backend could not provide full content for entry $entryId: ${e.message}")
        null
    }

    private suspend fun cachedContent(entryId: Long): CachedContent? {
        val article = articleRecordDao.getArticlesImmediate(listOf(entryId.toString())).firstOrNull()
            ?: return null
        article.fullContent?.takeIf { it.isNotBlank() }?.let {
            return CachedContent(it, ArticleContentSource.FULL)
        }
        article.content?.takeIf { it.isNotBlank() }?.let {
            return CachedContent(it, ArticleContentSource.FEED_FALLBACK)
        }
        articlePreviewHtml(article.preview)?.let {
            return CachedContent(it, ArticleContentSource.FEED_FALLBACK)
        }
        return null
    }

    private data class CachedContent(
        val content: String,
        val source: ArticleContentSource
    )

    private companion object {
        const val TAG = "ArticleContentReader"
    }
}
