package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiModel

internal fun OpenRouterModel.toAiModel(): AiModel = AiModel(
    id = id,
    displayName = name?.takeIf { it.isNotBlank() } ?: id,
    description = description?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull().orEmpty(),
    contextLength = contextLength ?: 0,
    isFree = isFree()
)

private fun OpenRouterModel.isFree(): Boolean {
    if (id.endsWith(":free")) return true
    val prompt = pricing?.prompt?.toDoubleOrNull() ?: return false
    val completion = pricing.completion?.toDoubleOrNull() ?: return false
    return prompt == 0.0 && completion == 0.0
}
