package com.hiosdra.hreader.adapter.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextProcessorTest {
    @Test
    fun `extracts readable text and removes non-content elements`() {
        val articleText = TtsTextProcessor.fromHtml(
            "Title",
            "<article><p>Hello <b>world</b>.</p><script>bad()</script><footer>menu</footer></article>"
        )

        assertEquals("Title.\n\nHello world.", articleText.chunks.single())
        assertFalse(articleText.chunks.single().contains("bad"))
        assertFalse(articleText.chunks.single().contains("menu"))
    }

    @Test
    fun `preserves content paragraphs and normalizes speech punctuation`() {
        val articleText = TtsTextProcessor.fromHtml(
            "A title — with emoji 😀",
            "<p>First&nbsp;paragraph…</p><p>Second paragraph</p>"
        )

        assertEquals(
            "A title, with emoji.\n\nFirst paragraph...\n\nSecond paragraph.",
            articleText.chunks.single()
        )
        assertEquals("First paragraph... Second paragraph.", articleText.languageSample)
    }

    @Test
    fun `chunks long text without dropping sentences`() {
        val chunks = TtsTextProcessor.chunks("First sentence. Second sentence. Third sentence.", 32)

        assertTrue(chunks.size > 1)
        assertEquals(
            "First sentence. Second sentence. Third sentence.",
            chunks.joinToString(" ")
        )
    }

    @Test
    fun `keeps default synthesis chunks short`() {
        val chunks = TtsTextProcessor.chunks("word ".repeat(200))

        assertTrue(chunks.all { it.length <= 300 })
    }

    @Test
    fun `splits long sentences at word boundaries`() {
        val chunks = TtsTextProcessor.chunks("word ".repeat(200), 32)

        assertTrue(chunks.all { it.length <= 32 })
        assertTrue(chunks.dropLast(1).all { it.last() != 'w' })
    }

    @Test
    fun `splits long sentences on word boundaries`() {
        val text = "one two three four five six seven eight nine ten."
        val chunks = TtsTextProcessor.chunks(text, 20)

        assertTrue(chunks.all { it.length <= 20 })
        assertEquals(text, chunks.joinToString(" "))
    }
}
