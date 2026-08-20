package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.ai.ArticleAiProgress
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource

interface ArticleAiGateway {
    suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String,
        onProgress: suspend (ArticleAiProgress) -> Unit = {}
    ): Result<String>

    suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport>
}
