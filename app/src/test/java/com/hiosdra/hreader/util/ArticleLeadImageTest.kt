package com.hiosdra.hreader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleLeadImageTest {

    @Test
    fun `the enclosure is shown when the body has no images`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = "<p>Summary</p>",
            articleHtml = "<p>The article</p>"
        )

        assertEquals("https://example.com/photo.jpg", lead)
    }

    @Test
    fun `the enclosure is dropped when the body opens with the same picture`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = null,
            articleHtml = """<img src="https://example.com/photo.jpg"><p>The article</p>"""
        )

        assertNull(lead)
    }

    @Test
    fun `a resizing query string does not make it a different picture`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = null,
            articleHtml = """<img src="https://example.com/photo.jpg?w=1200&quality=80">"""
        )

        assertNull(lead)
    }

    @Test
    fun `a resized copy of the same file is recognised`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/uploads/photo.jpg",
            feedContent = null,
            articleHtml = """<img src="https://example.com/uploads/photo-1200x675.jpg">"""
        )

        assertNull(lead)
    }

    @Test
    fun `the same file served through an image proxy is recognised`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/uploads/photo.jpg",
            feedContent = null,
            articleHtml = """<img src="//i0.wp.com/example.com/uploads/photo.jpg">"""
        )

        assertNull(lead)
    }

    @Test
    fun `a body picture further down still counts`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = null,
            articleHtml = """<p>Opening</p><img src="https://example.com/other.jpg"><img src="https://example.com/photo.jpg">"""
        )

        assertNull(lead)
    }

    @Test
    fun `a different picture leaves the enclosure in place`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = null,
            articleHtml = """<img src="https://example.com/chart.png">"""
        )

        assertEquals("https://example.com/photo.jpg", lead)
    }

    @Test
    fun `without an enclosure the first picture of the feed text is used`() {
        val lead = leadImageUrl(
            enclosureUrl = null,
            feedContent = """<p>Summary</p><img src="https://example.com/photo.jpg">""",
            articleHtml = "<p>The full text, with no pictures</p>"
        )

        assertEquals("https://example.com/photo.jpg", lead)
    }

    @Test
    fun `without an enclosure the feed picture is dropped when the full text carries it`() {
        val lead = leadImageUrl(
            enclosureUrl = null,
            feedContent = """<img src="https://example.com/photo.jpg"><p>Summary</p>""",
            articleHtml = """<img src="https://example.com/photo.jpg"><p>The full text</p>"""
        )

        assertNull(lead)
    }

    @Test
    fun `an article with no picture at all has no lead image`() {
        assertNull(leadImageUrl(enclosureUrl = null, feedContent = "<p>Text</p>", articleHtml = "<p>Text</p>"))
    }

    @Test
    fun `a blank enclosure falls back to the feed text`() {
        val lead = leadImageUrl(
            enclosureUrl = "  ",
            feedContent = """<img src="https://example.com/photo.jpg">""",
            articleHtml = "<p>No pictures here</p>"
        )

        assertEquals("https://example.com/photo.jpg", lead)
    }

    @Test
    fun `the enclosure survives while the article text is still loading`() {
        val lead = leadImageUrl(
            enclosureUrl = "https://example.com/photo.jpg",
            feedContent = null,
            articleHtml = null
        )

        assertEquals("https://example.com/photo.jpg", lead)
    }
}
