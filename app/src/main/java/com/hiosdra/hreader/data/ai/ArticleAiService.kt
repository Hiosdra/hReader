package com.hiosdra.hreader.data.ai

import android.util.Log
import com.hiosdra.hreader.data.model.CredibilityReport
import com.hiosdra.hreader.data.model.CredibilitySource
import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "ArticleAiService"

internal class MissingApiKeyException : Exception()
internal class EmptyContentException : Exception()

class OpenRouterException(val code: Int?, message: String) : Exception(message)

class ArticleAiService(
    private val openRouterApiService: OpenRouterApiService,
    private val preferencesManager: PreferencesManager,
    private val credibilityPromptBuilder: CredibilityPromptBuilder,
    private val credibilityResponseParser: CredibilityResponseParser
) {
    private fun apiKeyOrNull(): String? = preferencesManager.getOpenRouterApiKey().takeIf { it.isNotBlank() }

    suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val plainText = stripToPlainText(content)
        if (plainText.isBlank()) {
            return@withContext Result.failure(EmptyContentException())
        }
        Log.d(TAG, "Generating overview with model: $modelId")
        executeChat(createSummaryRequest(title, plainText, modelId)).map { it.trim() }
    }

    suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = withContext(Dispatchers.IO) {
        val prompt = credibilityPromptBuilder.build(source, modelId)
            ?: return@withContext Result.failure(EmptyContentException())

        Log.d(TAG, "Analyzing credibility with model: $modelId")

        val firstAttempt = executeChat(prompt.request)
        val chatResult = firstAttempt.exceptionOrNull()
            ?.takeIf(::isUnsupportedResponseFormat)
            ?.let {
                Log.i(TAG, "Model $modelId rejected response_format, retrying without it")
                executeChat(prompt.request.copy(responseFormat = null))
            }
            ?: firstAttempt
        chatResult.mapCatching { raw ->
            val parsed = credibilityResponseParser.parse(raw)
            CredibilityReport(
                score = parsed.score,
                confidence = parsed.confidence,
                summary = parsed.summary,
                reasons = parsed.reasons,
                redFlags = parsed.redFlags,
                factors = parsed.factors,
                modelId = modelId,
                analyzedAt = Instant.now(),
                contentTruncated = prompt.contentTruncated
            )
        }.onFailure { Log.e(TAG, "Credibility analysis failed", it) }
    }

    private suspend fun executeChat(request: OpenRouterRequest): Result<String> {
        val apiKey = apiKeyOrNull() ?: return Result.failure(MissingApiKeyException())
        return try {
            val response = openRouterApiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val message = "API call failed: ${response.code()} - ${response.message()}"
                Log.e(TAG, message)
                return Result.failure(OpenRouterException(response.code(), message))
            }

            val body = response.body()
            val apiError = body?.error
            if (apiError != null) {
                Log.e(TAG, "API Error: ${apiError.message}")
                return Result.failure(OpenRouterException(null, "AI Error: ${apiError.message}"))
            }

            val content = body?.choices?.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                return Result.failure(OpenRouterException(null, "The model returned an empty response."))
            }
            Result.success(content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat completion failed", e)
            Result.failure(e)
        }
    }

    private fun isUnsupportedResponseFormat(error: Throwable): Boolean {
        val openRouterError = error as? OpenRouterException ?: return false
        if (openRouterError.code?.let { it == 400 || it == 404 || it == 422 } == true) return true
        val message = openRouterError.message.orEmpty().lowercase()
        return "response_format" in message || "json mode" in message
    }

    private fun createSummaryRequest(
        title: String,
        content: String,
        modelId: String
    ): OpenRouterRequest {
        val systemMessage = ChatMessage(
            role = "system",
            content = """
You are a helpful assistant that creates concise, informative overviews of articles.
Provide a summary in 2-3 sentences that captures the main points and key insights.
Language of the summary should be the same as the language of the article.

DO NOT INCLUDE "Here's your summary" or "Here's your overview" in the response.
                """.trimIndent()
        )

        val userMessage = ChatMessage(
            role = "user",
            content = """
Please provide a brief overview of this article:
Title: $title
Content: $content
""".trimIndent()
        )

        return OpenRouterRequest(
            model = modelId,
            messages = listOf(systemMessage, userMessage),
            maxTokens = 500,
            temperature = 0.5
        )
    }
}
