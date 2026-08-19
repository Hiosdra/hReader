package com.hiosdra.hreader.adapter.ai.common

import com.hiosdra.hreader.core.domain.model.CredibilityReport
import java.time.Clock
import java.time.Instant

class CredibilityReportFactory(private val clock: Clock) {
    fun create(
        parsed: ParsedCredibility,
        modelId: String,
        contentTruncated: Boolean
    ): CredibilityReport = CredibilityReport(
        score = parsed.score,
        confidence = parsed.confidence,
        summary = parsed.summary,
        reasons = parsed.reasons,
        redFlags = parsed.redFlags,
        factors = parsed.factors,
        modelId = modelId,
        analyzedAt = Instant.now(clock),
        contentTruncated = contentTruncated
    )
}
