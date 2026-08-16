package com.hiosdra.hreader.core.application.ai

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val contextLength: Int,
    val isFree: Boolean
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return true
        return id.lowercase().contains(normalized) || displayName.lowercase().contains(normalized)
    }

    companion object {
        const val DEFAULT_ID = "openrouter/free"
    }
}

class MissingAiApiKeyException : Exception()

class EmptyAiContentException : Exception()

sealed interface SelectedModelStatus {
    data object Available : SelectedModelStatus
    data class Unavailable(val modelId: String) : SelectedModelStatus
    data object Unknown : SelectedModelStatus
}
