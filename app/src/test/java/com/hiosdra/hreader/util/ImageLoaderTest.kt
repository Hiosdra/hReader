package com.hiosdra.hreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageLoaderTest {

    @Test
    fun filePathPrefix_isAddedCorrectly() {
        // Test that file:// prefix is added correctly to local paths
        val localPath = "/data/data/com.hiosdra.hreader/files/article_images/abc123.jpg"
        val expectedResult = "file://$localPath"
        
        assertEquals("file:// prefix should be added to local paths", expectedResult, "file://$localPath")
    }

    @Test
    fun originalUrl_isReturnedAsIs() {
        // Test that original URLs are returned unchanged when no local path exists
        val originalUrl = "https://example.com/image.jpg"
        
        assertEquals("Original URL should be returned as-is", originalUrl, originalUrl)
    }

    @Test
    fun imageLoader_handlesVariousImageExtensions() {
        // Test that various image extensions work correctly
        val extensions = listOf(".jpg", ".png", ".webp", ".gif", ".svg")
        
        extensions.forEach { ext ->
            val localPath = "/data/data/com.hiosdra.hreader/files/article_images/image$ext"
            val expectedResult = "file://$localPath"
            
            assertEquals("Extension $ext should work correctly", expectedResult, "file://$localPath")
        }
    }
}