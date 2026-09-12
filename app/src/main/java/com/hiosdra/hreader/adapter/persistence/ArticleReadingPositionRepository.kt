package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleReadingPositionDao
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReadingPosition
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.port.out.ArticleReadingPositionStore
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate

class ArticleReadingPositionRepository(
    private val dao: ArticleReadingPositionDao,
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) : ArticleReadingPositionStore {
    override suspend fun getProgresses(articleIds: Collection<Long>): Map<Long, Float> {
        if (articleIds.isEmpty()) return emptyMap()
        val session = sessionGate.currentSession()
        return sessionGate.withSession(session) {
            dao.getForArticles(articleIds.map(Long::toString))
                .mapNotNull { position ->
                    position.articleId.toLongOrNull()?.let { it to position.progress }
                }
                .toMap()
        }
    }

    override suspend fun saveProgress(articleId: Long, progress: Float) {
        val session = sessionGate.currentSession()
        sessionGate.withSession(session) {
            dao.upsert(
                ArticleReadingPosition(
                    articleId = articleId.toString(),
                    progress = progress.coerceIn(0f, 1f)
                )
            )
        }
    }

    override suspend fun deleteProgress(articleId: Long) {
        val session = sessionGate.currentSession()
        sessionGate.withSession(session) {
            dao.deleteForArticle(articleId.toString())
        }
    }
}
