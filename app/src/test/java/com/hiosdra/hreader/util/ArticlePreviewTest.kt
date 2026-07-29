package com.hiosdra.hreader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePreviewTest {

    @Test
    fun `takes the readable text out of the markup`() {
        val preview = extractArticlePreview("<p>Hello <strong>world</strong>.</p>")

        assertEquals("Hello world.", preview)
    }

    @Test
    fun `separates block elements instead of running them together`() {
        val preview = extractArticlePreview("<h1>Headline</h1><p>Body text</p>")

        assertEquals("Headline Body text", preview)
    }

    @Test
    fun `drops scripts styles and media`() {
        val html = """
            <style>.a{color:red}</style>
            <script>alert('x')</script>
            <figure><img src="x.png"><figcaption>Caption</figcaption></figure>
            <p>The actual article.</p>
        """.trimIndent()

        assertEquals("The actual article.", extractArticlePreview(html))
    }

    @Test
    fun `returns null for nothing worth showing`() {
        assertNull(extractArticlePreview(null))
        assertNull(extractArticlePreview(""))
        assertNull(extractArticlePreview("<img src=\"only-an-image.png\">"))
    }

    @Test
    fun `collapses runs of whitespace`() {
        val preview = extractArticlePreview("<p>Too    many\n\n spaces</p>")

        assertEquals("Too many spaces", preview)
    }

    @Test
    fun `stays bounded for a long article`() {
        val preview = extractArticlePreview("<p>${"word ".repeat(1000)}</p>")

        assertTrue((preview?.length ?: 0) <= 400)
    }
}
