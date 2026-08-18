package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.ai.GemmaBackend
import kotlinx.coroutines.flow.Flow

interface AiPreferences {
    fun getOpenRouterApiKey(): String
    fun setOpenRouterApiKey(apiKey: String)
    fun getAiModelId(): String
    fun setAiModelId(modelId: String)
    fun observeAiModelId(): Flow<String>
    fun getGemmaBackend(): GemmaBackend
    fun setGemmaBackend(backend: GemmaBackend)
}
