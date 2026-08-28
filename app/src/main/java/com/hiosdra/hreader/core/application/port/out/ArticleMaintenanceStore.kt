package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.PrefetchTarget

interface ArticleMaintenanceStore {
    suspend fun backfillMissingPreviews(limit: Int = 500): Int
    suspend fun getPrefetchTargets(): List<PrefetchTarget>
}
