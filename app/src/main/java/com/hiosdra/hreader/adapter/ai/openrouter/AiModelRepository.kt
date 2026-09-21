package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L

class AiModelRepository(
    private val apiService: OpenRouterApiService,
    private val preferencesManager: AiPreferences
) : AiModelCatalog {
    private val mutex = Mutex()
    private var cachedModels: List<AiModel>? = null
    private var cachedAt: Long = 0L

    override suspend fun getModels(forceRefresh: Boolean): List<AiModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedModels?.takeIf { !forceRefresh && isFresh() }?.let { return@withLock it }
            apiService.getModels().data
                .map { it.toAiModel() }
                .sortedWith(compareByDescending<AiModel> { it.isFree }.thenBy { it.displayName.lowercase() })
                .also {
                    cachedModels = it
                    cachedAt = System.currentTimeMillis()
                }
        }
    }

    private fun isFresh(): Boolean = System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS

    override suspend fun checkSelectedModel(): SelectedModelStatus {
        if (preferencesManager.getOpenRouterApiKey().isBlank()) return SelectedModelStatus.Unknown
        val selectedId = preferencesManager.getAiModelId()
        val models = runCatching { getModels() }.getOrNull() ?: return SelectedModelStatus.Unknown
        if (models.isEmpty()) return SelectedModelStatus.Unknown
        return if (models.any { it.id == selectedId }) {
            SelectedModelStatus.Available
        } else {
            SelectedModelStatus.Unavailable(selectedId)
        }
    }
}
