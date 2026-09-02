package com.hiosdra.hreader.adapter.ai.gemma

import android.util.Log
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.adapter.ai.common.stripToPlainText
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.ArticleSummaryPipeline
import com.hiosdra.hreader.core.application.ai.ArticleSummaryPromptPolicy
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GemmaArticleAi"
private const val CREDIBILITY_MAX_OUTPUT_TOKENS = 1500

class GemmaArticleAiService(
    private val engine: GemmaInferenceEngine,
    private val credibilityPromptBuilder: CredibilityPromptBuilder,
    private val credibilityResponseParser: CredibilityResponseParser,
    private val credibilityReportFactory: CredibilityReportFactory
) : ArticleAiGateway {
    private val summaryPipeline = ArticleSummaryPipeline()

    override suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String,
        onProgress: suspend (ArticleAiProgress) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        onProgress(ArticleAiProgress(ArticleAiPhase.PREPARING))
        val plainText = stripToPlainText(content)
        if (plainText.isBlank()) return@withContext Result.failure(EmptyAiContentException())
        onProgress(ArticleAiProgress(ArticleAiPhase.LOADING_MODEL))
        summaryPipeline.generate(
            title = stripToPlainText(title).trim(),
            content = plainText,
            modelId = modelId,
            contextLength = Gemma4E2bModel.CONTEXT_LENGTH,
            onProgress = onProgress,
            promptPolicy = ArticleSummaryPromptPolicy.GEMMA
        ) { part, onDelta ->
            engine.generate(
                systemPrompt = part.systemPrompt,
                userPrompt = part.userPrompt,
                maxOutputTokens = part.maxOutputTokens,
                temperature = 0.2,
                onDelta = onDelta
            )
        }
    }

    override suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = withContext(Dispatchers.Default) {
        val prompt = credibilityPromptBuilder.buildText(source)
            ?: return@withContext Result.failure(EmptyAiContentException())
        try {
            engine.generate(
                systemPrompt = prompt.systemMessage,
                userPrompt = prompt.userMessage,
                maxOutputTokens = CREDIBILITY_MAX_OUTPUT_TOKENS,
                temperature = 0.2
            ).mapCatching { raw ->
                val parsed = credibilityResponseParser.parse(raw)
                credibilityReportFactory.create(parsed, modelId, prompt.contentTruncated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemma credibility analysis failed", e)
            Result.failure(e)
        }
    }
}
