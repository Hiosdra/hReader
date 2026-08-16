package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How long the catalogue is reused before it is fetched again. It used to be held for the life of
 * the process, so the startup check that warns about a withdrawn model was answering from the list
 * pulled when the app first opened — a model retired since would never be reported.
 */
private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L

class AiModelRepository(
    private val apiService: OpenRouterApiService,
    private val preferencesManager: AppPreferences
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
        // Without a key the AI features are unusable anyway, so there is nothing to warn about
        // and no reason to pull the whole catalogue over the network.
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
