package com.hiosdra.hreader.core.application.tts

enum class TtsEngineFamily {
    SUPERTONIC,
    KOKORO,
    VITS,
    KITTEN,
    MATCHA,
    QWEN_CPP,
    MNN,
    ANDROID
}

enum class TtsModel(
    val bundled: Boolean,
    val family: TtsEngineFamily
) {
    SUPERTONIC(false, TtsEngineFamily.SUPERTONIC),
    KOKORO(false, TtsEngineFamily.KOKORO),
    GOSIA(false, TtsEngineFamily.VITS),
    PIPER_LESSAC_HIGH(false, TtsEngineFamily.VITS),
    KITTEN_MINI(false, TtsEngineFamily.KITTEN),
    MATCHA_LJSPEECH(false, TtsEngineFamily.MATCHA),
    QWEN_CPP_0_6B_BASE_Q4(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_0_6B_BASE_Q8(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_0_6B_CUSTOM_VOICE_Q4(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_0_6B_CUSTOM_VOICE_Q8(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_BASE_Q4(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_BASE_Q8(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_CUSTOM_VOICE_Q4(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_CUSTOM_VOICE_Q8(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_VOICE_DESIGN_Q4(false, TtsEngineFamily.QWEN_CPP),
    QWEN_CPP_1_7B_VOICE_DESIGN_Q8(false, TtsEngineFamily.QWEN_CPP),
    MNN_0_6B_BASE_INT8(false, TtsEngineFamily.MNN),
    MNN_0_6B_BASE_FP16(false, TtsEngineFamily.MNN),
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
    val vitsDurationNoiseScale: Float = 0.8f
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
