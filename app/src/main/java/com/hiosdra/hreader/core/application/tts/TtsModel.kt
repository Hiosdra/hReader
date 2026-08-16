package com.hiosdra.hreader.core.application.tts

enum class TtsModel(val bundled: Boolean) {
    SUPERTONIC(false),
    KOKORO(false),
    GOSIA(false),
    ANDROID(true);

    companion object {
        fun fromName(value: String?) = entries.firstOrNull { it.name == value } ?: SUPERTONIC
    }
}

sealed interface TtsModelStatus {
    data object Available : TtsModelStatus
    data object NotInstalled : TtsModelStatus
    data class Downloading(val progress: Float) : TtsModelStatus
    data class Failed(val message: String) : TtsModelStatus
}

data class TtsAdvancedSettings(
    val numThreads: Int = 4,
    val silenceScale: Float = 0.2f,
    val supertonicSpeaker: Int = 0,
    val supertonicSteps: Int = 8,
    val kokoroSpeaker: Int = 0,
    val gosiaNoiseScale: Float = 0.667f,
    val gosiaDurationNoiseScale: Float = 0.8f
)

internal fun parseTtsLanguageOverrides(entries: Set<String>): Map<String, TtsModel> =
    entries.mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        val language = parts.firstOrNull()?.takeIf(String::isNotBlank)
        val model = parts.getOrNull(1)?.let { name ->
            TtsModel.entries.firstOrNull { it.name == name }
        }
        if (language != null && model != null) language to model else null
    }.toMap()
