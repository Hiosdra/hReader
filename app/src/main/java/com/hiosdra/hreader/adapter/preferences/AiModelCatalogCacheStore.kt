package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.AiProvider
import com.hiosdra.hreader.core.application.port.out.AiModelCatalogCache
import com.hiosdra.hreader.core.application.port.out.CachedAiModelCatalog
import org.json.JSONArray
import org.json.JSONObject

internal class AiModelCatalogCacheStore(
    private val storage: PreferenceStorage
) : AiModelCatalogCache {
    override fun read(): CachedAiModelCatalog? = runCatching {
        val json = storage.get(AiPreferenceKeys.openRouterModelCatalogCache) ?: return null
        val root = JSONObject(json)
        val models = root.getJSONArray(KEY_MODELS).toAiModels()
        CachedAiModelCatalog(models, root.getLong(KEY_FETCHED_AT))
    }.getOrNull()

    override fun write(catalog: CachedAiModelCatalog) {
        val modelArray = JSONArray().apply {
            catalog.models.forEach { model ->
                put(
                    JSONObject()
                        .put(KEY_ID, model.id)
                        .put(KEY_NAME, model.displayName)
                        .put(KEY_DESCRIPTION, model.description)
                        .put(KEY_CONTEXT_LENGTH, model.contextLength)
                        .put(KEY_FREE, model.isFree)
                        .put(KEY_PROVIDER, model.provider.name)
                )
            }
        }
        val value = JSONObject()
            .put(KEY_FETCHED_AT, catalog.fetchedAtMillis)
            .put(KEY_MODELS, modelArray)
            .toString()
        storage.set(AiPreferenceKeys.openRouterModelCatalogCache, value)
    }

    private fun JSONArray.toAiModels(): List<AiModel> = List(length()) { index ->
        val item = getJSONObject(index)
        val id = item.getString(KEY_ID)
        val provider = runCatching {
            AiProvider.valueOf(item.getString(KEY_PROVIDER))
        }.getOrElse { AiModel.providerFor(id) }
        AiModel(
            id = id,
            displayName = item.getString(KEY_NAME),
            description = item.optString(KEY_DESCRIPTION),
            contextLength = item.optInt(KEY_CONTEXT_LENGTH),
            isFree = item.optBoolean(KEY_FREE),
            provider = provider
        )
    }

    private companion object {
        const val KEY_FETCHED_AT = "fetched_at"
        const val KEY_MODELS = "models"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CONTEXT_LENGTH = "context_length"
        const val KEY_FREE = "free"
        const val KEY_PROVIDER = "provider"
    }
}
