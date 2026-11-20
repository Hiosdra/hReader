package com.hiosdra.hreader.adapter.tts

internal object TtsParagraphNavigation {
    const val RESTART_PARAGRAPH_THRESHOLD_MS = 3_000L

    fun firstChunkIndex(chunks: List<TtsChunk>, paragraphIndex: Int): Int? =
        chunks.indexOfFirst { it.paragraphIndex >= paragraphIndex }.takeIf { it >= 0 }

    fun nextParagraph(chunks: List<TtsChunk>, currentParagraph: Int): Int? =
        chunks.firstOrNull { it.paragraphIndex > currentParagraph }?.paragraphIndex

    fun previousParagraph(
        chunks: List<TtsChunk>,
        currentParagraph: Int,
        elapsedInParagraphMs: Long
    ): Int {
        if (elapsedInParagraphMs >= RESTART_PARAGRAPH_THRESHOLD_MS) return currentParagraph
        return chunks.lastOrNull { it.paragraphIndex < currentParagraph }?.paragraphIndex
            ?: currentParagraph
    }
}
