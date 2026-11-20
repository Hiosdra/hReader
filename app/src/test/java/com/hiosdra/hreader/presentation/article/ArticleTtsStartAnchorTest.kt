package com.hiosdra.hreader.presentation.article

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTtsStartAnchorTest {
    @Test
    fun `decodes javascript string results`() {
        assertEquals("Hello \"world\"", decodeJavascriptString("\"Hello \\\"world\\\"\""))
        assertEquals("", decodeJavascriptString("\"\""))
        assertEquals("Zażółć\nłódź", decodeJavascriptString("\"Za\u017c\u00f3\u0142\u0107\nłódź\""))
        assertNull(decodeJavascriptString("null"))
        assertNull(decodeJavascriptString(null))
    }

    @Test
    fun `embeds the visible top offset in the lookup script`() {
        assertTrue(firstVisibleParagraphScript(420).endsWith("(420)"))
        assertTrue(firstVisibleParagraphScript(-5).endsWith("(0)"))
    }

    @Test
    fun `returns the registered anchor only for its article`() = runTest {
        val anchors = ArticleTtsStartAnchors()
        val provider: suspend () -> String? = { "Visible paragraph" }
        anchors.register(1L, provider)

        assertEquals("Visible paragraph", anchors.anchorFor(1L))
        assertNull(anchors.anchorFor(2L))

        anchors.unregister(1L, provider)
        assertNull(anchors.anchorFor(1L))
    }

    @Test
    fun `treats a blank anchor as the article start`() = runTest {
        val anchors = ArticleTtsStartAnchors()
        anchors.register(1L) { "" }

        assertNull(anchors.anchorFor(1L))
    }
}
