package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModelCatalog

internal object QwenTtsLanguage {
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
