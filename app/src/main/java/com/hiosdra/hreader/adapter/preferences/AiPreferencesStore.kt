package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AiPreferencesStore(
    private val storage: PreferenceStorage,
    private val secrets: SecretPreferences
) : AiPreferences {
    override fun getOpenRouterApiKey(): String = secrets.get(SecretId.OPENROUTER_API_KEY)

    override fun setOpenRouterApiKey(apiKey: String) {
        secrets.set(SecretId.OPENROUTER_API_KEY, apiKey)
    }

    override fun getAiModelId(): String =
        storage.get(AiPreferenceKeys.aiModel) ?: AiModel.DEFAULT_ID

    override fun setAiModelId(modelId: String) {
        storage.update { this[AiPreferenceKeys.aiModel] = modelId }
    }

    override fun observeAiModelId(): Flow<String> = storage.observe(AiPreferenceKeys.aiModel)
        .map { it ?: AiModel.DEFAULT_ID }

    override fun getGemmaBackend(): GemmaBackend =
        GemmaBackend.fromName(storage.get(AiPreferenceKeys.gemmaBackend))

    override fun setGemmaBackend(backend: GemmaBackend) {
        storage.update { this[AiPreferenceKeys.gemmaBackend] = backend.name }
    }

    override fun getGemmaDownloadOnUnmeteredOnly(): Boolean =
        storage.get(AiPreferenceKeys.gemmaDownloadUnmeteredOnly) ?: true

    override fun setGemmaDownloadOnUnmeteredOnly(enabled: Boolean) {
        storage.update { this[AiPreferenceKeys.gemmaDownloadUnmeteredOnly] = enabled }
    }
}
