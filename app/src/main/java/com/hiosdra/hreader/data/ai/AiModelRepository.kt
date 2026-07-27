package com.hiosdra.hreader.data.ai

import com.hiosdra.hreader.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val FREE_SUFFIX = ":free"

sealed interface SelectedModelStatus {
    data object Available : SelectedModelStatus
    data class Unavailable(val modelId: String) : SelectedModelStatus
    data object Unknown : SelectedModelStatus
}

class AiModelRepository(
    private val apiService: OpenRouterApiService,
    private val preferencesManager: PreferencesManager
) {
    private val mutex = Mutex()
    private var cachedModels: List<AiModel>? = null

    suspend fun getModels(forceRefresh: Boolean = false): List<AiModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedModels?.takeUnless { forceRefresh }?.let { return@withLock it }
            apiService.getModels().data
                .map { it.toAiModel() }
                .sortedWith(compareByDescending<AiModel> { it.isFree }.thenBy { it.displayName.lowercase() })
                .also { cachedModels = it }
        }
    }

    suspend fun checkSelectedModel(): SelectedModelStatus {
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

internal fun OpenRouterModel.toAiModel(): AiModel = AiModel(
    id = id,
    displayName = name?.takeIf { it.isNotBlank() } ?: id,
    description = description?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull().orEmpty(),
    contextLength = contextLength ?: 0,
    isFree = isFree()
)

private fun OpenRouterModel.isFree(): Boolean {
    if (id.endsWith(FREE_SUFFIX)) return true
    val prompt = pricing?.prompt?.toDoubleOrNull() ?: return false
    val completion = pricing.completion?.toDoubleOrNull() ?: return false
    return prompt == 0.0 && completion == 0.0
}
