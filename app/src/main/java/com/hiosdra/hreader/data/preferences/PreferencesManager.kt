package com.hiosdra.hreader.data.preferences

import android.content.Context
import com.hiosdra.hreader.data.ai.AiModel
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

    fun getFreshRssServerUrl(): String =
        sharedPreferences.getString(KEY_FRESHRSS_SERVER_URL, DEFAULT_FRESHRSS_SERVER_URL) ?: DEFAULT_FRESHRSS_SERVER_URL

    fun setFreshRssServerUrl(url: String) {
        sharedPreferences.edit()
            .putString(KEY_FRESHRSS_SERVER_URL, url)
            .apply()
    }

    fun getFreshRssUsername(): String = sharedPreferences.getString(KEY_FRESHRSS_USERNAME, "").orEmpty()

    fun setFreshRssUsername(username: String) {
        sharedPreferences.edit()
            .putString(KEY_FRESHRSS_USERNAME, username)
            .apply()
    }

    fun getFreshRssApiPassword(): String = sharedPreferences.getString(KEY_FRESHRSS_API_PASSWORD, "").orEmpty()

    fun setFreshRssApiPassword(apiPassword: String) {
        sharedPreferences.edit()
            .putString(KEY_FRESHRSS_API_PASSWORD, apiPassword)
            .apply()
    }

    fun hasFreshRssCredentials(): Boolean =
        getFreshRssServerUrl().isNotBlank() &&
            getFreshRssUsername().isNotBlank() &&
            getFreshRssApiPassword().isNotBlank()

    fun getPaywallBypassMethod(): PaywallBypassMethod {
        val savedMethod = sharedPreferences.getString(KEY_PAYWALL_BYPASS_METHOD, PaywallBypassMethod.SMRY_AI.name)
        return PaywallBypassMethod.entries.find { it.name == savedMethod } ?: PaywallBypassMethod.SMRY_AI
    }

    fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        sharedPreferences.edit()
            .putString(KEY_PAYWALL_BYPASS_METHOD, method.name)
            .apply()
    }

    fun getBionicReadingEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIONIC_READING_ENABLED, false)
    }

    fun setBionicReadingEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BIONIC_READING_ENABLED, enabled)
            .apply()
    }

    fun getAiModel(): AiModel {
        val savedModel = sharedPreferences.getString(KEY_AI_MODEL, AiModel.getDefault().name)
        return AiModel.entries.find { it.name == savedModel } ?: AiModel.getDefault()
    }

    fun setAiModel(model: AiModel) {
        sharedPreferences.edit()
            .putString(KEY_AI_MODEL, model.name)
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

    fun getCredibilityScoreEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_CREDIBILITY_SCORE_ENABLED, false)
    }

    fun setCredibilityScoreEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_CREDIBILITY_SCORE_ENABLED, enabled)
            .apply()
    }

    companion object {
        const val DEFAULT_FRESHRSS_SERVER_URL = "https://rss.hiosdra.com"
        private const val KEY_FRESHRSS_SERVER_URL = "freshrss_server_url"
        private const val KEY_FRESHRSS_USERNAME = "freshrss_username"
        private const val KEY_FRESHRSS_API_PASSWORD = "freshrss_api_password"
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_BIONIC_READING_ENABLED = "bionic_reading_enabled"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_SYNC_PERFORMANCE_RECORDS = "sync_performance_records"
        private const val KEY_CREDIBILITY_SCORE_ENABLED = "credibility_score_enabled"
        private const val MAX_PERFORMANCE_RECORDS = 50
    }
}
