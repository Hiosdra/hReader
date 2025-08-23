package com.hiosdra.hreader.data.ai

import android.util.Log
import com.hiosdra.hreader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArticleAiService(
    private val openRouterApiService: OpenRouterApiService
) {
    private val apiKey = BuildConfig.OPENROUTER_KEY

    suspend fun generateArticleOverview(
        title: String,
        content: String,
        model: AiModel
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing OpenRouter API key"))
        try {
            val cleanContent = cleanArticleContent(content)
            val request = createSummaryRequest(title, cleanContent, model)

            Log.d("ArticleAiService", "Generating overview with model: ${model.modelId}")

            val response = openRouterApiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.error != null) {
                    Log.e("ArticleAiService", "API Error: ${body.error.message}")
                    Result.failure(Exception("AI Error: ${body.error.message}"))
                } else {
                    val overview = body?.choices?.firstOrNull()?.message?.content
                        ?: throw Exception("No content in response")
                    Log.d("ArticleAiService", "Successfully generated overview")
                    Result.success(overview.trim())
                }
            } else {
                val errorMsg = "API call failed: ${response.code()} - ${response.message()}"
                Log.e("ArticleAiService", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ArticleAiService", "Error generating overview", e)
            Result.failure(e)
        }
    }

    suspend fun generateCredibilityScore(
        title: String,
        content: String,
        model: AiModel
    ): Result<Float> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Missing OpenRouter API key"))
        try {
            val cleanContent = cleanArticleContent(content)
            val request = createCredibilityRequest(title, cleanContent, model)

            Log.d("ArticleAiService", "Generating credibility score with model: ${model.modelId}")

            val response = openRouterApiService.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.error != null) {
                    Log.e("ArticleAiService", "API Error: ${body.error.message}")
                    Result.failure(Exception("AI Error: ${body.error.message}"))
                } else {
                    val scoreText = body?.choices?.firstOrNull()?.message?.content
                        ?: throw Exception("No content in response")
                    
                    val score = parseCredibilityScore(scoreText.trim())
                    Log.d("ArticleAiService", "Successfully generated credibility score: $score")
                    Result.success(score)
                }
            } else {
                val errorMsg = "API call failed: ${response.code()} - ${response.message()}"
                Log.e("ArticleAiService", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("ArticleAiService", "Error generating credibility score", e)
            Result.failure(e)
        }
    }

    private fun cleanArticleContent(content: String): String {
        return content
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createSummaryRequest(
        title: String,
        content: String,
        model: AiModel
    ): OpenRouterRequest {
        val systemMessage = ChatMessage(
            role = "system",
            content = """
You are a helpful assistant that creates concise, informative overviews of articles. Provide a summary in 2-3 sentences that captures the main points and key insights.

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
            model = model.modelId,
            messages = listOf(systemMessage, userMessage),
            maxTokens = 500,
            temperature = 0.5
        )
    }

    private fun createCredibilityRequest(
        title: String,
        content: String,
        model: AiModel
    ): OpenRouterRequest {
        val systemMessage = ChatMessage(
            role = "system",
            content = """
You are an expert fact-checker and media analyst. Analyze the given article for credibility and trustworthiness.

Provide a credibility score from 0.0 to 1.0 where:
- 0.0-0.3: High risk of misinformation (sensationalized, unsubstantiated claims, propaganda)
- 0.4-0.6: Moderate credibility concerns (some bias, partial information, requires verification)
- 0.7-1.0: High credibility (well-sourced, factual, balanced reporting)

Consider these factors:
- Source credibility and citations
- Language tone and sensationalism
- Factual claims vs. opinion
- Balanced perspective vs. heavy bias
- Evidence provided for claims

Respond with ONLY the numerical score (e.g., 0.73). Do not include explanations or additional text.
                """.trimIndent()
        )

        val userMessage = ChatMessage(
            role = "user",
            content = """
Analyze this article for credibility:
Title: $title
Content: $content
""".trimIndent()
        )

        return OpenRouterRequest(
            model = model.modelId,
            messages = listOf(systemMessage, userMessage),
            maxTokens = 10,
            temperature = 0.3
        )
    }

    private fun parseCredibilityScore(scoreText: String): Float {
        return try {
            val cleanText = scoreText.replace(Regex("[^0-9.]"), "")
            val score = cleanText.toFloatOrNull()
            when {
                score == null -> 0.5f // Default neutral score if parsing fails
                score < 0f -> 0f
                score > 1f -> 1f
                else -> score
            }
        } catch (e: Exception) {
            Log.w("ArticleAiService", "Failed to parse credibility score: $scoreText", e)
            0.5f // Default neutral score
        }
    }
}
