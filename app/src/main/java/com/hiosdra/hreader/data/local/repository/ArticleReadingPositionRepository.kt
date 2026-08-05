package com.hiosdra.hreader.data.local.repository

import com.hiosdra.hreader.data.local.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.data.local.entity.ArticleReadingPosition

class ArticleReadingPositionRepository(
    private val dao: ArticleReadingPositionDao
) {
    suspend fun getProgresses(articleIds: Collection<Long>): Map<Long, Float> {
        if (articleIds.isEmpty()) return emptyMap()
        return dao.getForArticles(articleIds.map(Long::toString))
            .mapNotNull { position ->
                position.articleId.toLongOrNull()?.let { it to position.progress }
            }
            .toMap()
    }

    suspend fun saveProgress(articleId: Long, progress: Float) {
        dao.upsert(
            ArticleReadingPosition(
                articleId = articleId.toString(),
                progress = progress.coerceIn(0f, 1f)
            )
        )
    }

    suspend fun deleteProgress(articleId: Long) {
        dao.deleteForArticle(articleId.toString())
    }
}
