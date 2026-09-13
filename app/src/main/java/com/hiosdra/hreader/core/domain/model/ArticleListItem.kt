package com.hiosdra.hreader.core.domain.model

sealed interface ArticleListItem {
    data class Article(val entry: ArticleListEntry) : ArticleListItem
    data class DayHeader(val dateEpochDay: Long) : ArticleListItem
}
