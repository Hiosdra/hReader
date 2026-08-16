package com.hiosdra.hreader.adapter.ai.openrouter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @field:Json(name = "max_tokens") val maxTokens: Int,
    val temperature: Double,
    @field:Json(name = "response_format") val responseFormat: ResponseFormat? = null
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    val type: String
) {
    companion object {
        val JsonObject = ResponseFormat("json_object")
    }
}

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    val choices: List<Choice>,
    val usage: Usage? = null,
    val error: ErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: ChatMessage,
    val index: Int,
    @field:Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class Usage(
    @field:Json(name = "prompt_tokens") val promptTokens: Int,
    @field:Json(name = "completion_tokens") val completionTokens: Int,
    @field:Json(name = "total_tokens") val totalTokens: Int
)

@JsonClass(generateAdapter = true)
data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @field:Json(name = "context_length") val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null
)
