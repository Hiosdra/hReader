package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.domain.model.BackendType
import kotlinx.coroutines.flow.Flow

interface AppPreferences {
    fun getBackendType(): BackendType
    fun setBackendType(backendType: BackendType)
    fun getServerUrl(backendType: BackendType): String
    fun setServerUrl(backendType: BackendType, url: String)
    fun getBackendSecret(backendType: BackendType): String
    fun setBackendSecret(backendType: BackendType, secret: String)
    fun getFreshRssUsername(): String
    fun setFreshRssUsername(username: String)
    fun hasBackendCredentials(): Boolean
    fun getOpenRouterApiKey(): String
    fun setOpenRouterApiKey(apiKey: String)
    fun getPaywallBypassMethod(): PaywallBypassMethod
    fun setPaywallBypassMethod(method: PaywallBypassMethod)
    fun getBionicReadingEnabled(): Boolean
    fun setBionicReadingEnabled(enabled: Boolean)
    fun getSentryReportingEnabled(): Boolean
    fun setSentryReportingEnabled(enabled: Boolean)
    fun observeBionicReadingEnabled(): Flow<Boolean>
    fun getAiModelId(): String
    fun setAiModelId(modelId: String)
    fun getLastSyncTimestamp(): Long
    fun setLastSyncTimestamp(timestamp: Long)
    fun getCacheOwnerKey(): String
    fun setCacheOwnerKey(ownerKey: String)
    fun observeLastSyncTimestamp(): Flow<Long>
    fun getLastFullSyncTimestamp(): Long
    fun setLastFullSyncTimestamp(timestamp: Long)
    fun getSyncPerformanceRecords(): List<SyncPerformanceRecord>
    fun addSyncPerformanceRecord(record: SyncPerformanceRecord)
    fun clearSyncPerformanceRecords()
    fun getOfflineBacklogTarget(): Int
    fun setOfflineBacklogTarget(target: Int)
    fun getImageDownloadEnabled(): Boolean
    fun setImageDownloadEnabled(enabled: Boolean)
    fun getImageCacheBudgetMegabytes(): Int
    fun setImageCacheBudgetMegabytes(megabytes: Int)
    fun getSyncIntervalMinutes(): Int
    fun setSyncIntervalMinutes(minutes: Int)
    fun getSyncOnUnmeteredOnly(): Boolean
    fun setSyncOnUnmeteredOnly(enabled: Boolean)
    fun getSyncWhileRoaming(): Boolean
    fun setSyncWhileRoaming(enabled: Boolean)
    fun getQuietHoursEnabled(): Boolean
    fun setQuietHoursEnabled(enabled: Boolean)
    fun getQuietHoursStartHour(): Int
    fun getQuietHoursEndHour(): Int
    fun setQuietHours(startHour: Int, endHour: Int)
    fun getLastChainedSyncTimestamp(): Long
    fun setLastChainedSyncTimestamp(timestamp: Long)
    fun getCredibilityScoreEnabled(): Boolean
    fun setCredibilityScoreEnabled(enabled: Boolean)
    fun getTtsModel(): TtsModel
    fun setTtsModel(model: TtsModel)
    fun getTtsModelForLanguage(language: String): TtsModel
    fun getTtsLanguageOverrides(): Map<String, TtsModel>
    fun setTtsLanguageOverride(language: String, model: TtsModel?)
    fun getTtsSpeed(): Float
    fun setTtsSpeed(speed: Float)
    fun getTtsAdvancedSettings(): TtsAdvancedSettings
    fun setTtsAdvancedSettings(settings: TtsAdvancedSettings)
}
