package com.hiosdra.hreader.presentation.article

import androidx.compose.runtime.Immutable

@Immutable
internal data class ArticleRowModel(
    val id: Long,
    val title: String,
    val preview: String?,
    val feedTitle: String,
    val publishedTime: String,
    val isRead: Boolean
)
