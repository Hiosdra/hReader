package com.hiosdra.hreader.core.application.port.out

interface AiPreferences {
    fun getOpenRouterApiKey(): String
    fun setOpenRouterApiKey(apiKey: String)
    fun getAiModelId(): String
    fun setAiModelId(modelId: String)
}
