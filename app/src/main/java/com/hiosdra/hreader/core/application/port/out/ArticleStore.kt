package com.hiosdra.hreader.core.application.port.out

import androidx.paging.PagingData
import com.hiosdra.hreader.core.application.sync.PrefetchTarget
import com.hiosdra.hreader.core.domain.model.ArticleListEntry
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ArticleStore {
    fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListEntry>>
    suspend fun listIds(query: ArticleListQuery): List<Long>
    suspend fun unreadIds(feedId: Long?, starredOnly: Boolean): List<Long>
    fun observeUnreadCount(feedId: Long?, starredOnly: Boolean): Flow<Int>
    fun observeReadCount(feedId: Long?, starredOnly: Boolean): Flow<Int>
    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>>
    suspend fun refreshArticles(forceFullSync: Boolean = false)
    suspend fun updateReadStatus(articleIds: List<String>, newStatus: ArticleStatus)
    suspend fun updateReadStatus(articleId: String, newStatus: ArticleStatus)
    suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long>
    suspend fun updateStarred(articleId: Long, starred: Boolean)
    suspend fun backfillMissingPreviews(limit: Int = 500): Int
    suspend fun getPrefetchTargets(): List<PrefetchTarget>
    suspend fun getFeed(feedId: Long): Feed?
}
