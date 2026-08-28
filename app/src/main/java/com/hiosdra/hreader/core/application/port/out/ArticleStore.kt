package com.hiosdra.hreader.core.application.port.out

data class ArticleListWindow(
    val ids: List<Long>,
    val totalCount: Int,
    val windowStartIndex: Int,
    val currentIndex: Int
)

interface ArticleStore :
    ArticleQueryStore,
    ArticleMutationStore,
    ArticleSyncStore,
    ArticleMaintenanceStore
