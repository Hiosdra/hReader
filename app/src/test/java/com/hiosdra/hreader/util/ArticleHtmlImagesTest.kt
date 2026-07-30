package com.hiosdra.hreader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlImagesTest {

    private val baseUri = "https://example.com/posts/one"

    @Test
    fun `a relative image address becomes the absolute one it was downloaded under`() {
        val html = """<p>Text</p><img src="/media/photo.jpg">"""

        val prepared = prepareArticleImages(html, baseUri)

        assertTrue(prepared.html.contains("https://example.com/media/photo.jpg"))
    }

    @Test
    fun `an absolute image address is left alone`() {
        val html = """<img src="https://cdn.example.com/photo.jpg">"""

        assertTrue(prepareArticleImages(html, baseUri).html.contains("https://cdn.example.com/photo.jpg"))
    }

    @Test
    fun `srcset is dropped so nothing goes back to the network for another size`() {
        val html = """<img src="/a.jpg" srcset="/a-2x.jpg 2x, /a-3x.jpg 3x">"""

        val prepared = prepareArticleImages(html, baseUri)

        assertFalse(prepared.html.contains("srcset"))
        assertFalse(prepared.html.contains("a-2x.jpg"))
    }

    @Test
    fun `picture sources are dropped too`() {
        val html = """<picture><source srcset="/a.webp" type="image/webp"><img src="/a.jpg"></picture>"""

        assertFalse(prepareArticleImages(html, baseUri).html.contains("srcset"))
    }

    @Test
    fun `an empty body is returned untouched`() {
        val prepared = prepareArticleImages("", baseUri)

        assertTrue(prepared.html.isEmpty())
        assertTrue(prepared.imageUrls.isEmpty())
    }

    @Test
    fun `the pictures to download come back with the body, resolved and without repeats`() {
        val html = """<img src="/media/photo.jpg"><p>Text</p><img src="/media/photo.jpg"><img src="/chart.png">"""

        val prepared = prepareArticleImages(html, baseUri)

        assertEquals(
            listOf("https://example.com/media/photo.jpg", "https://example.com/chart.png"),
            prepared.imageUrls
        )
    }

    @Test
    fun `an image with no source is not something to download`() {
        val html = """<img alt="none"><img src="/photo.jpg">"""

        assertEquals(listOf("https://example.com/photo.jpg"), prepareArticleImages(html, baseUri).imageUrls)
    }
}
