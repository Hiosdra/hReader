package com.hiosdra.hreader.core.application.usecase.article

import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.ArticleContentStore
import com.hiosdra.hreader.core.application.port.out.ArticleImageLoader
import com.hiosdra.hreader.core.application.port.out.ArticleListWindow
import com.hiosdra.hreader.core.application.port.out.ArticleMutationStore
import com.hiosdra.hreader.core.application.port.out.ArticlePageStore
import com.hiosdra.hreader.core.application.port.out.ArticleReadingPositionStore
import com.hiosdra.hreader.core.application.port.out.ArticleQueryStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore
import com.hiosdra.hreader.core.application.port.out.NetworkStatus
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.domain.model.ArticleListQuery
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.core.domain.model.OfflinePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ArticleReaderUseCase(
    private val articles: ArticleQueryStore,
    private val articleMutations: ArticleMutationStore,
    private val positions: ArticleReadingPositionStore,
    private val content: ArticleContentStore,
    private val pages: ArticlePageStore,
    private val ai: ArticleAiGateway,
    private val overviews: ArticleAiOverviewStore,
    private val credibility: CredibilityStore,
    private val preferences: ReaderPreferences,
    private val aiPreferences: AiPreferences,
    private val images: ArticleImageLoader,
    network: NetworkStatus
) {
    val isOnline: StateFlow<Boolean> = network.isOnline

    fun credibilityEnabled(): Boolean = preferences.getCredibilityScoreEnabled()

    fun getAiModelId(): String = aiPreferences.getAiModelId()

    fun observeAiModelId(): Flow<String> = aiPreferences.observeAiModelId()

    suspend fun getArticleListWindow(
        query: ArticleListQuery,
        articleId: Long,
        radius: Int
    ): ArticleListWindow = articles.listWindow(query, articleId, radius)

    fun observeArticles(ids: List<Long>): Flow<List<Entry>> = articles.getArticlesByIds(ids)

    suspend fun getReadingProgresses(ids: Collection<Long>): Map<Long, Float> = positions.getProgresses(ids)

    suspend fun getOfflinePage(entryId: Long, url: String): OfflinePage? = pages.getOfflinePage(entryId, url)

    suspend fun getArticleContent(entryId: Long, url: String, allowNetwork: Boolean) =
        content.getArticleContent(entryId, url, allowNetwork)

    suspend fun getLocalImagePaths(entryId: Long): Map<String, String> = images.getLocalImagePaths(entryId)

    fun observeLocalImagePaths(entryId: Long): Flow<Map<String, String>> =
        images.observeLocalImagePaths(entryId)

    suspend fun getCachedOverview(entryId: Long, body: String, modelId: String = getAiModelId()): String? =
        overviews.get(entryId, body, modelId)

    suspend fun generateOverview(
        entryId: Long,
        title: String,
        body: String,
        modelId: String = getAiModelId(),
        onProgress: suspend (ArticleAiProgress) -> Unit = {}
    ): Result<String> {
        overviews.get(entryId, body, modelId)?.let { return Result.success(it) }
        return ai.generateArticleOverview(title, body, modelId, onProgress).onSuccess { overview ->
            overviews.save(entryId, body, modelId, overview)
        }
    }

    suspend fun analyzeCredibility(
        entryId: Long,
        source: CredibilitySource,
        forceRefresh: Boolean,
        modelId: String = getAiModelId()
    ): Result<CredibilityReport> = credibility.analyze(
        entryId = entryId,
        source = source,
        modelId = modelId,
        forceRefresh = forceRefresh
    )

    suspend fun getCachedCredibility(
        ids: List<Long>,
        modelId: String = getAiModelId()
    ): Map<Long, CredibilityReport> = credibility.getCached(ids, modelId)

    suspend fun updateReadStatus(entryId: Long, status: ArticleStatus) {
        articleMutations.updateReadStatus(entryId.toString(), status)
    }

    suspend fun saveReadingProgress(entryId: Long, progress: Float) = positions.saveProgress(entryId, progress)

    suspend fun clearReadingProgress(entryId: Long) = positions.deleteProgress(entryId)

}
