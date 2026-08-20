package com.hiosdra.hreader.core.application.ai

enum class ArticleAiPhase {
    PREPARING,
    LOADING_MODEL,
    COMPACTING,
    THINKING,
    STREAMING,
    FINALIZING
}

data class ArticleAiProgress(
    val phase: ArticleAiPhase,
    val part: Int = 0,
    val totalParts: Int = 0,
    val draft: String = ""
)
