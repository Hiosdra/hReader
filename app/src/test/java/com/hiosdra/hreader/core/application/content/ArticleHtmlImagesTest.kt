package com.hiosdra.hreader.core.application.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlImagesTest {

    private val baseUri = "https://example.com/posts/one"

    @Test
    fun `a relative image address becomes the absolute one it was downloaded under`() {
        val html = """<p>Text</p><img src="/media/photo.jpg">"""

        val prepared = prepareArticleImages(html, baseUri, "Open embedded media")

        assertTrue(prepared.html.contains("https://example.com/media/photo.jpg"))
    }

    @Test
    fun `an absolute image address is left alone`() {
        val html = """<img src="https://cdn.example.com/photo.jpg">"""

        assertTrue(prepareArticleImages(html, baseUri, "Open embedded media").html.contains("https://cdn.example.com/photo.jpg"))
    }

    @Test
    fun `srcset is dropped so nothing goes back to the network for another size`() {
        val html = """<img src="/a.jpg" srcset="/a-2x.jpg 2x, /a-3x.jpg 3x">"""

        val prepared = prepareArticleImages(html, baseUri, "Open embedded media")

        assertFalse(prepared.html.contains("srcset"))
        assertFalse(prepared.html.contains("a-2x.jpg"))
    }

    @Test
    fun `picture sources are dropped too`() {
        val html = """<picture><source srcset="/a.webp" type="image/webp"><img src="/a.jpg"></picture>"""

        assertFalse(prepareArticleImages(html, baseUri, "Open embedded media").html.contains("srcset"))
    }

    @Test
    fun `an empty body is returned untouched`() {
        val prepared = prepareArticleImages("", baseUri, "Open embedded media")

        assertTrue(prepared.html.isEmpty())
        assertTrue(prepared.imageUrls.isEmpty())
    }

    @Test
    fun `the pictures to download come back with the body, resolved and without repeats`() {
        val html = """<img src="/media/photo.jpg"><p>Text</p><img src="/media/photo.jpg"><img src="/chart.png">"""

        val prepared = prepareArticleImages(html, baseUri, "Open embedded media")

        assertEquals(
            listOf("https://example.com/media/photo.jpg", "https://example.com/chart.png"),
            prepared.imageUrls
        )
    }

    @Test
    fun `the feed title is removed before the body is stored`() {
        val html = "<article><h1>Article title</h1><p>Body</p></article>"

        val prepared = prepareArticleImages(html, baseUri, "Open embedded media", "Article title")

        assertFalse(prepared.html.contains("<h1>Article title</h1>"))
        assertTrue(prepared.html.contains("Body"))
    }

    @Test
    fun `an image with no source is not something to download`() {
        val html = """<img alt="none"><img src="/photo.jpg">"""

        assertEquals(listOf("https://example.com/photo.jpg"), prepareArticleImages(html, baseUri, "Open embedded media").imageUrls)
    }

    @Test
    fun `removes active embeds while keeping article content and linking the media`() {
        val html = """
            <p>Intro</p>
            <style>.lead { color: red; }</style>
            <svg viewBox="0 0 1 1"><path d="M0 0h1v1H0z"></path></svg>
            <iframe src="/video"></iframe>
            <video><source src="/clip.mp4"></video>
            <script>alert('bad')</script>
            <p onclick="bad()">Body</p>
            <a href="javascript:alert('bad')">Safe label</a>
            <a href="java&#10;script:alert('bad')">Obfuscated label</a>
            <img src="/photo.jpg">
        """.trimIndent()

        val prepared = prepareArticleImages(html, baseUri, "Open embedded media")

        assertTrue(prepared.html.contains("Intro"))
        assertFalse(prepared.html.contains("<style"))
        assertFalse(prepared.html.contains("<svg"))
        assertTrue(prepared.html.contains("Body"))
        assertTrue(prepared.html.contains("Open embedded media"))
        assertTrue(prepared.html.contains("https://example.com/photo.jpg"))
        assertFalse(prepared.html.contains("<iframe"))
        assertFalse(prepared.html.contains("<video"))
        assertFalse(prepared.html.contains("<script"))
        assertFalse(prepared.html.contains("onclick"))
        assertFalse(prepared.html.contains("javascript:"))
        assertTrue(prepared.html.contains("Obfuscated label"))
        assertFalse(prepared.html.contains("script:"))
    }

    @Test
    fun `drops unsupported protocols and parser edge cases`() {
        val html = """
            <a href="vbscript:alert('bad')">Bad link</a>
            <img src="data:image/png;base64,AAAA">
            <div style="background:url(javascript:alert('bad'))">Safe text</div>
        """.trimIndent()
        val parserEdgeCase = "<noscript><style></noscript><script>alert('bad')</script>"

        val sanitized = sanitizeArticleHtml(html, baseUri, "Open embedded media")
        val sanitizedEdgeCase = sanitizeArticleHtml(parserEdgeCase, baseUri, "Open embedded media")

        assertTrue(sanitized.contains("Safe text"))
        assertFalse(sanitized.contains("<style"))
        assertFalse(sanitized.contains("<script"))
        assertFalse(sanitized.contains("vbscript:"))
        assertFalse(sanitized.contains("data:image"))
        assertFalse(sanitized.contains("javascript:"))
        assertFalse(sanitizedEdgeCase.contains("noscript"))
        assertFalse(sanitizedEdgeCase.contains("<style"))
        assertFalse(sanitizedEdgeCase.contains("<script"))
    }

    @Test
    fun `sanitizes already cached article html before rendering`() {
        val cachedHtml = "<p>Cached</p><iframe src=\"/embed\"></iframe><script>bad()</script>"

        val sanitized = sanitizeArticleHtml(cachedHtml, baseUri, "Open embedded media")

        assertTrue(sanitized.contains("Cached"))
        assertTrue(sanitized.contains("Open embedded media"))
        assertFalse(sanitized.contains("<iframe"))
        assertFalse(sanitized.contains("<script"))
    }
}
