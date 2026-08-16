package com.hiosdra.hreader.core.application.tts

import java.util.Locale

object TtsLanguages {
    private val supertonicSupported = listOf(
        "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
        "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
        "sk", "sl", "es", "sv", "tr", "uk", "vi"
    )

    val supported = (supertonicSupported + "zh").sorted()

    fun resolve(detected: List<String>, fallback: String): String = detected.asSequence()
        .map(::normalize)
        .firstOrNull(supported::contains)
        ?: normalize(fallback).takeIf(supported::contains)
        ?: "en"

    fun compatibleModels(language: String): List<TtsModel> = when (normalize(language)) {
        "en" -> listOf(TtsModel.SUPERTONIC, TtsModel.KOKORO, TtsModel.ANDROID)
        "pl" -> listOf(TtsModel.SUPERTONIC, TtsModel.GOSIA, TtsModel.ANDROID)
        "zh" -> listOf(TtsModel.KOKORO, TtsModel.ANDROID)
        in supertonicSupported -> listOf(TtsModel.SUPERTONIC, TtsModel.ANDROID)
        else -> listOf(TtsModel.ANDROID)
    }

    fun isCompatible(model: TtsModel, language: String): Boolean = model in compatibleModels(language)

    private fun normalize(language: String): String = when (language.lowercase(Locale.ROOT)) {
        "in" -> "id"
        else -> language.lowercase(Locale.ROOT)
    }
}
