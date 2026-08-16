package com.hiosdra.hreader.presentation.article

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleWebViewSecurityTest {
    @Test
    fun `allows only web links`() {
        assertTrue(isAllowedArticleLink("https://example.com/article"))
        assertTrue(isAllowedArticleLink("http://example.com/article"))
        assertFalse(isAllowedArticleLink("mailto:user@example.com"))
        assertFalse(isAllowedArticleLink("javascript:alert(1)"))
    }

    @Test
    fun `accepts only files below image directory`() {
        val root = Files.createTempDirectory("hreader-images").toFile()
        try {
            val images = File(root, "article_images").apply { mkdirs() }
            val image = File(images, "image.jpg").apply { writeText("image") }
            val outside = File(root, "outside.jpg").apply { writeText("outside") }

            assertTrue(isFileWithinDirectory(image.path, images))
            assertFalse(isFileWithinDirectory(outside.path, images))
        } finally {
            root.deleteRecursively()
        }
    }
}
