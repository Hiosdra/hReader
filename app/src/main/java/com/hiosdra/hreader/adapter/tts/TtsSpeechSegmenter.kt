package com.hiosdra.hreader.adapter.tts

internal data class TtsSpeechSegment(
    val chunkIndex: Int,
    val text: String,
    val trailingSilenceMillis: Int
)

internal object TtsSpeechSegmenter {
    private val RAMP_LIMITS = listOf(60, 120, 200)
    private const val MIN_SPLIT_INDEX = 20
    private const val CLAUSE_PUNCTUATION = ",;:—–"

    fun segments(chunks: List<TtsChunk>, startIndex: Int): Sequence<TtsSpeechSegment> = sequence {
        var emitted = 0
        for (chunkIndex in startIndex until chunks.size) {
            val pieces = piecesFor(chunks[chunkIndex].text, emitted)
            val trailingSilence = chunks.getOrNull(chunkIndex + 1)?.boundaryBefore?.pauseBeforeMillis ?: 0
            pieces.forEachIndexed { index, piece ->
                yield(
                    TtsSpeechSegment(
                        chunkIndex = chunkIndex,
                        text = piece,
                        trailingSilenceMillis = if (index == pieces.lastIndex) trailingSilence else 0
                    )
                )
            }
            emitted += pieces.size
        }
    }

    private fun piecesFor(text: String, emittedBefore: Int): List<String> {
        if (emittedBefore >= RAMP_LIMITS.size) return listOf(text)
        val sentences = ArrayDeque(TtsTextProcessor.sentences(text))
        if (sentences.isEmpty()) return listOf(text)
        val pieces = mutableListOf<String>()
        while (sentences.isNotEmpty()) {
            val limit = RAMP_LIMITS.getOrNull(emittedBefore + pieces.size)
            if (limit == null) {
                pieces += sentences.joinToString(" ")
                break
            }
            pieces += nextPiece(sentences, limit)
        }
        return pieces
    }

    private fun nextPiece(sentences: ArrayDeque<String>, limit: Int): String {
        val first = sentences.removeFirst()
        if (first.length > limit) {
            val splitIndex = splitIndex(first, limit) ?: return first
            sentences.addFirst(first.substring(splitIndex).trim())
            return first.substring(0, splitIndex).trim()
        }
        val piece = StringBuilder(first)
        while (sentences.isNotEmpty() && piece.length + 1 + sentences.first().length <= limit) {
            piece.append(' ').append(sentences.removeFirst())
        }
        return piece.toString()
    }

    private fun splitIndex(sentence: String, limit: Int): Int? {
        val searchEnd = minOf(limit, sentence.length - 1)
        if (searchEnd < MIN_SPLIT_INDEX) return null
        val punctuation = (searchEnd - 1 downTo MIN_SPLIT_INDEX).firstOrNull {
            sentence[it] in CLAUSE_PUNCTUATION && sentence[it + 1].isWhitespace()
        }
        if (punctuation != null) return punctuation + 1
        val whitespace = sentence.lastIndexOf(' ', searchEnd)
        return whitespace.takeIf { it >= MIN_SPLIT_INDEX }
    }
}
