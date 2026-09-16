package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRetentionDao
import com.hiosdra.hreader.core.application.port.out.ArticleRetentionStore
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import java.time.Instant

internal class ArticleRetentionRepository(
    private val articleRetentionDao: ArticleRetentionDao
) : ArticleRetentionStore {
    override suspend fun deleteReadArticlesBefore(before: Instant): Int =
        articleRetentionDao.deleteReadArticlesBefore(before, ArticleStatus.READ)
}
