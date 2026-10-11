package com.hiosdra.hreader.core.application.ai

import kotlin.math.roundToInt

private const val DEFAULT_CONTEXT_LENGTH = 8_192
private const val CHARS_PER_TOKEN = 3
private const val CHUNK_CONTEXT_FRACTION = 0.40
private const val SUMMARY_CONTEXT_FRACTION = 0.15
private const val OUTPUT_CONTEXT_FRACTION = 0.12
private const val MIN_CONTEXT_LENGTH = 1_024
private const val MIN_OUTPUT_TOKENS = 96
private const val ARTICLE_SUMMARY_MAX_PERCENT = 10
private const val SENTENCE_BOUNDARY = "(?<=[.!?…。！？])\\s+"
private val SENTENCE_ENDINGS = setOf('.', '!', '?', '。', '！', '？', '…')
private val CJK_SCRIPTS = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL
)

internal enum class ArticleSummaryLengthUnit(val promptLabel: String) {
    WORDS("words"),
    CHARACTERS("characters")
}

internal data class ArticleSummaryLengthLimit(
    val value: Int,
    val unit: ArticleSummaryLengthUnit
)

data class ArticleSummaryPlan(
    val chunks: List<String>,
    val workingSummaryCharacterLimit: Int,
    val maxOutputTokens: Int
)

object ArticleSummaryPlanner {
    fun plan(content: String, contextLength: Int): ArticleSummaryPlan {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        val effectiveContext = contextLength
            .takeIf { it > 0 }
            ?.coerceAtLeast(MIN_CONTEXT_LENGTH)
            ?: DEFAULT_CONTEXT_LENGTH
        val chunkCharacterLimit = (effectiveContext * CHARS_PER_TOKEN * CHUNK_CONTEXT_FRACTION)
            .roundToInt()
        val summaryCharacterLimit = (effectiveContext * CHARS_PER_TOKEN * SUMMARY_CONTEXT_FRACTION)
            .roundToInt()

        return ArticleSummaryPlan(
            chunks = splitIntoChunks(normalized, chunkCharacterLimit),
            workingSummaryCharacterLimit = summaryCharacterLimit,
            maxOutputTokens = (effectiveContext * OUTPUT_CONTEXT_FRACTION)
                .roundToInt()
                .coerceIn(MIN_OUTPUT_TOKENS, 500)
        )
    }

    fun boundWorkingSummary(summary: String, characterLimit: Int): String {
        val normalized = summary.replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= characterLimit) return normalized

        val headLength = (characterLimit * 0.65).roundToInt().coerceAtLeast(1)
        val tailLength = (characterLimit - headLength - 1).coerceAtLeast(1)
        return normalized.take(headLength).trimEnd() + "…" + normalized.takeLast(tailLength).trimStart()
    }

    internal fun finalSummaryLengthLimit(content: String): ArticleSummaryLengthLimit {
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        val codePointCount = normalized.codePointCount(0, normalized.length)
        val cjkCodePointCount = normalized.countCjkCodePoints()
        val useCharacterLimit = normalized.none(Char::isWhitespace) ||
            cjkCodePointCount.toLong() * ARTICLE_SUMMARY_MAX_PERCENT >= codePointCount
        val unit = if (useCharacterLimit) {
            ArticleSummaryLengthUnit.CHARACTERS
        } else {
            ArticleSummaryLengthUnit.WORDS
        }
        val sourceLength = when (unit) {
            ArticleSummaryLengthUnit.WORDS -> normalized.split(Regex("\\s+")).count(String::isNotBlank)
            ArticleSummaryLengthUnit.CHARACTERS -> codePointCount
        }
        return ArticleSummaryLengthLimit(
            value = (sourceLength.toLong() * ARTICLE_SUMMARY_MAX_PERCENT / 100)
                .coerceAtLeast(1)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            unit = unit
        )
    }

    internal fun boundFinalSummary(summary: String, limit: ArticleSummaryLengthLimit): String {
        val normalized = summary.replace(Regex("\\s+"), " ").trim()
        if (limit.unit == ArticleSummaryLengthUnit.CHARACTERS) {
            return boundCharacterSummary(normalized, limit.value)
        }

        val words = normalized.split(' ').filter(String::isNotBlank)
        if (words.size <= limit.value) return normalized

        val selectedSentences = mutableListOf<String>()
        var selectedWords = 0
        for (sentence in normalized.split(Regex("(?<=[.!?…。！？])\\s+"))) {
            val sentenceWords = sentence.split(Regex("\\s+")).count(String::isNotBlank)
            if (selectedWords + sentenceWords > limit.value) break
            selectedSentences += sentence
            selectedWords += sentenceWords
        }
        if (selectedSentences.isNotEmpty()) return selectedSentences.joinToString(" ").trim()
        return words.take(limit.value).joinToString(" ").trimEnd(',', ';', ':')
    }

    private fun boundCharacterSummary(summary: String, characterLimit: Int): String {
        if (summary.codePointCount(0, summary.length) <= characterLimit) return summary

        val endIndex = summary.offsetByCodePoints(0, characterLimit)
        val boundedPrefix = summary.substring(0, endIndex)
        val sentenceEndIndex = boundedPrefix.indexOfLast { it in SENTENCE_ENDINGS }
        return if (sentenceEndIndex >= 0) {
            boundedPrefix.substring(0, sentenceEndIndex + 1).trimEnd()
        } else {
            boundedPrefix.trimEnd()
        }
    }

    private fun String.countCjkCodePoints(): Int {
        var count = 0
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (Character.UnicodeScript.of(codePoint) in CJK_SCRIPTS) {
                count++
            }
            index += Character.charCount(codePoint)
        }
        return count
    }

    private fun splitIntoChunks(content: String, characterLimit: Int): List<String> {
        if (content.isBlank()) return emptyList()
        if (content.length <= characterLimit) return listOf(content)

        val units = content
            .split(Regex(SENTENCE_BOUNDARY))
            .flatMap { unit -> splitOversizedUnit(unit, characterLimit) }
            .filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        units.forEach { unit ->
            val separatorLength = if (current.isEmpty()) 0 else 1
            if (current.length + separatorLength + unit.length <= characterLimit) {
                if (separatorLength > 0) current.append(' ')
                current.append(unit)
            } else {
                if (current.isNotEmpty()) chunks += current.toString().trim()
                current.clear()
                current.append(unit)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks
    }

    private fun splitOversizedUnit(unit: String, characterLimit: Int): List<String> {
        if (unit.length <= characterLimit) return listOf(unit.trim())

        val words = unit.trim().split(Regex("\\s+"))
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        words.forEach { word ->
            if (word.length > characterLimit) {
                if (current.isNotEmpty()) {
                    pieces += current.toString()
                    current.clear()
                }
                word.chunked(characterLimit).forEach(pieces::add)
                return@forEach
            }
            val separatorLength = if (current.isEmpty()) 0 else 1
            if (current.length + separatorLength + word.length <= characterLimit) {
                if (separatorLength > 0) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) pieces += current.toString()
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) pieces += current.toString()
        return pieces
    }
}
