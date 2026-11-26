package com.hiosdra.hreader.core.application.tts

enum class TtsEngineFamily {
    SUPERTONIC,
    KOKORO,
    VITS,
    ANDROID
}

enum class TtsModel(
    val bundled: Boolean,
    val family: TtsEngineFamily
) {
    SUPERTONIC(false, TtsEngineFamily.SUPERTONIC),
    KOKORO(false, TtsEngineFamily.KOKORO),
    COQUI_PL_MAI_FEMALE(false, TtsEngineFamily.VITS),
    ANDROID(true, TtsEngineFamily.ANDROID);

    companion object {
        private val retiredModelReplacements = mapOf(
            "KOKORO_V1_0" to KOKORO,
            "KITTEN_MINI" to KOKORO
        )

        fun fromStoredName(value: String?): TtsModel? =
            TtsModelCatalog.models.firstOrNull { it.name == value } ?: retiredModelReplacements[value]

        fun fromName(value: String?) = fromStoredName(value) ?: SUPERTONIC
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
    val kokoroSpeaker: Int = TtsModelCatalog.DEFAULT_KOKORO_VOICE,
    val vitsNoiseScale: Float = 0.667f,
    val vitsDurationNoiseScale: Float = 0.8f
)

internal fun parseTtsLanguageOverrides(entries: Set<String>): Map<String, TtsModel> =
    entries.mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        val language = parts.firstOrNull()?.takeIf(String::isNotBlank)
        val model = parts.getOrNull(1)?.let(TtsModel::fromStoredName)
        if (language != null && model != null) language to model else null
    }.toMap()
