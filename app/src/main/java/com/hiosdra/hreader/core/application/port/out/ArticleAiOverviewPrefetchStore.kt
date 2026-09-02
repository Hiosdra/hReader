package com.hiosdra.hreader.core.application.port.out

data class AiOverviewPrefetchTarget(
    val id: Long,
    val title: String,
    val url: String
)

interface ArticleAiOverviewPrefetchStore {
    suspend fun getAiOverviewPrefetchTargets(): List<AiOverviewPrefetchTarget>
}
