package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.core.application.port.out.AiOverviewPrefetchTarget
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewPrefetchStore

internal class ArticleAiOverviewPrefetchRepository(
    private val articleDao: ArticleDao
) : ArticleAiOverviewPrefetchStore {
    override suspend fun getAiOverviewPrefetchTargets(
        limit: Int,
        offset: Int
    ): List<AiOverviewPrefetchTarget> = articleDao
        .getAiOverviewPrefetchTargets(limit = limit, offset = offset)
        .mapNotNull { target ->
            target.id.toLongOrNull()?.let { id ->
                AiOverviewPrefetchTarget(id = id, title = target.title, url = target.url)
            }
        }
}
