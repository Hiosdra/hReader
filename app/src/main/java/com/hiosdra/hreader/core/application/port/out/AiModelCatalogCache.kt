package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.ai.AiModel

data class CachedAiModelCatalog(
    val models: List<AiModel>,
    val fetchedAtMillis: Long
)

interface AiModelCatalogCache {
    fun read(): CachedAiModelCatalog?
    fun write(catalog: CachedAiModelCatalog)
}
