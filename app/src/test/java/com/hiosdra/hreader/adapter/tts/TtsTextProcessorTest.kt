package com.hiosdra.hreader.adapter.tts

import com.hiosdra.hreader.core.application.tts.TtsModel
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

    @Test
    fun `expands Polish numbers only for the Coqui character model`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.COQUI_PL_MAI_FEMALE,
            listOf("Np. w roku 2026, dnia 2026-09-08, o godzinie 12:30, wynik to 5,5%.")
        )

        assertEquals(
            "Na przykład w roku dwa tysiące dwadzieścia sześć, dnia osiem września dwa tysiące " +
                "dwadzieścia sześć, o godzinie dwanaście trzydzieści, wynik to pięć przecinek " +
                "pięć procent.",
            chunks.single()
        )
        assertTrue(chunks.single().none(Char::isDigit))
        assertEquals(
            listOf(
                "Między innymi zrobił to doktor Kowalski, a profesor Nowak powiedział, " +
                    "że to jest dobrze i tak dalej"
            ),
            TtsTextProcessor.forModel(
                TtsModel.COQUI_PL_MAI_FEMALE,
                listOf("M.in. zrobił to dr. Kowalski, a prof. Nowak powiedział, że tj. dobrze itd.")
            )
        )
        assertEquals(
            listOf("Np. w roku 2026."),
            TtsTextProcessor.forModel(TtsModel.PIPER_LESSAC_HIGH, listOf("Np. w roku 2026."))
        )
    }

    @Test
    fun `rechunks expanded Polish text to the model limit`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.COQUI_PL_MAI_FEMALE,
            listOf("2026 ".repeat(80))
        )

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 300 })
        assertTrue(chunks.joinToString(" ").none(Char::isDigit))
    }

    @Test
    fun `does not rewrite numbers inside technical identifiers`() {
        val text = PolishTtsTextNormalizer.normalize(
            "Odwiedź https://example.com/v2/2026, napisz e-mail a12@example.com " +
                "i użyj wersji v2.0.1 oraz hosta 192.168.1.10. Kod A123B pozostaje."
        )

        assertTrue(text.contains("https://example.com/v2/2026"))
        assertTrue(text.contains("a12@example.com"))
        assertTrue(text.contains("v2.0.1"))
        assertTrue(text.contains("192.168.1.10"))
        assertTrue(text.contains("A123B"))
    }
}
