package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource

interface CredibilityStore {
    suspend fun getCached(entryId: Long): CredibilityReport?
    suspend fun getCached(entryIds: List<Long>): Map<Long, CredibilityReport>
    suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        forceRefresh: Boolean = false
    ): Result<CredibilityReport>
    suspend fun cleanupOrphanedReports(currentEntryIds: Set<Long>)
}
