package com.hiosdra.hreader.adapter.ai.common

import com.hiosdra.hreader.core.domain.model.CredibilityConfidence
import com.hiosdra.hreader.core.domain.model.CredibilityFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CredibilityReportFactoryTest {
    @Test
    fun usesTheInjectedClockAndPreservesTheParsedVerdict() {
        val analyzedAt = Instant.parse("2026-07-28T12:34:56Z")
        val parsed = ParsedCredibility(
            score = 0.8f,
            confidence = CredibilityConfidence.HIGH,
            summary = "Solid.",
            reasons = listOf("Named sources"),
            redFlags = listOf("Missing date"),
            factors = listOf(CredibilityFactor("Sources", 0.9f))
        )

        val report = CredibilityReportFactory(
            Clock.fixed(analyzedAt, ZoneOffset.UTC)
        ).create(parsed, modelId = "test/model", contentTruncated = true)

        assertEquals(parsed.score, report.score)
        assertEquals(parsed.confidence, report.confidence)
        assertEquals(parsed.summary, report.summary)
        assertEquals(parsed.reasons, report.reasons)
        assertEquals(parsed.redFlags, report.redFlags)
        assertEquals(parsed.factors, report.factors)
        assertEquals("test/model", report.modelId)
        assertEquals(analyzedAt, report.analyzedAt)
        assertTrue(report.contentTruncated)
    }
}
