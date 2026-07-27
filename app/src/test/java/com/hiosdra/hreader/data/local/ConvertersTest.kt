package com.hiosdra.hreader.data.local

import com.hiosdra.hreader.data.model.ArticleStatus
import com.hiosdra.hreader.data.model.Enclosure
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `an enclosure survives a round trip`() {
        val enclosures = listOf(Enclosure(url = "https://example.com/a.jpg", mimeType = "image/jpeg"))

        assertEquals(enclosures, converters.storageToEnclosures(converters.enclosuresToStorage(enclosures)))
    }

    @Test
    fun `every enclosure keeps its own url and mime type`() {
        val enclosures = listOf(
            Enclosure(url = "https://example.com/a.jpg", mimeType = "image/jpeg"),
            Enclosure(url = "https://example.com/b.mp3", mimeType = "audio/mpeg")
        )

        assertEquals(enclosures, converters.storageToEnclosures(converters.enclosuresToStorage(enclosures)))
    }

    @Test
    fun `a missing mime type comes back as null`() {
        val enclosures = listOf(Enclosure(url = "https://example.com/a.bin", mimeType = null))

        assertEquals(enclosures, converters.storageToEnclosures(converters.enclosuresToStorage(enclosures)))
    }

    @Test
    fun `an empty column reads back as no enclosures`() {
        assertEquals(emptyList<Enclosure>(), converters.storageToEnclosures(null))
        assertEquals(emptyList<Enclosure>(), converters.storageToEnclosures(""))
        assertEquals(emptyList<Enclosure>(), converters.storageToEnclosures(converters.enclosuresToStorage(emptyList())))
    }

    @Test
    fun `article status survives a round trip`() {
        ArticleStatus.entries.forEach { status ->
            assertEquals(status, converters.stringToArticleStatus(converters.articleStatusToString(status)))
        }
    }
}
