package com.hiosdra.hreader.data.preferences

import android.content.Context
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod
import com.hiosdra.hreader.util.SyncPerformanceRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class PreferencesManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("hreader_prefs", Context.MODE_PRIVATE)
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val syncRecordsAdapter = moshi.adapter<List<SyncPerformanceRecord>>(
        Types.newParameterizedType(List::class.java, SyncPerformanceRecord::class.java)
    )
    
    fun getPaywallBypassMethod(): PaywallBypassMethod {
        val savedMethod = sharedPreferences.getString(KEY_PAYWALL_BYPASS_METHOD, PaywallBypassMethod.SMRY_AI.name)
        return PaywallBypassMethod.entries.find { it.name == savedMethod } ?: PaywallBypassMethod.SMRY_AI
    }
    
    fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        sharedPreferences.edit()
            .putString(KEY_PAYWALL_BYPASS_METHOD, method.name)
            .apply()
    }
    
    fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }
    
    fun setLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
    }
    
    fun getSyncPerformanceRecords(): List<SyncPerformanceRecord> {
        val json = sharedPreferences.getString(KEY_SYNC_PERFORMANCE_RECORDS, null)
        return if (json != null) {
            try {
                syncRecordsAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun addSyncPerformanceRecord(record: SyncPerformanceRecord) {
        val currentRecords = getSyncPerformanceRecords().toMutableList()
        currentRecords.add(0, record) // Add to beginning
        
        // Keep only the last 50 records
        if (currentRecords.size > MAX_PERFORMANCE_RECORDS) {
            currentRecords.subList(MAX_PERFORMANCE_RECORDS, currentRecords.size).clear()
        }
        
        val json = syncRecordsAdapter.toJson(currentRecords)
        sharedPreferences.edit()
            .putString(KEY_SYNC_PERFORMANCE_RECORDS, json)
            .apply()
    }
    
    fun clearSyncPerformanceRecords() {
        sharedPreferences.edit()
            .remove(KEY_SYNC_PERFORMANCE_RECORDS)
            .apply()
    }
    
    companion object {
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_SYNC_PERFORMANCE_RECORDS = "sync_performance_records"
        private const val MAX_PERFORMANCE_RECORDS = 50
    }
}