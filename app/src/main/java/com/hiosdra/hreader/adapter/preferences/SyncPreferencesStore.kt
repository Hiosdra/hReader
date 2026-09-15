package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.sync.SyncCheckpoint
import com.hiosdra.hreader.core.application.sync.SyncCheckpointMode
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.sync.SyncMode
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SyncPreferencesStore(
    private val storage: PreferenceStorage
) : SyncPreferences {
    override fun getLastSyncTimestamp(): Long =
        storage.get(SyncPreferenceKeys.lastSyncTimestamp) ?: 0L

    override fun setLastSyncTimestamp(timestamp: Long) {
        storage.update { this[SyncPreferenceKeys.lastSyncTimestamp] = timestamp }
    }

    override fun getCacheOwnerKey(): String =
        storage.get(SyncPreferenceKeys.cacheOwner).orEmpty()

    override fun setCacheOwnerKey(ownerKey: String) {
        storage.update { this[SyncPreferenceKeys.cacheOwner] = ownerKey }
    }

    override fun isCacheCleanupPending(): Boolean =
        storage.get(SyncPreferenceKeys.cacheCleanupPending) ?: false

    override fun setCacheCleanupPending(pending: Boolean) {
        storage.update { this[SyncPreferenceKeys.cacheCleanupPending] = pending }
    }

    override fun observeLastSyncTimestamp(): Flow<Long> =
        storage.observe(SyncPreferenceKeys.lastSyncTimestamp).map { it ?: 0L }

    override fun getLastFullSyncTimestamp(): Long =
        storage.get(SyncPreferenceKeys.lastFullSyncTimestamp) ?: 0L

    override fun setLastFullSyncTimestamp(timestamp: Long) {
        storage.update { this[SyncPreferenceKeys.lastFullSyncTimestamp] = timestamp }
    }

    override fun getSyncCheckpoint(): SyncCheckpoint? =
        storage.get(SyncPreferenceKeys.syncCheckpoint)?.let(::decodeSyncCheckpoint)

    override fun setSyncCheckpoint(checkpoint: SyncCheckpoint) {
        storage.update {
            this[SyncPreferenceKeys.syncCheckpoint] = encodeSyncCheckpoint(checkpoint)
        }
    }

    override fun clearSyncCheckpoint() {
        storage.update { remove(SyncPreferenceKeys.syncCheckpoint) }
    }

    override fun getOfflineBacklogTarget(): Int =
        (storage.get(SyncPreferenceKeys.offlineBacklogTarget) ?: DEFAULT_OFFLINE_BACKLOG_TARGET)
            .coerceAtLeast(0)

    override fun setOfflineBacklogTarget(target: Int) {
        val normalizedTarget = target.coerceAtLeast(0)
        storage.update { this[SyncPreferenceKeys.offlineBacklogTarget] = normalizedTarget }
    }

    override fun getImageDownloadEnabled(): Boolean =
        storage.get(SyncPreferenceKeys.imageDownloadEnabled) ?: true

    override fun setImageDownloadEnabled(enabled: Boolean) {
        storage.update { this[SyncPreferenceKeys.imageDownloadEnabled] = enabled }
    }

    override fun getImageCacheBudgetMegabytes(): Int =
        (storage.get(SyncPreferenceKeys.imageCacheBudgetMegabytes) ?: DEFAULT_IMAGE_CACHE_BUDGET_MB)
            .coerceAtLeast(0)

    override fun setImageCacheBudgetMegabytes(megabytes: Int) {
        val normalizedMegabytes = megabytes.coerceAtLeast(0)
        storage.update { this[SyncPreferenceKeys.imageCacheBudgetMegabytes] = normalizedMegabytes }
    }

    override fun getSyncIntervalMinutes(): Int =
        (storage.get(SyncPreferenceKeys.syncIntervalMinutes) ?: SyncDefaults.INTERVAL_MINUTES)
            .coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)

    override fun setSyncIntervalMinutes(minutes: Int) {
        val normalizedMinutes = minutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        storage.update { this[SyncPreferenceKeys.syncIntervalMinutes] = normalizedMinutes }
    }

    override fun observeSyncIntervalMinutes(): Flow<Int> =
        storage.observe(SyncPreferenceKeys.syncIntervalMinutes).map {
            (it ?: SyncDefaults.INTERVAL_MINUTES).coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        }

    override fun getSyncMode(): SyncMode =
        SyncMode.fromName(storage.get(SyncPreferenceKeys.syncMode))

    override fun setSyncMode(mode: SyncMode) {
        storage.update { this[SyncPreferenceKeys.syncMode] = mode.name }
    }

    override fun getSyncOnUnmeteredOnly(): Boolean =
        storage.get(SyncPreferenceKeys.syncOnUnmeteredOnly) ?: false

    override fun setSyncOnUnmeteredOnly(enabled: Boolean) {
        storage.update { this[SyncPreferenceKeys.syncOnUnmeteredOnly] = enabled }
    }

    override fun getSyncWhileRoaming(): Boolean =
        storage.get(SyncPreferenceKeys.syncWhileRoaming) ?: true

    override fun setSyncWhileRoaming(enabled: Boolean) {
        storage.update { this[SyncPreferenceKeys.syncWhileRoaming] = enabled }
    }

    override fun getQuietHoursEnabled(): Boolean =
        storage.get(SyncPreferenceKeys.quietHoursEnabled) ?: false

    override fun setQuietHoursEnabled(enabled: Boolean) {
        storage.update { this[SyncPreferenceKeys.quietHoursEnabled] = enabled }
    }

    override fun getQuietHoursStartHour(): Int =
        (storage.get(SyncPreferenceKeys.quietHoursStart) ?: SyncDefaults.QUIET_HOURS_START)
            .coerceIn(0, 23)

    override fun getQuietHoursEndHour(): Int =
        (storage.get(SyncPreferenceKeys.quietHoursEnd) ?: SyncDefaults.QUIET_HOURS_END)
            .coerceIn(0, 23)

    override fun setQuietHours(startHour: Int, endHour: Int) {
        val normalizedStartHour = startHour.coerceIn(0, 23)
        val normalizedEndHour = endHour.coerceIn(0, 23)
        storage.update {
            this[SyncPreferenceKeys.quietHoursStart] = normalizedStartHour
            this[SyncPreferenceKeys.quietHoursEnd] = normalizedEndHour
        }
    }

    override fun getLastChainedSyncTimestamp(): Long =
        storage.get(SyncPreferenceKeys.lastChainedSyncTimestamp) ?: 0L

    override fun setLastChainedSyncTimestamp(timestamp: Long) {
        storage.update { this[SyncPreferenceKeys.lastChainedSyncTimestamp] = timestamp }
    }

    private fun encodeSyncCheckpoint(checkpoint: SyncCheckpoint): String {
        fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

        return listOf(
            encode(checkpoint.ownerKey),
            checkpoint.mode.name,
            checkpoint.startedAt.toString(),
            checkpoint.changedAfter?.toString().orEmpty(),
            checkpoint.cursor?.let(::encode).orEmpty(),
            checkpoint.fullSyncRunId?.let(::encode).orEmpty()
        ).joinToString(".")
    }

    private fun decodeSyncCheckpoint(value: String): SyncCheckpoint? = runCatching {
        val parts = value.split('.', limit = 6)
        require(parts.size == 5 || parts.size == 6)
        fun decode(encoded: String): String =
            String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)

        SyncCheckpoint(
            ownerKey = decode(parts[0]),
            mode = SyncCheckpointMode.valueOf(parts[1]),
            startedAt = parts[2].toLong(),
            changedAfter = parts[3].takeIf(String::isNotEmpty)?.toLong(),
            cursor = parts[4].takeIf(String::isNotEmpty)?.let(::decode),
            fullSyncRunId = parts.getOrNull(5)?.takeIf(String::isNotEmpty)?.let(::decode)
        )
    }.getOrNull()

    private companion object {
        const val DEFAULT_OFFLINE_BACKLOG_TARGET = 0
        const val DEFAULT_IMAGE_CACHE_BUDGET_MB = 500
        const val MIN_SYNC_INTERVAL_MINUTES = 15
    }
}
