package com.hiosdra.hreader.adapter.persistence

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.hiosdra.hreader.adapter.persistence.room.buildFtsMatchQuery
import com.hiosdra.hreader.adapter.persistence.room.buildLikePattern
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleDao
import com.hiosdra.hreader.adapter.persistence.room.dao.FeedDao
import com.hiosdra.hreader.core.application.port.out.ArticleListWindow
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

private val PAGING_CONFIG = PagingConfig(
    pageSize = 40,
    prefetchDistance = 20,
    maxSize = 200,
    enablePlaceholders = false
)

internal class ArticleQueryRepository(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao
) : ArticleQueryStore {
    override fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListItem>> {
        val match = buildFtsMatchQuery(query.searchQuery.trim())
        return Pager(PAGING_CONFIG) {
            if (match == null) {
                articleDao.pageArticles(
                    feedId = query.feedId,
                    starredOnly = query.starredOnly,
                    includeRead = query.includeRead,
                    sessionStart = query.sessionStart
                )
            } else {
                articleDao.pageSearchResults(
                    feedId = query.feedId,
                    starredOnly = query.starredOnly,
                    includeRead = query.includeRead,
                    sessionStart = query.sessionStart,
                    ftsQuery = match,
                    titleQuery = buildLikePattern(query.searchQuery)
                )
            }
        }.flow
            .map { page -> page.map { ArticleListItem.Article(it.toListEntry()) } }
            .map { page ->
                page.insertSeparators { before, after ->
                    val afterArticle = when (after) {
                        is ArticleListItem.Article -> after.entry
                        else -> return@insertSeparators null
                    }
                    val beforeDate = when (before) {
                        is ArticleListItem.Article -> before.entry.publishedAt
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        else -> null
                    }
                    val afterDate = afterArticle.publishedAt
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    if (beforeDate != afterDate) ArticleListItem.DayHeader(afterDate) else null
                }
            }
    }

    override suspend fun listWindow(
        query: ArticleListQuery,
        articleId: Long,
        radius: Int
    ): ArticleListWindow {
        val totalCount = articleDao.countList(
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart
        )
        if (totalCount == 0) {
            return ArticleListWindow(emptyList(), 0, 0, 0)
        }

        val publishedAt = articleDao.getPublishedAt(articleId.toString())
        val selectedArticleIsVisible = publishedAt != null && articleDao.countVisibleArticle(
            articleId = articleId.toString(),
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart
        ) > 0
        if (!selectedArticleIsVisible) {
            val ids = articleDao.getListWindow(
                feedId = query.feedId,
                starredOnly = query.starredOnly,
                includeRead = query.includeRead,
                sessionStart = query.sessionStart,
                limit = (radius * 2 + 1).coerceAtLeast(1),
                offset = 0
            ).toArticleIds("the reader's fallback window")
            return ArticleListWindow(
                ids = ids,
                totalCount = totalCount,
                windowStartIndex = 0,
                currentIndex = 0
            )
        }

        val currentPosition = articleDao.countArticlesBefore(
            articleId = articleId.toString(),
            publishedAt = publishedAt,
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart
        ).coerceIn(0, (totalCount - 1).coerceAtLeast(0))
        val before = articleDao.getListWindowBefore(
            articleId = articleId.toString(),
            publishedAt = publishedAt,
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart,
            limit = radius.coerceAtLeast(0)
        ).asReversed()
        val after = articleDao.getListWindowAfter(
            articleId = articleId.toString(),
            publishedAt = publishedAt,
            feedId = query.feedId,
            starredOnly = query.starredOnly,
            includeRead = query.includeRead,
            sessionStart = query.sessionStart,
            limit = radius.coerceAtLeast(0)
        )
        val ids = (before + articleId.toString() + after).toArticleIds("the reader's window")
        val windowStart = currentPosition - before.size
        return ArticleListWindow(
            ids = ids,
            totalCount = totalCount,
            windowStartIndex = windowStart,
            currentIndex = before.size.coerceIn(0, (ids.size - 1).coerceAtLeast(0))
        )
    }

    override suspend fun unreadIds(feedId: Long?, starredOnly: Boolean): List<Long> =
        articleDao.getUnreadIds(feedId, starredOnly).toArticleIds("the unread set")

    override fun observeUnreadCount(feedId: Long?, starredOnly: Boolean): Flow<Int> =
        articleDao.observeUnreadCountFor(feedId, starredOnly)

    override fun observeReadCount(feedId: Long?, starredOnly: Boolean): Flow<Int> =
        articleDao.observeReadCountFor(feedId, starredOnly)

    override fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>> =
        articleDao.getArticlesWithFeedByIds(ids.map { it.toString() }).map { rows ->
            rows.map { it.toEntry() }
        }

    override suspend fun getFeed(feedId: Long): Feed? = feedDao.getFeedById(feedId)?.toArticleFeed()
}
