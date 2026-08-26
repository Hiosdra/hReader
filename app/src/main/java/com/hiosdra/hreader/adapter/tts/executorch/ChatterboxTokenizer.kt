package com.hiosdra.hreader.adapter.tts.executorch

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.text.Normalizer
import java.util.Locale

internal class ChatterboxTokenizer internal constructor(json: String) {
    private val tokenIds: Map<String, Long>
    private val mergeRanks: Map<Merge, Int>
    private val specialTokens: List<SpecialToken>

    init {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val root = Moshi.Builder().build().adapter<Map<String, Any?>>(mapType).fromJson(json)
            ?: error("Empty Chatterbox tokenizer")
        val model = root["model"] as? Map<*, *> ?: error("Missing Chatterbox BPE model")
        tokenIds = (model["vocab"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            if (key is String && value is Number) key to value.toLong() else null
        }?.toMap() ?: error("Missing Chatterbox vocabulary")
        mergeRanks = (model["merges"] as? List<*>)?.let { merges ->
            buildMap {
                merges.forEachIndexed { index, merge ->
                    val parts = merge.toString().split(' ', limit = 2)
                    check(parts.size == 2) { "Invalid Chatterbox BPE merge" }
                    put(Merge(parts[0], parts[1]), index)
                }
            }
        } ?: error("Missing Chatterbox BPE merges")
        specialTokens = (root["added_tokens"] as? List<*>)
            ?.mapNotNull { token ->
                val value = token as? Map<*, *> ?: return@mapNotNull null
                if (value["special"] != true) return@mapNotNull null
                val content = value["content"] as? String ?: return@mapNotNull null
                val id = (value["id"] as? Number)?.toLong() ?: return@mapNotNull null
                SpecialToken(content, id)
            }
            .orEmpty()
            .sortedByDescending(SpecialToken::contentLength)
    }

    constructor(file: File) : this(file.readText())

    fun encode(text: String, language: String): LongArray {
        val languageCode = language.lowercase(Locale.ROOT).substringBefore('-')
        val languageToken = tokenIds["[$languageCode]"]
            ?: error("Chatterbox tokenizer has no language token for $language")
        val normalized = Normalizer.normalize(
            text.lowercase(Locale.ROOT),
            Normalizer.Form.NFKD
        )
        val result = ArrayList<Long>(normalized.length + 1)
        result += languageToken
        var index = 0
        while (index < normalized.length) {
            if (normalized[index] == ' ') {
                result += tokenId("[SPACE]")
                index++
                continue
            }
            val special = specialTokens.firstOrNull { normalized.startsWith(it.content, index) }
            if (special != null) {
                result += special.id
                index += special.contentLength
                continue
            }
            val segmentStart = index
            while (index < normalized.length && normalized[index] != ' ' &&
                specialTokens.none { normalized.startsWith(it.content, index) }
            ) {
                if (Character.isWhitespace(normalized.codePointAt(index))) break
                index += Character.charCount(normalized.codePointAt(index))
            }
            if (segmentStart == index) {
                index += Character.charCount(normalized.codePointAt(index))
            } else {
                encodeSegment(normalized.substring(segmentStart, index), result)
            }
        }
        return result.toLongArray()
    }

    private fun encodeSegment(segment: String, result: MutableList<Long>) {
        var index = 0
        while (index < segment.length) {
            val start = index
            val word = isWord(segment.codePointAt(index))
            while (index < segment.length && isWord(segment.codePointAt(index)) == word) {
                index += Character.charCount(segment.codePointAt(index))
            }
            encodePiece(segment.substring(start, index), result)
        }
    }

    private fun encodePiece(piece: String, result: MutableList<Long>) {
        val symbols = ArrayList<String>()
        var index = 0
        while (index < piece.length) {
            val codePoint = piece.codePointAt(index)
            symbols += String(Character.toChars(codePoint))
            index += Character.charCount(codePoint)
        }
        while (symbols.size > 1) {
            var bestIndex = -1
            var bestRank = Int.MAX_VALUE
            for (index in 0 until symbols.lastIndex) {
                val rank = mergeRanks[Merge(symbols[index], symbols[index + 1])]
                if (rank != null && rank < bestRank) {
                    bestIndex = index
                    bestRank = rank
                }
            }
            if (bestIndex < 0) break
            symbols[bestIndex] += symbols.removeAt(bestIndex + 1)
        }
        symbols.forEach { symbol -> result += tokenIds[symbol] ?: tokenId("[UNK]") }
    }

    private fun tokenId(token: String): Long = tokenIds[token]
        ?: error("Chatterbox tokenizer has no token $token")

    private fun isWord(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return Character.isLetterOrDigit(codePoint) ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.CONNECTOR_PUNCTUATION.toInt()
    }

    private data class Merge(val first: String, val second: String)

    internal data class SpecialToken(
        val content: String,
        val id: Long
    ) {
        val contentLength: Int get() = content.length
    }
}
