package com.hiosdra.hreader.core.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `drops the scheme and the www prefix`() {
        assertEquals("androidauthority.com", displayUrl("https://www.androidauthority.com/"))
    }

    @Test
    fun `keeps the path`() {
        assertEquals("all3dp.com/feed/newsfeed", displayUrl("https://all3dp.com/feed/newsfeed"))
    }

    @Test
    fun `leaves a bare host alone`() {
        assertEquals("addyosmani.com", displayUrl("addyosmani.com"))
    }

    @Test
    fun `falls back to the input when nothing is left`() {
        assertEquals("https://", displayUrl("https://"))
    }
}
