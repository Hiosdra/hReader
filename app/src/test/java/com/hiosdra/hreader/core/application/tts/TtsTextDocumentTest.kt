package com.hiosdra.hreader.core.application.tts

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextDocumentTest {
    @Test
    fun `uses one coordinate space for title and body`() {
        val document = TtsTextDocumentFactory.fromHtml(
            title = "Title",
            html = "<p>Hello <b>world</b>.</p><script>ignored()</script>"
        )

        assertEquals("Title. Hello world.", document.text)
        assertEquals(TtsTextRange(0, 5), document.titleRange)
        assertEquals(TtsTextRange(7, 19), document.bodyRange)
        assertEquals("llo world.", document.segmentsFrom(9).single().text)
        assertEquals(7, document.segmentsFrom(6).single().range.start)
    }

    @Test
    fun `annotates visible text nodes with the same body offsets`() {
        val annotated = TtsTextDocumentFactory.annotateHtml(
            title = "Title",
            html = "<p>Hello <b>world</b>.</p><script>ignored()</script>"
        )

        assertTrue(annotated.contains("data-hreader-tts-start=\"7\""))
        assertTrue(annotated.contains("data-hreader-tts-start=\"13\""))
        assertTrue(annotated.contains("data-hreader-tts-end=\"19\""))
    }

    @Test
    fun `does not make a whole paragraph one highlight marker`() {
        val document = TtsTextDocumentFactory.fromHtml(
            title = "",
            html = "<p>First sentence. Second sentence.</p>"
        )
        val annotated = TtsTextDocumentFactory.annotateHtml(
            title = "",
            html = "<p>First sentence. Second sentence.</p>"
        )

        assertEquals(2, document.segments.size)
        document.segments.forEach { segment ->
            assertTrue(annotated.contains("data-hreader-tts-start=\"${segment.range.start}\""))
            assertTrue(annotated.contains("data-hreader-tts-end=\"${segment.range.endExclusive}\""))
        }
    }

    @Test
    fun `removes reserved marker attributes from article markup`() {
        val annotated = TtsTextDocumentFactory.annotateHtml(
            title = "",
            html = "<p><span data-hreader-tts-start=\"999\" data-hreader-tts-end=\"1000\">Text.</span></p>"
        )

        val markers = Jsoup.parseBodyFragment(annotated).select("[data-hreader-tts-start]")
        assertEquals(1, markers.size)
        assertEquals("0", markers.single().attr("data-hreader-tts-start"))
        assertEquals("5", markers.single().attr("data-hreader-tts-end"))
    }

    @Test
    fun `keeps offsets stable for non-breaking whitespace`() {
        val document = TtsTextDocumentFactory.fromHtml(
            title = "",
            html = "<p>First&nbsp;&nbsp;sentence.</p>"
        )

        assertEquals("First sentence.", document.text)
        assertEquals(TtsTextRange(0, document.text.length), document.segments.single().range)
        val annotated = TtsTextDocumentFactory.annotateHtml(
            title = "",
            html = "<p>First&nbsp;&nbsp;sentence.</p>"
        )
        assertTrue(annotated.contains("data-hreader-tts-start=\"0\""))
        assertTrue(annotated.contains("data-hreader-tts-end=\"15\""))
    }
}
