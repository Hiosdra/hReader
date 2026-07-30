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
        return SupertonicLanguages.resolve(detected, Locale.getDefault().language)
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

    fun resolve(detected: List<String>, fallback: String): String {
        return detected.asSequence()
            .map(::normalize)
            .firstOrNull(supported::contains)
            ?: normalize(fallback).takeIf(supported::contains)
            ?: "en"
    }

    private fun normalize(language: String): String = when (language.lowercase(Locale.ROOT)) {
        "in" -> "id"
        else -> language.lowercase(Locale.ROOT)
    }
}
