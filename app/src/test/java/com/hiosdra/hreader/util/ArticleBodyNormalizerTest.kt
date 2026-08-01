package com.hiosdra.hreader.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBodyNormalizerTest {

    @Test
    fun `removes a repeated article title`() {
        val html = "<h1>  A headline with <em>markup</em> </h1><p>Body</p>"

        val normalized = removeDuplicateArticleTitle(html, "A headline with markup")

        assertFalse(normalized.contains("headline with"))
        assertTrue(normalized.contains("Body"))
    }

    @Test
    fun `removes a metadata-only semantic header`() {
        val html = "<header><time>30 July 2026</time><h1>Headline</h1></header><p>Body</p>"

        val normalized = removeDuplicateArticleTitle(html, "Headline")

        assertFalse(normalized.contains("30 July 2026"))
        assertTrue(normalized.contains("Body"))
    }

    @Test
    fun `keeps a lead inside a semantic header`() {
        val html = "<header><h1>Headline</h1><p>Short introduction</p></header><p>Body</p>"

        val normalized = removeDuplicateArticleTitle(html, "Headline")

        assertTrue(normalized.contains("Short introduction"))
        assertTrue(normalized.contains("Body"))
    }

    @Test
    fun `keeps a different heading`() {
        val html = "<h2>Section heading</h2><p>Body</p>"

        val normalized = removeDuplicateArticleTitle(html, "Article title")

        assertTrue(normalized.contains("Section heading"))
    }

    @Test
    fun `keeps content when the title is not a heading`() {
        val html = "<p>Article title</p><p>Body</p>"

        val normalized = removeDuplicateArticleTitle(html, "Article title")

        assertTrue(normalized.contains("Article title"))
        assertTrue(normalized.contains("Body"))
    }
}
