package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.port.out.PerformancePreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

internal class PerformancePreferencesStore(
    private val storage: PreferenceStorage,
    moshi: Moshi
) : PerformancePreferences {
    private val recordsAdapter = moshi.adapter<List<SyncPerformanceRecord>>(
        Types.newParameterizedType(List::class.java, SyncPerformanceRecord::class.java)
    )

    override fun getSyncPerformanceRecords(): List<SyncPerformanceRecord> =
        decode(storage.get(PerformancePreferenceKeys.syncPerformanceRecords))

    override fun addSyncPerformanceRecord(record: SyncPerformanceRecord) {
        storage.update {
            val records = (listOf(record) + decode(this[PerformancePreferenceKeys.syncPerformanceRecords]))
                .take(MAX_RECORDS)
            this[PerformancePreferenceKeys.syncPerformanceRecords] = recordsAdapter.toJson(records)
        }
    }

    override fun clearSyncPerformanceRecords() {
        storage.update { remove(PerformancePreferenceKeys.syncPerformanceRecords) }
    }

    private fun decode(json: String?): List<SyncPerformanceRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { recordsAdapter.fromJson(json).orEmpty().toList() }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_RECORDS = 50
    }
}
