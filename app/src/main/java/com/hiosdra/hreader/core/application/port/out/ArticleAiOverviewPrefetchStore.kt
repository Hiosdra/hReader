package com.hiosdra.hreader.core.application.port.out

data class AiOverviewPrefetchTarget(
    val id: Long,
    val title: String,
    val url: String,
    val preloadAiOverview: Boolean = true,
    val preloadAiArticleSummary: Boolean = false
)

interface ArticleAiOverviewPrefetchStore {
    suspend fun getAiOverviewPrefetchTargets(limit: Int, offset: Int): List<AiOverviewPrefetchTarget>
}
