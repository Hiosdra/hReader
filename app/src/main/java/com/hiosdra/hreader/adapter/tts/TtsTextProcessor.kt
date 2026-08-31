package com.hiosdra.hreader.adapter.tts

import org.jsoup.Jsoup
import java.text.BreakIterator
import java.util.Locale

internal object TtsTextProcessor {
    fun fromHtml(
        title: String,
        html: String,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS
    ): List<String> {
        val body = Jsoup.parse(html).apply {
            select("script, style, nav, footer, aside, noscript, figure").remove()
        }.text()
        return chunks(
            listOf(title, body).filter { it.isNotBlank() }.joinToString(". "),
            maxCharacters
        )
    }

    fun chunks(text: String, maxCharacters: Int = 350): List<String> {
        require(maxCharacters > 0) { "maxCharacters must be positive" }
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
                result += splitLongSentence(sentence, maxCharacters)
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

    private fun splitLongSentence(sentence: String, maxCharacters: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < sentence.length) {
            val remaining = sentence.length - start
            if (remaining <= maxCharacters) {
                result += sentence.substring(start).trim()
                break
            }
            val limit = start + maxCharacters
            val separator = sentence.lastIndexOf(' ', limit - 1)
            val end = if (separator > start) separator else limit
            result += sentence.substring(start, end).trim()
            start = end
            while (start < sentence.length && sentence[start].isWhitespace()) start++
        }
        return result
    }

    private const val DEFAULT_MAX_CHARACTERS = 350
}
