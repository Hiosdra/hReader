package com.hiosdra.hreader.adapter.ai.openrouter

import android.util.Log
import com.hiosdra.hreader.adapter.ai.common.CredibilityReportFactory
import com.hiosdra.hreader.adapter.ai.common.CredibilityPromptBuilder
import com.hiosdra.hreader.adapter.ai.common.CredibilityResponseParser
import com.hiosdra.hreader.adapter.ai.common.stripToPlainText
import com.hiosdra.hreader.core.application.ai.ArticleAiPhase
import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.application.ai.ArticleSummaryPart
import com.hiosdra.hreader.core.application.ai.ArticleSummaryPipeline
import com.hiosdra.hreader.core.application.ai.EmptyAiContentException
import com.hiosdra.hreader.core.application.ai.MissingAiApiKeyException
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ArticleAiService"
private const val DEFAULT_CONTEXT_LENGTH = 4_096
private const val CREDIBILITY_MAX_OUTPUT_TOKENS = 1_500
private const val CREDIBILITY_TEMPERATURE = 0.2

class ArticleAiService(
    private val openRouterApiService: OpenRouterApiService,
    private val streamingClient: OpenRouterStreamingClient,
    private val preferencesManager: AiPreferences,
    private val credibilityPromptBuilder: CredibilityPromptBuilder,
    private val credibilityResponseParser: CredibilityResponseParser,
    private val credibilityReportFactory: CredibilityReportFactory,
    private val aiModelCatalog: AiModelCatalog,
    moshi: Moshi
) : ArticleAiGateway {
    private val errorAdapter = moshi.adapter(OpenRouterErrorEnvelope::class.java)
    private val summaryPipeline = ArticleSummaryPipeline()

    private fun apiKeyOrNull(): String? = preferencesManager.getOpenRouterApiKey().takeIf { it.isNotBlank() }

    override suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String,
        onProgress: suspend (ArticleAiProgress) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        onProgress(ArticleAiProgress(ArticleAiPhase.PREPARING))
        val plainText = stripToPlainText(content)
        if (plainText.isBlank()) {
            return@withContext Result.failure(EmptyAiContentException())
        }
        if (apiKeyOrNull() == null) {
            return@withContext Result.failure(MissingAiApiKeyException())
        }

        Log.d(TAG, "Generating overview with model: $modelId")
        onProgress(ArticleAiProgress(ArticleAiPhase.LOADING_MODEL))
        val contextLength = resolveContextLength(modelId)
        summaryPipeline.generate(
            title = stripToPlainText(title).trim(),
            content = plainText,
            modelId = modelId,
            contextLength = contextLength,
            onProgress = onProgress
        ) { part, onDelta ->
            executeChatStreaming(createSummaryRequest(part, modelId), onDelta)
        }
    }

    override suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = withContext(Dispatchers.IO) {
        val prompt = credibilityPromptBuilder.buildText(source)
            ?: return@withContext Result.failure(EmptyAiContentException())

        Log.d(TAG, "Analyzing credibility with model: $modelId")

        val request = OpenRouterRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = prompt.systemMessage),
                ChatMessage(role = "user", content = prompt.userMessage)
            ),
            maxTokens = CREDIBILITY_MAX_OUTPUT_TOKENS,
            temperature = CREDIBILITY_TEMPERATURE,
            responseFormat = ResponseFormat.JsonObject
        )

        val firstAttempt = executeChat(request)
        val chatResult = firstAttempt.exceptionOrNull()
            ?.takeIf(::isUnsupportedResponseFormat)
            ?.let {
                Log.i(TAG, "Model $modelId rejected response_format, retrying without it")
                executeChat(request.copy(responseFormat = null))
            }
            ?: firstAttempt
        chatResult.mapCatching { raw ->
            val parsed = credibilityResponseParser.parse(raw)
            credibilityReportFactory.create(parsed, modelId, prompt.contentTruncated)
        }.onFailure { Log.e(TAG, "Credibility analysis failed", it) }
    }

    private suspend fun resolveContextLength(modelId: String): Int = try {
        aiModelCatalog.getModels()
            .firstOrNull { it.id == modelId }
            ?.contextLength
            ?.takeIf { it > 0 }
            ?: DEFAULT_CONTEXT_LENGTH
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load context length for model $modelId", e)
        DEFAULT_CONTEXT_LENGTH
    }

    private suspend fun executeChat(request: OpenRouterRequest): Result<String> {
        val apiKey = apiKeyOrNull() ?: return Result.failure(MissingAiApiKeyException())
        return try {
            val response = openRouterApiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val providerMessage = response.errorBody()?.string()
                    ?.let { providerErrorMessage(it, errorAdapter) }
                    ?.takeIf(String::isNotBlank)
                val message = "API call failed: ${response.code()} - ${providerMessage ?: response.message()}"
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

    private suspend fun executeChatStreaming(
        request: OpenRouterRequest,
        onDelta: suspend (String) -> Unit
    ): Result<String> {
        val apiKey = apiKeyOrNull() ?: return Result.failure(MissingAiApiKeyException())
        return try {
            val content = streamingClient.stream(
                authorization = "Bearer $apiKey",
                request = request,
                onDelta = onDelta
            )
            Result.success(content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming chat completion failed", e)
            Result.failure(e)
        }
    }

    private fun isUnsupportedResponseFormat(error: Throwable): Boolean {
        val openRouterError = error as? OpenRouterException ?: return false
        if (openRouterError.code?.let { it == 400 || it == 404 || it == 422 } == true) return true
        val message = openRouterError.message.orEmpty().lowercase()
        return "response_format" in message || "json mode" in message
    }

    private fun createSummaryRequest(part: ArticleSummaryPart, modelId: String): OpenRouterRequest =
        OpenRouterRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = part.systemPrompt),
                ChatMessage(role = "user", content = part.userPrompt)
            ),
            maxTokens = part.maxOutputTokens,
            temperature = 0.2,
            stream = true
        )
}
