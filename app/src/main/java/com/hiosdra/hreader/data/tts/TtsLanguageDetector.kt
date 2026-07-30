package com.hiosdra.hreader.data.tts

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import java.util.Locale

internal class TtsLanguageDetector(context: Context) {
    private val textClassifier = context
        .getSystemService(TextClassificationManager::class.java)
        .textClassifier

    fun detect(text: String): String {
        val detected = runCatching {
            val request = TextLanguage.Request.Builder(text.take(MAX_TEXT_LENGTH)).build()
            val result = textClassifier.detectLanguage(request)
            List(result.localeHypothesisCount) { index -> result.getLocale(index).language }
        }.getOrDefault(emptyList())
        return TtsLanguages.resolve(detected, Locale.getDefault().language)
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 1_000
    }
}

internal object SupertonicLanguages {
    val supported = listOf(
        "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
        "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
        "sk", "sl", "es", "sv", "tr", "uk", "vi"
    )
}

internal object TtsLanguages {
    val supported = (SupertonicLanguages.supported + "zh").sorted()

    fun resolve(detected: List<String>, fallback: String): String {
        return detected.asSequence()
            .map(::normalize)
            .firstOrNull(supported::contains)
            ?: normalize(fallback).takeIf(supported::contains)
            ?: "en"
    }

    fun compatibleModels(language: String): List<TtsModel> = when (normalize(language)) {
        "en" -> listOf(TtsModel.SUPERTONIC, TtsModel.KOKORO, TtsModel.ANDROID)
        "pl" -> listOf(TtsModel.SUPERTONIC, TtsModel.GOSIA, TtsModel.ANDROID)
        "zh" -> listOf(TtsModel.KOKORO, TtsModel.ANDROID)
        in SupertonicLanguages.supported -> listOf(TtsModel.SUPERTONIC, TtsModel.ANDROID)
        else -> listOf(TtsModel.ANDROID)
    }

    fun isCompatible(model: TtsModel, language: String): Boolean =
        model in compatibleModels(language)

    private fun normalize(language: String): String = when (language.lowercase(Locale.ROOT)) {
        "in" -> "id"
        else -> language.lowercase(Locale.ROOT)
    }
}
