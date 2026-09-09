package com.hiosdra.hreader.core.application.tts

enum class TtsEngineFamily {
    SUPERTONIC,
    KOKORO,
    VITS,
    KITTEN,
    MATCHA,
    VOXCPM2,
    ANDROID
}

enum class TtsModel(
    val bundled: Boolean,
    val family: TtsEngineFamily
) {
    SUPERTONIC(false, TtsEngineFamily.SUPERTONIC),
    KOKORO(false, TtsEngineFamily.KOKORO),
    COQUI_PL_MAI_FEMALE(false, TtsEngineFamily.VITS),
    PIPER_LESSAC_HIGH(false, TtsEngineFamily.VITS),
    KITTEN_MINI(false, TtsEngineFamily.KITTEN),
    MATCHA_LJSPEECH(false, TtsEngineFamily.MATCHA),
    VOXCPM2(false, TtsEngineFamily.VOXCPM2),
    ANDROID(true, TtsEngineFamily.ANDROID);

    companion object {
        fun fromName(value: String?) = TtsModelCatalog.models.firstOrNull { it.name == value } ?: SUPERTONIC
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
    val kittenSpeaker: Int = 0,
    val vitsNoiseScale: Float = 0.667f,
    val vitsDurationNoiseScale: Float = 0.8f,
    val voxCpmCfg: Float = 2f,
    val voxCpmTimesteps: Int = 5
)

internal fun parseTtsLanguageOverrides(entries: Set<String>): Map<String, TtsModel> =
    entries.mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        val language = parts.firstOrNull()?.takeIf(String::isNotBlank)
        val model = parts.getOrNull(1)?.let { name ->
            TtsModelCatalog.models.firstOrNull { it.name == name }
        }
        if (language != null && model != null) language to model else null
    }.toMap()
