package com.hiosdra.hreader.data.tts

enum class TtsModel(
    val displayName: String,
    val description: String,
    val bundled: Boolean
) {
    SUPERTONIC("Supertonic 3", "Multilingual neural voice · 129 MB download", false),
    KOKORO("Kokoro", "English neural voices · 140 MB download", false),
    GOSIA("Gosia", "Polish neural voice · 21 MB download", false),
    ANDROID("Android TTS", "System voice · always available", true);

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
    val kokoroSpeaker: Int = 3,
    val gosiaNoiseScale: Float = 0.667f,
    val gosiaDurationNoiseScale: Float = 0.8f
)
