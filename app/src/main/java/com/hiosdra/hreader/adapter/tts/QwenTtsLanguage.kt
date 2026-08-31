package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal object QwenTtsLanguage {
    fun cppId(language: String): Int = when (TtsModelCatalog.normalizeLanguage(language)) {
        "en" -> 2050
        "ru" -> 2069
        "zh" -> 2055
        "ja" -> 2058
        "ko" -> 2064
        "de" -> 2053
        "fr" -> 2061
        "es" -> 2054
        "it" -> 2070
        "pt" -> 2071
        else -> throw IllegalArgumentException("Unsupported Qwen3-TTS language: $language")
    }

    fun mnnName(language: String): String = when (TtsModelCatalog.normalizeLanguage(language)) {
        "en" -> "english"
        "ru" -> "russian"
        "zh" -> "chinese"
        "ja" -> "japanese"
        "ko" -> "korean"
        "de" -> "german"
        "fr" -> "french"
        "es" -> "spanish"
        "it" -> "italian"
        "pt" -> "portuguese"
        else -> throw IllegalArgumentException("Unsupported Qwen3-TTS language: $language")
    }
}
