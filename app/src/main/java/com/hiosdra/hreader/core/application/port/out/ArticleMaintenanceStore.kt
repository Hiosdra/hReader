package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.PrefetchTarget

interface ArticleMaintenanceStore {
    suspend fun backfillMissingPreviews(limit: Int = 500): Int
    suspend fun getPrefetchTargets(): List<PrefetchTarget>

    suspend fun getPrefetchTargets(limit: Int, downloadAllImages: Boolean): List<PrefetchTarget> =
        getPrefetchTargets().take(limit)

    suspend fun getPrefetchTargetsWithEnclosures(limit: Int): List<PrefetchTarget> = emptyList()
}
