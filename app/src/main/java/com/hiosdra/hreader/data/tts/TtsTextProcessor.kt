package com.hiosdra.hreader.data.tts

import org.jsoup.Jsoup
import java.text.BreakIterator
import java.util.Locale

internal object TtsTextProcessor {
    fun fromHtml(title: String, html: String): List<String> {
        val body = Jsoup.parse(html).apply {
            select("script, style, nav, footer, aside, noscript, figure").remove()
        }.text()
        return chunks(listOf(title, body).filter { it.isNotBlank() }.joinToString(". "))
    }

    fun chunks(text: String, maxCharacters: Int = 700): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(normalized) }
        val result = mutableListOf<String>()
        var buffer = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = normalized.substring(start, end).trim()
            if (buffer.isNotEmpty() && buffer.length + sentence.length + 1 > maxCharacters) {
                result += buffer.toString()
                buffer = StringBuilder()
            }
            if (sentence.length > maxCharacters) {
                if (buffer.isNotEmpty()) {
                    result += buffer.toString()
                    buffer = StringBuilder()
                }
                result += sentence.chunked(maxCharacters)
            } else {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(sentence)
            }
            start = end
            end = iterator.next()
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }
}
