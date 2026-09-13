package com.hiosdra.hreader.core.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleContentProvenanceTest {

    @Test
    fun `full article keeps its source metadata`() {
        val fetchedAt = Instant.parse("2026-09-01T12:34:56Z")
        val sourceUrl = "https://example.com/articles/one"

        val provenance = ArticleText(
            html = "<p>Article</p>",
            leadImageUrl = null,
            source = ArticleContentSource.FULL,
            fetchedAt = fetchedAt,
            sourceUrl = sourceUrl,
            delivery = ArticleContentDelivery.NETWORK
        ).toProvenance()

        assertEquals(
            ArticleContentProvenance(
                kind = ArticleContentKind.FULL_ARTICLE,
                sourceUrl = sourceUrl,
                fetchedAt = fetchedAt,
                delivery = ArticleContentDelivery.NETWORK,
                isComplete = true
            ),
            provenance
        )
    }

    @Test
    fun `feed fallback is marked incomplete`() {
        val provenance = ArticleText(
            html = "<p>Summary</p>",
            leadImageUrl = null,
            source = ArticleContentSource.FEED_FALLBACK,
            delivery = ArticleContentDelivery.LOCAL_STORAGE
        ).toProvenance()

        assertEquals(ArticleContentKind.FEED_FALLBACK, provenance.kind)
        assertEquals(ArticleContentDelivery.LOCAL_STORAGE, provenance.delivery)
        assertEquals(false, provenance.isComplete)
    }
}
