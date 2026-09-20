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

        assertEquals(listOf("Title.", "Hello world."), articleText.chunks.map(TtsChunk::text))
        assertEquals(
            listOf(TtsChunkBoundary.START, TtsChunkBoundary.PARAGRAPH),
            articleText.chunks.map(TtsChunk::boundaryBefore)
        )
        assertFalse(articleText.chunks.any { it.text.contains("bad") })
        assertFalse(articleText.chunks.any { it.text.contains("menu") })
    }

    @Test
    fun `preserves content paragraphs and normalizes speech punctuation`() {
        val articleText = TtsTextProcessor.fromHtml(
            "A title — with emoji 😀",
            "<p>First&nbsp;paragraph…</p><p>Second paragraph</p>"
        )

        assertEquals(
            listOf("A title, with emoji.", "First paragraph...", "Second paragraph."),
            articleText.chunks.map(TtsChunk::text)
        )
        assertEquals("First paragraph... Second paragraph.", articleText.languageSample)
    }

    @Test
    fun `filters comments while retaining figure captions`() {
        val articleText = TtsTextProcessor.fromHtml(
            "Title",
            "<article><h2>Architecture</h2><figure><img alt=diagram>" +
                "<figcaption>Six hundred GB per second.</figcaption></figure>" +
                "<div id=comments>Ignore this discussion.</div></article>"
        )

        assertEquals(
            listOf("Title.", "Architecture.", "Six hundred GB per second."),
            articleText.chunks.map(TtsChunk::text)
        )
        assertFalse(articleText.chunks.any { it.text.contains("discussion") })
        assertEquals(
            listOf(
                TtsChunkBoundary.START,
                TtsChunkBoundary.HEADING,
                TtsChunkBoundary.PARAGRAPH
            ),
            articleText.chunks.map(TtsChunk::boundaryBefore)
        )
    }

    @Test
    fun `assigns shorter pauses to continuation chunks`() {
        assertTrue(
            TtsChunkBoundary.CONTINUATION.pauseBeforeMillis <
                TtsChunkBoundary.PARAGRAPH.pauseBeforeMillis
        )
        assertTrue(
            TtsChunkBoundary.PARAGRAPH.pauseBeforeMillis <
                TtsChunkBoundary.HEADING.pauseBeforeMillis
        )
    }

    @Test
    fun `marks chunks from one paragraph as continuations`() {
        val articleText = TtsTextProcessor.fromHtml(
            "Title",
            "<p>${"word ".repeat(100)}</p>"
        )

        val bodyChunks = articleText.chunks.drop(1)
        assertTrue(bodyChunks.size > 1)
        assertEquals(TtsChunkBoundary.PARAGRAPH, bodyChunks.first().boundaryBefore)
        assertTrue(
            bodyChunks.drop(1).all { it.boundaryBefore == TtsChunkBoundary.CONTINUATION }
        )
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
            listOf(TtsChunk("Np. w roku 2026, dnia 2026-09-08, o godzinie 12:30, wynik to 5,5%.", TtsChunkBoundary.START))
        ).map(TtsChunk::text)

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
                listOf(TtsChunk("M.in. zrobił to dr. Kowalski, a prof. Nowak powiedział, że tj. dobrze itd.", TtsChunkBoundary.START))
            ).map(TtsChunk::text)
        )
        assertEquals(
            listOf("Np. w roku 2026."),
            TtsTextProcessor.forModel(
                TtsModel.PIPER_LESSAC_HIGH,
                listOf(TtsChunk("Np. w roku 2026.", TtsChunkBoundary.START))
            ).map(TtsChunk::text)
        )
    }

    @Test
    fun `rechunks expanded Polish text to the model limit`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.COQUI_PL_MAI_FEMALE,
            listOf(TtsChunk("2026 ".repeat(80), TtsChunkBoundary.PARAGRAPH))
        )

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 300 })
        assertTrue(chunks.joinToString(" ", transform = TtsChunk::text).none(Char::isDigit))
        assertEquals(TtsChunkBoundary.PARAGRAPH, chunks.first().boundaryBefore)
        assertTrue(
            chunks.drop(1).all { it.boundaryBefore == TtsChunkBoundary.CONTINUATION }
        )
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

    @Test
    fun `normalizes technical English tokens for Supertonic`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.SUPERTONIC,
            listOf(
                TtsChunk(
                    "The ASIC reaches 13.4 PFLOP/s and 15.4 TB/s with GB200 and HBM4. " +
                        "It is 1.9x faster than MI355X using GPT-OSS and 216 GiB.",
                    TtsChunkBoundary.START
                )
            ),
            language = "en"
        )

        assertEquals(
            "The A S I C reaches 13.4 petaflops per second and 15.4 terabytes per second " +
                "with G B two hundred and H B M four. It is 1.9 times faster than " +
                "M I three five five X using G P T O S S and 216 gibibytes.",
            chunks.single().text
        )
    }

    @Test
    fun `normalizes Jalapeno specification units for Supertonic`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.SUPERTONIC,
            listOf(
                TtsChunk(
                    "The chip delivers 13.4 PFLOP/s and 15.4 TB/s, scaling to 27 EFLOP/s " +
                        "and 432 TiB with MXFP4 and GB300.",
                    TtsChunkBoundary.START
                )
            ),
            language = "en"
        )

        assertEquals(
            "The chip delivers 13.4 petaflops per second and 15.4 terabytes per second, " +
                "scaling to 27 exaflops per second and 432 tebibytes with M X F P four " +
                "and G B three hundred.",
            chunks.single().text
        )
    }

    @Test
    fun `does not apply English technical normalization to Polish Supertonic text`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.SUPERTONIC,
            listOf(TtsChunk("ASIC działa z prędkością 600 GB/s.", TtsChunkBoundary.START)),
            language = "pl"
        )

        assertEquals("ASIC działa z prędkością 600 GB/s.", chunks.single().text)
    }

    @Test
    fun `does not apply Supertonic technical normalization to other English models`() {
        val chunks = TtsTextProcessor.forModel(
            TtsModel.PIPER_LESSAC_HIGH,
            listOf(TtsChunk("The ASIC reaches 13.4 PFLOP/s.", TtsChunkBoundary.START)),
            language = "en"
        )

        assertEquals("The ASIC reaches 13.4 PFLOP/s.", chunks.single().text)
    }
}
