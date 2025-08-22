package com.hiosdra.hreader.data.ai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val maxTokens: Int = 500,
    val temperature: Double = 0.7
) {
    fun toMap(): Map<String, Any> = mapOf(
        "model" to model,
        "messages" to messages.map { it.toMap() },
        "max_tokens" to maxTokens,
        "temperature" to temperature
    )
}

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,
    val content: String
) {
    fun toMap(): Map<String, String> = mapOf(
        "role" to role,
        "content" to content
    )
}

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
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

@JsonClass(generateAdapter = true)
data class ErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)