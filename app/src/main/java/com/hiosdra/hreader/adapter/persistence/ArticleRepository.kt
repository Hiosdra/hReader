package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import androidx.paging.PagingData
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.core.application.content.extractArticlePreview
import com.hiosdra.hreader.core.application.port.out.AiOverviewPrefetchTarget
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewPrefetchStore
import com.hiosdra.hreader.core.application.port.out.ArticleListWindow
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.application.port.out.ArticleStore
import com.hiosdra.hreader.core.application.port.out.ArticleSyncStore
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow
import java.time.Instant

private const val TAG = "ArticleRepository"

class ArticleRepository(
    articleDao: ArticleDao,
    feedDao: FeedDao,
    private val syncEngine: ArticleSyncStore
) : ArticleStore, ArticleAiOverviewPrefetchStore {
    private val queryRepository: ArticleQueryStore = ArticleQueryRepository(articleDao, feedDao)
    private val mutationRepository: ArticleMutationStore = ArticleMutationRepository(articleDao)
    private val articleDao = articleDao

    override fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListItem>> =
        queryRepository.pageArticles(query)

    override suspend fun listWindow(
        query: ArticleListQuery,
        articleId: Long,
        radius: Int
    ): ArticleListWindow = queryRepository.listWindow(query, articleId, radius)

    override suspend fun unreadIds(feedId: Long?): List<Long> = queryRepository.unreadIds(feedId)

    override fun observeUnreadCount(feedId: Long?): Flow<Int> =
        queryRepository.observeUnreadCount(feedId)

    override fun observeReadCount(feedId: Long?): Flow<Int> =
        queryRepository.observeReadCount(feedId)

    override fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        queryRepository.getArticlesByIds(ids)

    override suspend fun refreshArticles(forceFullSync: Boolean) =
        syncEngine.refreshArticles(forceFullSync)

    override suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus) =
        mutationRepository.updateReadStatus(articleIds, newStatus)

    override suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus) =
        mutationRepository.updateReadStatus(articleId, newStatus)

    override suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long> =
        mutationRepository.idsStillReadSince(articleIds, readBefore)

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

    override suspend fun getFeed(feedId: Long): Feed? = queryRepository.getFeed(feedId)

    suspend fun refreshArticles() = refreshArticles(forceFullSync = false)

    suspend fun backfillMissingPreviews() = backfillMissingPreviews(PREVIEW_BACKFILL_LIMIT)

    companion object {
        internal const val PREVIEW_BACKFILL_LIMIT = 500
    }
}
