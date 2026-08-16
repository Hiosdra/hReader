package com.hiosdra.hreader.core.application.port.out

interface ArticleReadingPositionStore {
    suspend fun getProgresses(articleIds: Collection<Long>): Map<Long, Float>
    suspend fun saveProgress(articleId: Long, progress: Float)
    suspend fun deleteProgress(articleId: Long)
}
