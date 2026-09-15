package com.hiosdra.hreader.presentation.main

import androidx.paging.PagingData
import com.hiosdra.hreader.core.domain.model.ArticleListItem
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import kotlinx.coroutines.flow.Flow

fun interface ArticlePagingProvider {
    fun pageArticles(query: ArticleListQuery): Flow<PagingData<ArticleListItem>>
}
