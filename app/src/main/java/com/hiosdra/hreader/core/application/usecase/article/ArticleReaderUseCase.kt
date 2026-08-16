package com.hiosdra.hreader.core.application.usecase.article

import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ArticleReadingPositionStore
import com.hiosdra.hreader.core.application.port.out.ArticleStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

class ArticleReaderUseCase(
    private val articles: ArticleStore,
    private val positions: ArticleReadingPositionStore,
    private val content: ArticleContentStore,
    private val pages: ArticlePageStore,
    private val ai: ArticleAiGateway,
    private val overviews: ArticleAiOverviewStore,
    private val credibility: CredibilityStore,
    private val preferences: AppPreferences,
    private val images: ArticleImageLoader,
    network: NetworkStatus
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    fun credibilityEnabled(): Boolean = preferences.getCredibilityScoreEnabled()

    suspend fun getArticleIds(query: ArticleListQuery): List<Long> = articles.listIds(query)

    fun observeArticles(ids: List<Long>): Flow<List<Entry>> = articles.getArticlesByIds(ids)

    suspend fun getReadingProgresses(ids: Collection<Long>): Map<Long, Float> = positions.getProgresses(ids)

    suspend fun getOfflinePage(entryId: Long, url: String): OfflinePage? = pages.getOfflinePage(entryId, url)

    suspend fun getArticleContent(entryId: Long, url: String, allowNetwork: Boolean) =
        content.getArticleContent(entryId, url, allowNetwork)

    suspend fun getLocalImagePaths(entryId: Long): Map<String, String> = images.getLocalImagePaths(entryId)

    suspend fun getCachedOverview(entryId: Long, body: String): String? =
        overviews.get(entryId, body, preferences.getAiModelId())

    suspend fun generateOverview(entryId: Long, title: String, body: String): Result<String> {
        val modelId = preferences.getAiModelId()
        overviews.get(entryId, body, modelId)?.let { return Result.success(it) }
        return ai.generateArticleOverview(title, body, modelId).onSuccess { overview ->
            overviews.save(entryId, body, modelId, overview)
        }
    }

    suspend fun analyzeCredibility(
        entryId: Long,
        source: CredibilitySource,
        forceRefresh: Boolean
    ): Result<CredibilityReport> = credibility.analyze(
        entryId = entryId,
        source = source,
        modelId = preferences.getAiModelId(),
        forceRefresh = forceRefresh
    )

    suspend fun getCachedCredibility(ids: List<Long>): Map<Long, CredibilityReport> = credibility.getCached(ids)

    suspend fun updateReadStatus(entryId: Long, status: ArticleStatus) {
        articles.updateReadStatus(entryId.toString(), status)
    }

    suspend fun updateReadStatus(entryIds: List<Long>, status: ArticleStatus) {
        articles.updateReadStatus(entryIds.map(Long::toString), status)
    }

    suspend fun updateStarred(entryId: Long, starred: Boolean) = articles.updateStarred(entryId, starred)

    suspend fun saveReadingProgress(entryId: Long, progress: Float) = positions.saveProgress(entryId, progress)

    suspend fun clearReadingProgress(entryId: Long) = positions.deleteProgress(entryId)

    suspend fun idsStillReadSince(articleIds: List<Long>, readBefore: Instant): List<Long> =
        articles.idsStillReadSince(articleIds, readBefore)
}
