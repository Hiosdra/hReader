package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSpeechSegmenterTest {
    @Test
    fun `starts with a short segment split at a clause boundary`() {
        val chunks = listOf(
            TtsChunk(
                text = "The first paragraph opens with a long clause, which then continues for quite a while " +
                    "before it finally reaches its conclusion. Then another sentence follows.",
                boundaryBefore = TtsChunkBoundary.START
            )
        )

        val segments = TtsSpeechSegmenter.segments(chunks, 0).toList()

        assertEquals("The first paragraph opens with a long clause,", segments.first().text)
        assertTrue(segments.first().text.length <= 60)
        assertEquals(chunks.single().text, segments.joinToString(" ") { it.text })
    }

    @Test
    fun `keeps short sentences together within the ramp limit`() {
        val chunks = listOf(TtsChunk("One. Two. Three. Four.", TtsChunkBoundary.START))

        assertEquals(
            listOf("One. Two. Three. Four."),
            TtsSpeechSegmenter.segments(chunks, 0).map { it.text }.toList()
        )
    }

    @Test
    fun `reads chunks whole once the ramp is over`() {
        val chunks = (0 until 5).map { index ->
            TtsChunk(
                text = "Sentence number $index is here. And it has a second sentence too.",
                boundaryBefore = if (index == 0) TtsChunkBoundary.START else TtsChunkBoundary.PARAGRAPH,
                paragraphIndex = index
            )
        }

        val segments = TtsSpeechSegmenter.segments(chunks, 0).toList()

        assertEquals(chunks.last().text, segments.last().text)
        assertEquals(chunks.map { it.text }, chunks.indices.map { index ->
            segments.filter { it.chunkIndex == index }.joinToString(" ") { it.text }
        })
    }

    @Test
    fun `adds the next chunk pause after the last segment of a chunk`() {
        val chunks = listOf(
            TtsChunk("A heading", TtsChunkBoundary.START, paragraphIndex = 0),
            TtsChunk("Body text.", TtsChunkBoundary.HEADING, paragraphIndex = 1)
        )

        val segments = TtsSpeechSegmenter.segments(chunks, 0).toList()

        assertEquals(TtsChunkBoundary.HEADING.pauseBeforeMillis, segments.first().trailingSilenceMillis)
        assertEquals(0, segments.last().trailingSilenceMillis)
    }

    @Test
    fun `starts from the requested chunk`() {
        val chunks = listOf(
            TtsChunk("Skipped.", TtsChunkBoundary.START, paragraphIndex = 0),
            TtsChunk("Read.", TtsChunkBoundary.PARAGRAPH, paragraphIndex = 1)
        )

        assertEquals(
            listOf(1),
            TtsSpeechSegmenter.segments(chunks, 1).map { it.chunkIndex }.toList()
        )
    }
}
