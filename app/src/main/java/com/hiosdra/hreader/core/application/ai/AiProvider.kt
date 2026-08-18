package com.hiosdra.hreader.core.application.ai

enum class AiProvider {
    OPENROUTER,
    GEMMA_LOCAL
}

enum class GemmaBackend {
    AUTO,
    CPU,
    GPU,
    NPU;

    companion object {
        fun fromName(value: String?): GemmaBackend =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

sealed interface GemmaModelStatus {
    data object NotInstalled : GemmaModelStatus
    data class Downloading(val progress: Float) : GemmaModelStatus
    data object Available : GemmaModelStatus
    data class Failed(val message: String) : GemmaModelStatus
}
