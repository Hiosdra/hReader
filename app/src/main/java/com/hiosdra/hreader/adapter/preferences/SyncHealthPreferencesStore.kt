package com.hiosdra.hreader.adapter.preferences

import androidx.datastore.preferences.core.Preferences
import com.hiosdra.hreader.core.application.port.out.SyncHealthStore
import com.hiosdra.hreader.core.application.sync.ArticleSyncResult
import com.hiosdra.hreader.core.application.sync.SyncFailure
import com.hiosdra.hreader.core.application.sync.SyncHealthSnapshot
import com.hiosdra.hreader.core.application.sync.SyncRunState
import com.hiosdra.hreader.core.application.sync.SyncRunSummary
import com.hiosdra.hreader.core.application.sync.recordCancelled
import com.hiosdra.hreader.core.application.sync.recordFinished
import com.hiosdra.hreader.core.application.sync.recordStageFailure
import com.hiosdra.hreader.core.application.sync.recordStarted
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class SyncHealthPreferencesStore(
    private val storage: PreferenceStorage,
    moshi: Moshi
) : SyncHealthStore {
    private val healthAdapter = moshi.adapter(SyncHealthSnapshot::class.java)

    override fun getSnapshot(): SyncHealthSnapshot =
        toSyncHealthSnapshot(storage.currentPreferences())

    override fun observe(): Flow<SyncHealthSnapshot> = storage.observePreferences()
        .map(::toSyncHealthSnapshot)
        .distinctUntilChanged()

    override fun recordSyncStarted(attemptedAt: Long, runId: String) {
        updateSyncHealth { it.recordStarted(attemptedAt, runId) }
    }

    override fun recordSyncFinished(completedAt: Long, result: ArticleSyncResult, runId: String) {
        updateSyncHealth { it.recordFinished(completedAt, result, runId) }
    }

    override fun recordStageFailure(completedAt: Long, failure: SyncFailure, runId: String) {
        updateSyncHealth { it.recordStageFailure(completedAt, failure, runId) }
    }

    override fun recordSyncCancelled(completedAt: Long, runId: String) {
        updateSyncHealth { it.recordCancelled(completedAt, runId) }
    }

    override fun clear() {
        storage.update {
            this[SyncPreferenceKeys.syncHealth] = healthAdapter.toJson(SyncHealthSnapshot())
        }
    }

    private fun updateSyncHealth(transform: (SyncHealthSnapshot) -> SyncHealthSnapshot) {
        storage.update {
            this[SyncPreferenceKeys.syncHealth] = healthAdapter.toJson(transform(toSyncHealthSnapshot(this)))
        }
    }

    private fun toSyncHealthSnapshot(preferences: Preferences): SyncHealthSnapshot {
        val storedJson = preferences[SyncPreferenceKeys.syncHealth]
        val stored = decode(storedJson)
        if (!storedJson.isNullOrBlank()) return stored
        val legacyTimestamp = preferences[SyncPreferenceKeys.lastSyncTimestamp] ?: 0L
        if (legacyTimestamp <= 0L) return stored
        return SyncHealthSnapshot(
            lastSuccessfulSyncAt = legacyTimestamp,
            lastAttemptedSyncAt = legacyTimestamp,
            lastRun = SyncRunSummary(
                startedAt = legacyTimestamp,
                completedAt = legacyTimestamp,
                state = SyncRunState.SUCCEEDED
            )
        )
    }

    private fun decode(json: String?): SyncHealthSnapshot =
        if (json.isNullOrBlank()) {
            SyncHealthSnapshot()
        } else {
            runCatching { healthAdapter.fromJson(json) ?: SyncHealthSnapshot() }
                .getOrDefault(SyncHealthSnapshot())
        }
}
