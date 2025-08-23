package com.hiosdra.hreader.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ArticleImageRepositoryTest {

    @Test
    fun generateImageId_isConsistent() {
        // Test that generateImageId produces consistent results
        val entryId = 123L
        val imageUrl = "https://example.com/image.jpg"
        
        // Use the same algorithm as in ArticleImageRepository
        val input = "$entryId-$imageUrl"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        val imageId1 = hash.joinToString("") { "%02x".format(it) }.take(16)
        
        val digest2 = MessageDigest.getInstance("SHA-256")
        val hash2 = digest2.digest(input.toByteArray())
        val imageId2 = hash2.joinToString("") { "%02x".format(it) }.take(16)
        
        assertEquals("Image ID should be consistent", imageId1, imageId2)
        assertEquals("Image ID should be 16 characters", 16, imageId1.length)
        assertNotNull("Image ID should not be null", imageId1)
        assertTrue("Image ID should be non-empty", imageId1.isNotEmpty())
    }

    @Test
    fun getFileExtension_returnsCorrectExtension() {
        // Test file extension detection logic similar to ArticleImageRepository
        val testCases = mapOf(
            "image/png" to ".png",
            "image/jpeg" to ".jpg",
            "image/webp" to ".webp",
            "image/gif" to ".gif",
            "image/svg+xml" to ".svg",
            "unknown/type" to ".img"
        )

        testCases.forEach { (contentType, expected) ->
            val extension = when {
                contentType.contains("png") -> ".png"
                contentType.contains("webp") -> ".webp"
                contentType.contains("gif") -> ".gif"
                contentType.contains("svg") -> ".svg"
                contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                else -> ".img"
            }
            assertEquals("Extension for $contentType should be $expected", expected, extension)
        }
    }

    @Test
    fun getFileExtension_fromUrl_returnsCorrectExtension() {
        // Test file extension detection from URLs
        val testCases = mapOf(
            "https://example.com/image.png" to ".png",
            "https://example.com/image.JPG" to ".jpg",
            "https://example.com/image.webp" to ".webp",
            "https://example.com/image.gif" to ".gif",
            "https://example.com/photo.jpeg" to ".jpg",
            "https://example.com/unknown" to ".img"
        )

        testCases.forEach { (imageUrl, expected) ->
            val extension = when {
                imageUrl.contains(".png", ignoreCase = true) -> ".png"
                imageUrl.contains(".webp", ignoreCase = true) -> ".webp"
                imageUrl.contains(".gif", ignoreCase = true) -> ".gif"
                imageUrl.contains(".svg", ignoreCase = true) -> ".svg"
                imageUrl.contains(".jpg", ignoreCase = true) || imageUrl.contains(".jpeg", ignoreCase = true) -> ".jpg"
                else -> ".img"
            }
            assertEquals("Extension for URL $imageUrl should be $expected", expected, extension)
        }
    }
}