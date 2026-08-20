package com.hiosdra.hreader.adapter.ai

import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaArticleAiService
import com.hiosdra.hreader.adapter.ai.openrouter.ArticleAiService
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.application.port.out.ErrorReporter
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import kotlinx.coroutines.CancellationException

private const val AI_SUMMARY_COMPONENT = "ai_summary"

class ArticleAiGatewayRouter(
    private val openRouter: ArticleAiService,
    private val gemma: GemmaArticleAiService,
    private val errorReporter: ErrorReporter
) : ArticleAiGateway {
    override suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String
    ): Result<String> = try {
        gatewayFor(modelId)
            .generateArticleOverview(title, content, modelId)
            .onFailure(::reportSummaryFailure)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportSummaryFailure(e)
        Result.failure(e)
    }

    override suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = gatewayFor(modelId).analyzeCredibility(source, modelId)

    private fun gatewayFor(modelId: String): ArticleAiGateway =
        if (modelId == Gemma4E2bModel.MODEL_ID) gemma else openRouter

    private fun reportSummaryFailure(error: Throwable) {
        if (error !is EmptyAiContentException && error !is MissingAiApiKeyException) {
            errorReporter.captureException(error, AI_SUMMARY_COMPONENT)
        }
    }
}
