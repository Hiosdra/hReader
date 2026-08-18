package com.hiosdra.hreader.adapter.ai

import com.hiosdra.hreader.adapter.ai.gemma.Gemma4E2bModel
import com.hiosdra.hreader.adapter.ai.gemma.GemmaArticleAiService
import com.hiosdra.hreader.adapter.ai.openrouter.ArticleAiService
import com.hiosdra.hreader.core.application.port.out.ArticleAiGateway
import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource

class ArticleAiGatewayRouter(
    private val openRouter: ArticleAiService,
    private val gemma: GemmaArticleAiService
) : ArticleAiGateway {
    override suspend fun generateArticleOverview(
        title: String,
        content: String,
        modelId: String
    ): Result<String> = gatewayFor(modelId).generateArticleOverview(title, content, modelId)

    override suspend fun analyzeCredibility(
        source: CredibilitySource,
        modelId: String
    ): Result<CredibilityReport> = gatewayFor(modelId).analyzeCredibility(source, modelId)

    private fun gatewayFor(modelId: String): ArticleAiGateway =
        if (modelId == Gemma4E2bModel.MODEL_ID) gemma else openRouter
}
