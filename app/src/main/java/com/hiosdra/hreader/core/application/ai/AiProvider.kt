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

data class GemmaModelDownloadPreflight(
    val availableBytes: Long,
    val requiredBytes: Long,
    val isLowRamDevice: Boolean
) {
    val hasEnoughStorage: Boolean
        get() = availableBytes >= requiredBytes
}

class GemmaModelInsufficientStorageException(
    val requiredBytes: Long,
    val availableBytes: Long
) : IllegalStateException()

const val GEMMA_DOWNLOAD_SAFETY_MARGIN_BYTES = 512L * 1024L * 1024L

internal fun requiredGemmaDownloadBytes(modelSizeBytes: Long, partialBytes: Long): Long {
    val resumableBytes = partialBytes.takeIf { it in 0..modelSizeBytes } ?: 0L
    return (modelSizeBytes - resumableBytes + GEMMA_DOWNLOAD_SAFETY_MARGIN_BYTES)
        .coerceAtLeast(0L)
}

sealed interface GemmaModelStatus {
    data object NotInstalled : GemmaModelStatus
    data class Downloading(val progress: Float) : GemmaModelStatus
    data object Available : GemmaModelStatus
    data class Failed(val message: String) : GemmaModelStatus
}
