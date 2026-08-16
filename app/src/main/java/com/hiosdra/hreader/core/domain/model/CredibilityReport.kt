package com.hiosdra.hreader.core.domain.model

import java.time.Instant

enum class CredibilityLevel {
    LOW,
    MIXED,
    HIGH;

    companion object {
        const val MIXED_THRESHOLD = 0.4f
        const val HIGH_THRESHOLD = 0.7f

        fun fromScore(score: Float): CredibilityLevel = when {
            score >= HIGH_THRESHOLD -> HIGH
            score >= MIXED_THRESHOLD -> MIXED
            else -> LOW
        }
    }
}

enum class CredibilityConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class CredibilityFactor(
    val name: String,
    val score: Float
)

data class CredibilityReport(
    val score: Float,
    val confidence: CredibilityConfidence,
    val summary: String,
    val reasons: List<String> = emptyList(),
    val redFlags: List<String> = emptyList(),
    val factors: List<CredibilityFactor> = emptyList(),
    val modelId: String,
    val analyzedAt: Instant,
    val contentTruncated: Boolean = false
) {
    val level: CredibilityLevel get() = CredibilityLevel.fromScore(score)
}

data class CredibilitySource(
    val title: String,
    val content: String,
    val author: String?,
    val feedTitle: String?,
    val url: String,
    val publishedAt: Instant?
)
