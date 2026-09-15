package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.core.application.content.extractArticlePreview
import com.hiosdra.hreader.core.application.port.out.ArticleMaintenanceStore
import com.hiosdra.hreader.core.application.sync.PrefetchTarget

private const val TAG = "ArticleMaintenanceRepository"

internal class ArticleMaintenanceRepository(
    private val articleDao: ArticleDao
) : ArticleMaintenanceStore {
    override suspend fun backfillMissingPreviews(limit: Int): Int {
        val stale = articleDao.getArticlesMissingPreview(limit)
        if (stale.isEmpty()) return 0
        stale.forEach { article ->
            articleDao.setPreview(article.id, extractArticlePreview(article.content).orEmpty())
        }
        Log.d(TAG, "Backfilled ${stale.size} article previews")
        return stale.size
    }

    override suspend fun getPrefetchTargets(): List<PrefetchTarget> = articleDao
        .getPrefetchTargets()
        .mapNotNull { target ->
            target.id.toLongOrNull()?.let { id ->
                PrefetchTarget(id = id, url = target.url, enclosures = target.enclosures)
            }
        }

    override suspend fun getPrefetchTargets(
        limit: Int,
        downloadAllImages: Boolean
    ): List<PrefetchTarget> = articleDao
        .getPrefetchTargetsMissingContent(limit, downloadAllImages)
        .mapNotNull { target ->
            target.id.toLongOrNull()?.let { id ->
                PrefetchTarget(id = id, url = target.url, enclosures = target.enclosures)
            }
        }

    override suspend fun getPrefetchTargetsWithEnclosures(limit: Int): List<PrefetchTarget> = articleDao
        .getPrefetchTargetsWithEnclosures(limit)
        .mapNotNull { target ->
            target.id.toLongOrNull()?.let { id ->
                PrefetchTarget(id = id, url = target.url, enclosures = target.enclosures)
            }
        }
}
