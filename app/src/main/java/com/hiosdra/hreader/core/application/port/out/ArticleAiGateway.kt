package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource

interface ArticleAiGateway {
    suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String
    ): Result<String>

    suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport>
}
