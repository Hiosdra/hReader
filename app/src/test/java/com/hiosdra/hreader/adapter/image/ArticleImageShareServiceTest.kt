package com.hiosdra.hreader.adapter.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleImageShareServiceTest {
    @Test
    fun `accepts raster image bytes matching the declared type`() {
        assertTrue(isSafeRasterImage("image/png", PNG_HEADER))
        assertTrue(isSafeRasterImage("image/jpeg", JPEG_HEADER))
        assertTrue(isSafeRasterImage("image/gif", "GIF89a".toByteArray()))
        assertTrue(isSafeRasterImage("image/webp", "RIFF0000WEBP".toByteArray()))
    }

    @Test
    fun `rejects active or mismatched content`() {
        assertFalse(isSafeRasterImage("image/svg+xml", "<svg>".toByteArray()))
        assertFalse(isSafeRasterImage("image/png", "not an image".toByteArray()))
        assertFalse(isSafeRasterImage("text/html", "<html>".toByteArray()))
    }

    @Test
    fun `normalizes only supported raster content types`() {
        assertEquals("image/jpeg", normalizedRasterImageContentType("IMAGE/JPG; charset=binary"))
        assertTrue(normalizedRasterImageContentType("image/png") != null)
        assertFalse(normalizedRasterImageContentType("image/svg+xml") != null)
    }

    private companion object {
        val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val JPEG_HEADER = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
    }
}
