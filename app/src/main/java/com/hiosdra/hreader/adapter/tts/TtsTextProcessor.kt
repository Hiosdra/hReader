package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsTextDocumentFactory

internal object TtsTextProcessor {
    fun fromHtml(title: String, html: String): List<String> =
        TtsTextDocumentFactory.chunks(TtsTextDocumentFactory.fromHtml(title, html).text)

    fun chunks(text: String, maxCharacters: Int = 350): List<String> =
        TtsTextDocumentFactory.chunks(text, maxCharacters)
}
