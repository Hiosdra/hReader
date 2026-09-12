package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.domain.model.CredibilityReport
import com.hiosdra.hreader.core.domain.model.CredibilitySource

interface CredibilityStore {
    suspend fun invalidateForEntries(entryIds: List<Long>, session: SyncSession? = null)
    suspend fun getCached(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        session: SyncSession? = null
    ): CredibilityReport?

    suspend fun getCached(
        sources: Map<Long, CredibilitySource>,
        modelId: String,
        session: SyncSession? = null
    ): Map<Long, CredibilityReport>
    suspend fun analyze(
        entryId: Long,
        source: CredibilitySource,
        modelId: String,
        forceRefresh: Boolean = false,
        session: SyncSession? = null
    ): Result<CredibilityReport>
    suspend fun cleanupOrphanedReports(session: SyncSession? = null)
}
