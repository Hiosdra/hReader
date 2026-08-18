package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition
import com.hiosdra.hreader.core.application.port.out.ArticleReadingPositionStore

class ArticleReadingPositionRepository(
    private val dao: ArticleReadingPositionDao
) : ArticleReadingPositionStore {
    override suspend fun getProgresses(articleIds: Collection<Long>): Map<Long, Float> {
        if (articleIds.isEmpty()) return emptyMap()
        return dao.getForArticles(articleIds.toList())
            .associate { position -> position.articleId to position.progress }
    }

    override suspend fun saveProgress(articleId: Long, progress: Float) {
        dao.upsert(
            ArticleReadingPosition(
                articleId = articleId,
                progress = progress.coerceIn(0f, 1f)
            )
        )
    }

    override suspend fun deleteProgress(articleId: Long) {
        dao.deleteForArticle(articleId)
    }
}
