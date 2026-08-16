package com.hiosdra.hreader.adapter.tts

import android.content.Context
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.TtsLanguages
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
