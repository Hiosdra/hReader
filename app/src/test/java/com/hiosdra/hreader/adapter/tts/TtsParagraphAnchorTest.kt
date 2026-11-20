package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsParagraphAnchorTest {
    private val article = TtsTextProcessor.fromHtml(
        "Title",
        "<p>First paragraph about rivers.</p>" +
            "<h2>Mountains</h2>" +
            "<p>Second “paragraph” — about <b>mountains</b> and valleys…</p>" +
            "<ul><li>Third item on lakes</li></ul>"
    )

    @Test
    fun `assigns a paragraph index to every chunk`() {
        assertEquals(listOf(0, 1, 2, 3, 4), article.chunks.map(TtsChunk::paragraphIndex))
        assertEquals(5, article.paragraphs.size)
    }

    @Test
    fun `matches the visible paragraph despite punctuation normalization`() {
        val index = TtsTextProcessor.paragraphIndexForAnchor(
            article.paragraphs,
            "Second “paragraph” — about mountains and valleys…"
        )

        assertEquals(3, index)
    }

    @Test
    fun `matches a paragraph by its leading text`() {
        assertEquals(4, TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, "Third item on lakes"))
    }

    @Test
    fun `matches a selected fragment inside a paragraph`() {
        assertEquals(3, TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, "mountains and valleys"))
    }

    @Test
    fun `ignores the title and blank anchors`() {
        assertNull(TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, "Title"))
        assertNull(TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, ""))
        assertNull(TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, null))
        assertNull(TtsTextProcessor.paragraphIndexForAnchor(article.paragraphs, "Unrelated text"))
    }

    @Test
    fun `keeps paragraph indices after model specific rechunking`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.COQUI_PL_MAI_FEMALE,
            listOf(TtsChunk("2026 ".repeat(80), TtsChunkBoundary.PARAGRAPH, paragraphIndex = 7)),
            language = "pl"
        )

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.paragraphIndex == 7 })
    }
}
