package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatusCounts
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.Feed
import kotlinx.coroutines.flow.Flow

interface ArticleQueryStore {
    suspend fun listWindow(query: ArticleListQuery, articleId: Long, radius: Int): ArticleListWindow
    fun observeStatusCounts(feedId: Long?): Flow<ArticleStatusCounts>
    fun getArticlesByIds(ids: List<Long>): Flow<List<Entry>>
    suspend fun getFeed(feedId: Long): Feed?
}
