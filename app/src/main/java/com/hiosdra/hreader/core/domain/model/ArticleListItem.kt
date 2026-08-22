package com.hiosdra.hreader.core.domain.model

import java.time.LocalDate

sealed interface ArticleListItem {
    data class Article(val entry: ArticleListEntry) : ArticleListItem
    data class DayHeader(val date: LocalDate) : ArticleListItem
}
