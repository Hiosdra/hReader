package com.hiosdra.hreader.core.application.ai

data class AiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val contextLength: Int,
    val isFree: Boolean,
    val provider: AiProvider = AiProvider.OPENROUTER
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return true
        return id.lowercase().contains(normalized) || displayName.lowercase().contains(normalized)
    }

    companion object {
        const val DEFAULT_ID = "openrouter/free"
        const val GEMMA_4_E2B_ID = "local/gemma-4-e2b"

        fun providerFor(modelId: String): AiProvider =
            if (modelId == GEMMA_4_E2B_ID) AiProvider.GEMMA_LOCAL else AiProvider.OPENROUTER
    }
}

class MissingAiApiKeyException : Exception()

class EmptyAiContentException : Exception()

open class AiProviderException(
    val statusCode: Int?,
    message: String
) : Exception(message)

class GemmaModelNotInstalledException : Exception()

sealed interface SelectedModelStatus {
    data object Available : SelectedModelStatus
    data class Unavailable(val modelId: String) : SelectedModelStatus
    data object Unknown : SelectedModelStatus
}
