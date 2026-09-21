package com.hiosdra.hreader.presentation.article

import com.hiosdra.hreader.R
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.AiProviderException
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.GemmaModelNotInstalledException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.usecase.article.ArticleReaderUseCase
import com.hiosdra.hreader.core.application.util.runCatchingCancellable
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.hiosdra.hreader.core.domain.model.Entry
import com.hiosdra.hreader.presentation.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private val MISSING_CONTENT_MESSAGE = UiText.Resource(R.string.article_missing_content)

internal fun initialArticleAiState(reader: ArticleReaderUseCase): ArticleAiState = ArticleAiState(
    credibilityEnabled = reader.credibilityEnabled(),
    aiProvider = AiModel.providerFor(reader.getAiModelId())
)

internal class ArticleAiCoordinator(
    private val reader: ArticleReaderUseCase,
    private val state: MutableStateFlow<ArticleUiState>,
    private val scope: CoroutineScope
) {
    private var activeModelId = reader.getAiModelId()
    private var modelGeneration = 0
    private val checkedCredibilityIds = mutableSetOf<Long>()

    fun start() {
        scope.launch {
            reader.observeAiModelId().collect { modelId ->
                if (modelId == activeModelId) return@collect
                activeModelId = modelId
                modelGeneration++
                checkedCredibilityIds.clear()
                val generation = modelGeneration
                val nearbyIds = state.value.readerWindowIds()
                state.update {
                    it.copy(
                        ai = it.ai.copy(
                            aiOverviews = emptyMap(),
                            aiProvider = AiModel.providerFor(modelId),
                            credibilityReports = emptyMap(),
                            generatingOverviewIds = emptySet(),
                            aiOverviewProgress = emptyMap(),
                            analyzingCredibilityIds = emptySet(),
                            overviewError = null,
                            scoreError = null
                        )
                    )
                }
                reloadCaches(nearbyIds, modelId, generation)
            }
        }
    }

    fun loadCachedOverview(entryId: Long, body: String) {
        val modelId = activeModelId
        val generation = modelGeneration
        scope.launch {
            val overview = reader.getCachedOverview(entryId, body, modelId) ?: return@launch
            updateVisibleForGeneration(generation, entryId) { current ->
                current.copy(ai = current.ai.copy(aiOverviews = current.ai.aiOverviews + (entryId to overview)))
            }
        }
    }

    fun loadCachedCredibility(entryIds: List<Long>) {
        if (!state.value.ai.credibilityEnabled) return
        checkedCredibilityIds.retainAll(entryIds.toSet())
        val modelId = activeModelId
        val generation = modelGeneration
        val missing = entryIds
            .filterNot { state.value.ai.credibilityReports.containsKey(it) }
            .filter { checkedCredibilityIds.add(it) }
        if (missing.isEmpty()) return
        val sources = missing.mapNotNull { entryId ->
            val entry = state.value.navigation.entries.firstOrNull { it.id == entryId }
                ?: return@mapNotNull null
            val content = contentFor(entryId)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            entryId to entry.toCredibilitySource(content)
        }.toMap()
        if (sources.isEmpty()) return
        scope.launch {
            val cached = runCatchingCancellable {
                reader.getCachedCredibility(sources, modelId)
            }.getOrElse { emptyMap() }
            if (generation != modelGeneration || cached.isEmpty()) return@launch
            state.update { current ->
                val retainedIds = current.readerWindowIds()
                current.copy(
                    ai = current.ai.copy(
                        credibilityReports = current.ai.credibilityReports.filterKeys { it in retainedIds } +
                            cached.filterKeys { it in retainedIds }
                    )
                )
            }
        }
    }

    fun generateOverview(entryId: Long) {
        val entry = entry(entryId) ?: return
        if (entryId in state.value.ai.generatingOverviewIds) return
        val modelId = activeModelId
        val generation = modelGeneration
        state.update {
            it.copy(
                ai = it.ai.copy(
                    generatingOverviewIds = it.ai.generatingOverviewIds + entryId,
                    aiOverviewProgress = it.ai.aiOverviewProgress +
                        (entryId to ArticleAiProgress(ArticleAiPhase.PREPARING)),
                    overviewError = null
                )
            )
        }
        scope.launch {
            val content = contentFor(entryId).orEmpty()
            if (content.isBlank()) {
                updateForGeneration(generation) { current ->
                    current.copy(
                        ai = current.ai.copy(
                            generatingOverviewIds = current.ai.generatingOverviewIds - entryId,
                            aiOverviewProgress = current.ai.aiOverviewProgress - entryId,
                            overviewError = MISSING_CONTENT_MESSAGE
                        )
                    )
                }
                return@launch
            }
            val result = reader.generateOverview(
                entryId = entryId,
                title = entry.title,
                body = content,
                modelId = modelId
            ) { progress ->
                updateVisibleForGeneration(generation, entryId) { current ->
                    current.copy(
                        ai = current.ai.copy(
                            aiOverviewProgress = current.ai.aiOverviewProgress + (entryId to progress)
                        )
                    )
                }
            }
            updateForGeneration(generation) { current ->
                current.copy(
                    ai = current.ai.copy(
                        generatingOverviewIds = current.ai.generatingOverviewIds - entryId,
                        aiOverviewProgress = current.ai.aiOverviewProgress - entryId,
                        aiOverviews = result.getOrNull()?.takeIf { entryId in current.readerWindowIds() }?.let {
                            current.ai.aiOverviews + (entryId to it)
                        } ?: current.ai.aiOverviews,
                        overviewError = result.exceptionOrNull()
                            ?.let { aiErrorText(it, R.string.article_summary_error) }
                            ?: current.ai.overviewError
                    )
                )
            }
        }
    }

    fun analyzeCredibility(entryId: Long, forceRefresh: Boolean = false) {
        val entry = entry(entryId) ?: return
        if (!state.value.ai.credibilityEnabled) return
        if (entryId in state.value.ai.analyzingCredibilityIds) return
        if (!forceRefresh && entryId in state.value.ai.credibilityReports) return
        val content = contentFor(entryId).orEmpty()
        if (content.isBlank()) {
            state.update { it.copy(ai = it.ai.copy(scoreError = MISSING_CONTENT_MESSAGE)) }
            return
        }
        val modelId = activeModelId
        val generation = modelGeneration
        state.update {
            it.copy(
                ai = it.ai.copy(
                    analyzingCredibilityIds = it.ai.analyzingCredibilityIds + entryId,
                    scoreError = null
                )
            )
        }
        scope.launch {
            val result = reader.analyzeCredibility(
                entryId = entryId,
                source = entry.toCredibilitySource(content),
                forceRefresh = forceRefresh,
                modelId = modelId
            )
            updateForGeneration(generation) { current ->
                current.copy(
                    ai = current.ai.copy(
                        analyzingCredibilityIds = current.ai.analyzingCredibilityIds - entryId,
                        credibilityReports = result.getOrNull()
                            ?.takeIf { entryId in current.readerWindowIds() }
                            ?.let {
                            current.ai.credibilityReports + (entryId to it)
                        } ?: current.ai.credibilityReports,
                        scoreError = result.exceptionOrNull()
                            ?.let { aiErrorText(it, R.string.article_credibility_error) }
                            ?: current.ai.scoreError
                    )
                )
            }
        }
    }

    fun clearOverviewError() {
        state.update { it.copy(ai = it.ai.copy(overviewError = null)) }
    }

    fun clearScoreError() {
        state.update { it.copy(ai = it.ai.copy(scoreError = null)) }
    }

    private fun reloadCaches(entryIds: Set<Long>, modelId: String, generation: Int) {
        val overviews = entryIds.mapNotNull { entryId ->
            state.value.content.content[entryId]?.let { entryId to it }
        }
        if (overviews.isNotEmpty()) {
            scope.launch {
                val cached = overviews.mapNotNull { (entryId, body) ->
                    runCatchingCancellable {
                        reader.getCachedOverview(entryId, body, modelId)
                    }.getOrNull()?.let { entryId to it }
                }.toMap()
                if (generation != modelGeneration) return@launch
                state.update { current ->
                    current.copy(
                        ai = current.ai.copy(
                            aiOverviews = cached.filterKeys { it in current.readerWindowIds() }
                        )
                    )
                }
            }
        }
        loadCachedCredibility(entryIds.toList())
    }

    private fun updateForGeneration(
        generation: Int,
        transform: (ArticleUiState) -> ArticleUiState
    ) {
        state.update { current ->
            if (generation != modelGeneration) {
                current
            } else {
                transform(current)
            }
        }
    }

    private fun updateVisibleForGeneration(
        generation: Int,
        entryId: Long,
        transform: (ArticleUiState) -> ArticleUiState
    ) = updateForGeneration(generation) { current ->
        if (entryId in current.readerWindowIds()) transform(current) else current
    }

    private fun entry(entryId: Long): Entry? =
        state.value.navigation.entries.firstOrNull { it.id == entryId }

    private fun contentFor(entryId: Long): String? =
        state.value.content.content[entryId]
            ?: entry(entryId)?.let(::readerFallbackContent)

    private fun Entry.toCredibilitySource(content: String): CredibilitySource = CredibilitySource(
        title = title,
        content = content,
        author = author,
        feedTitle = feed.title,
        url = url,
        publishedAt = publishedAt
    )

    private fun aiErrorText(error: Throwable, fallbackResId: Int): UiText = when (error) {
        is MissingAiApiKeyException -> UiText.Resource(R.string.article_ai_api_key_missing)
        is GemmaModelNotInstalledException -> UiText.Resource(R.string.article_ai_model_missing)
        is EmptyAiContentException -> MISSING_CONTENT_MESSAGE
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is IOException -> UiText.Resource(R.string.article_ai_network_error)
        is AiProviderException -> when (error.statusCode) {
            401, 403 -> UiText.Resource(R.string.article_ai_api_key_invalid)
            404, 422 -> UiText.Resource(R.string.article_ai_model_unavailable)
            else -> UiText.Resource(R.string.article_ai_provider_error)
        }
        else -> UiText.Resource(fallbackResId)
    }
}
