package com.hiosdra.hreader.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlImagesTest {

    private val baseUri = "https://example.com/posts/one"

    @Test
    fun `a relative image address becomes the absolute one it was downloaded under`() {
        val html = """<p>Text</p><img src="/media/photo.jpg">"""

        val prepared = absolutizeArticleImages(html, baseUri)

        assertTrue(prepared.contains("https://example.com/media/photo.jpg"))
    }

    @Test
    fun `an absolute image address is left alone`() {
        val html = """<img src="https://cdn.example.com/photo.jpg">"""

        assertTrue(absolutizeArticleImages(html, baseUri).contains("https://cdn.example.com/photo.jpg"))
    }

    @Test
    fun `srcset is dropped so nothing goes back to the network for another size`() {
        val html = """<img src="/a.jpg" srcset="/a-2x.jpg 2x, /a-3x.jpg 3x">"""

        val prepared = absolutizeArticleImages(html, baseUri)

        assertFalse(prepared.contains("srcset"))
        assertFalse(prepared.contains("a-2x.jpg"))
    }

    @Test
    fun `picture sources are dropped too`() {
        val html = """<picture><source srcset="/a.webp" type="image/webp"><img src="/a.jpg"></picture>"""

        assertFalse(absolutizeArticleImages(html, baseUri).contains("srcset"))
    }

    @Test
    fun `an empty body is returned untouched`() {
        assertTrue(absolutizeArticleImages("", baseUri).isEmpty())
    }
}
