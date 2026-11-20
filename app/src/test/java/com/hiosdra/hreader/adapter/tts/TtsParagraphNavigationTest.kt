package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsParagraphNavigationTest {
    private val chunks = listOf(0, 1, 1, 2, 4).map { paragraph ->
        TtsChunk("Chunk $paragraph.", TtsChunkBoundary.PARAGRAPH, paragraph)
    }

    @Test
    fun `finds the first chunk of a paragraph`() {
        assertEquals(1, TtsParagraphNavigation.firstChunkIndex(chunks, 1))
        assertEquals(4, TtsParagraphNavigation.firstChunkIndex(chunks, 3))
        assertNull(TtsParagraphNavigation.firstChunkIndex(chunks, 5))
    }

    @Test
    fun `skips to the next paragraph that has speech`() {
        assertEquals(2, TtsParagraphNavigation.nextParagraph(chunks, 1))
        assertEquals(4, TtsParagraphNavigation.nextParagraph(chunks, 2))
        assertNull(TtsParagraphNavigation.nextParagraph(chunks, 4))
    }

    @Test
    fun `goes to the previous paragraph shortly after a paragraph starts`() {
        assertEquals(1, TtsParagraphNavigation.previousParagraph(chunks, 2, elapsedInParagraphMs = 500))
        assertEquals(0, TtsParagraphNavigation.previousParagraph(chunks, 0, elapsedInParagraphMs = 500))
    }

    @Test
    fun `restarts the current paragraph after it has played for a while`() {
        assertEquals(
            2,
            TtsParagraphNavigation.previousParagraph(
                chunks,
                2,
                elapsedInParagraphMs = TtsParagraphNavigation.RESTART_PARAGRAPH_THRESHOLD_MS
            )
        )
    }
}
