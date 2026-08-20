package com.hiosdra.hreader.adapter.ai.gemma

import android.util.Log
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.adapter.ai.common.stripToPlainText
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GemmaArticleAi"
private const val SUMMARY_MAX_OUTPUT_TOKENS = 500
private const val CREDIBILITY_MAX_OUTPUT_TOKENS = 1500

class GemmaArticleAiService(
    private val engine: GemmaInferenceEngine,
    private val credibilityPromptBuilder: CredibilityPromptBuilder,
    private val credibilityResponseParser: CredibilityResponseParser,
    private val credibilityReportFactory: CredibilityReportFactory
) : ArticleAiGateway {
    override suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String
    ): Result<String> = withContext(Dispatchers.Default) {
        val plainText = stripToPlainText(content)
        if (plainText.isBlank()) return@withContext Result.failure(EmptyAiContentException())
        engine.generate(
            systemPrompt = """
                You create concise, informative article overviews.
                Return 2-3 sentences in the same language as the article.
                Do not add an introduction such as \"Here is the summary\".
            """.trimIndent(),
            userPrompt = """
                Provide a brief overview of this article.
                Title: $title
                Content: $plainText
            """.trimIndent(),
            maxOutputTokens = SUMMARY_MAX_OUTPUT_TOKENS,
            temperature = 0.5
        ).map { it.trim() }
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
