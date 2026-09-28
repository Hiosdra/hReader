package com.hiosdra.hreader.adapter.ai.openrouter

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.SelectedModelStatus
import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiModelCatalogCache
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.CachedAiModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L

class AiModelRepository(
    private val apiService: OpenRouterApiService,
    private val preferencesManager: AiPreferences,
    private val catalogCache: AiModelCatalogCache
) : AiModelCatalog {
    private val mutex = Mutex()
    private var cachedModels: List<AiModel>? = null
    private var cachedAt: Long = 0L

    override suspend fun getModels(forceRefresh: Boolean): List<AiModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadPersistentCacheIfNeeded()
            cachedModels?.takeIf { !forceRefresh && isFresh() }?.let { return@withLock it }
            apiService.getModels().data
                .map { it.toAiModel() }
                .sortedWith(compareByDescending<AiModel> { it.isFree }.thenBy { it.displayName.lowercase() })
                .also {
                    cachedModels = it
                    cachedAt = System.currentTimeMillis()
                    catalogCache.write(CachedAiModelCatalog(it, cachedAt))
                }
        }
    }

    private fun isFresh(): Boolean = System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS

    override suspend fun checkSelectedModel(): SelectedModelStatus = withContext(Dispatchers.IO) {
        if (preferencesManager.getOpenRouterApiKey().isBlank()) {
            return@withContext SelectedModelStatus.Unknown
        }
        val selectedId = preferencesManager.getAiModelId()
        mutex.withLock {
            loadPersistentCacheIfNeeded()
            val models = cachedModels ?: return@withLock SelectedModelStatus.Unknown
            when {
                !isFresh() || models.isEmpty() -> SelectedModelStatus.Unknown
                models.any { it.id == selectedId } -> SelectedModelStatus.Available
                else -> SelectedModelStatus.Unavailable(selectedId)
            }
        }
    }

    override suspend fun refreshCatalogIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (preferencesManager.getOpenRouterApiKey().isBlank()) return@withContext false
        val stale = mutex.withLock {
            loadPersistentCacheIfNeeded()
            cachedModels == null || !isFresh()
        }
        if (!stale) return@withContext false
        getModels(forceRefresh = true)
        true
    }

    private fun loadPersistentCacheIfNeeded() {
        if (cachedModels != null) return
        catalogCache.read()?.let { cache ->
            cachedModels = cache.models
            cachedAt = cache.fetchedAtMillis
        }
    }
}
