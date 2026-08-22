package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.BackendPreferences
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.OfflineReadinessStore
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.domain.model.BackendType

class SettingsUseCase(
    private val backendPreferences: BackendPreferences,
    private val aiPreferences: AiPreferences,
    private val syncPreferences: SyncPreferences,
    private val feeds: FeedStore,
    private val aiModels: AiModelCatalog,
    private val cache: CacheStore,
    private val offlineReadiness: OfflineReadinessStore,
    private val sync: SyncRequester,
    private val preferenceWrites: PreferenceWriteBarrier? = null
) {
    fun getOpenRouterApiKey() = aiPreferences.getOpenRouterApiKey()
    fun setOpenRouterApiKey(apiKey: String) = aiPreferences.setOpenRouterApiKey(apiKey)
    fun getAiModelId() = aiPreferences.getAiModelId()
    fun setAiModelId(modelId: String) = aiPreferences.setAiModelId(modelId)
    fun getBackendType() = backendPreferences.getBackendType()
    fun setBackendType(backendType: BackendType) =
        backendPreferences.setBackendType(backendType)
    fun getServerUrl(backendType: BackendType) =
        backendPreferences.getServerUrl(backendType)
    fun setServerUrl(backendType: BackendType, url: String) =
        backendPreferences.setServerUrl(backendType, url)
    fun getFreshRssUsername() = backendPreferences.getFreshRssUsername()
    fun setFreshRssUsername(username: String) = backendPreferences.setFreshRssUsername(username)
    fun getBackendSecret(backendType: BackendType) =
        backendPreferences.getBackendSecret(backendType)
    fun setBackendSecret(backendType: BackendType, secret: String) =
        backendPreferences.setBackendSecret(backendType, secret)
    fun getLastSyncTimestamp() = syncPreferences.getLastSyncTimestamp()
    fun getSyncIntervalMinutes() = syncPreferences.getSyncIntervalMinutes()
    fun setSyncIntervalMinutes(minutes: Int) = syncPreferences.setSyncIntervalMinutes(minutes)
    fun getSyncOnUnmeteredOnly() = syncPreferences.getSyncOnUnmeteredOnly()
    fun setSyncOnUnmeteredOnly(enabled: Boolean) = syncPreferences.setSyncOnUnmeteredOnly(enabled)
    fun getSyncWhileRoaming() = syncPreferences.getSyncWhileRoaming()
    fun setSyncWhileRoaming(enabled: Boolean) = syncPreferences.setSyncWhileRoaming(enabled)
    fun getQuietHoursEnabled() = syncPreferences.getQuietHoursEnabled()
    fun setQuietHoursEnabled(enabled: Boolean) = syncPreferences.setQuietHoursEnabled(enabled)
    fun getQuietHoursStartHour() = syncPreferences.getQuietHoursStartHour()
    fun getQuietHoursEndHour() = syncPreferences.getQuietHoursEndHour()
    fun setQuietHours(startHour: Int, endHour: Int) = syncPreferences.setQuietHours(startHour, endHour)
    fun getOfflineBacklogTarget() = syncPreferences.getOfflineBacklogTarget()
    fun setOfflineBacklogTarget(target: Int) = syncPreferences.setOfflineBacklogTarget(target)
    fun getImageDownloadEnabled() = syncPreferences.getImageDownloadEnabled()
    fun setImageDownloadEnabled(enabled: Boolean) = syncPreferences.setImageDownloadEnabled(enabled)
    fun getImageCacheBudgetMegabytes() = syncPreferences.getImageCacheBudgetMegabytes()
    fun setImageCacheBudgetMegabytes(megabytes: Int) = syncPreferences.setImageCacheBudgetMegabytes(megabytes)

    fun observeOfflineReadiness() = offlineReadiness.observe()
    fun observeOfflinePreparation() = sync.observeOfflinePreparation()
    fun observeRequestedSync() = sync.observeRequestedSync()
    fun prepareForOffline() = sync.prepareForOffline()
    fun prepareFullOffline() = sync.prepareFullOffline()
    fun schedulePeriodicSync() = sync.schedulePeriodicSync()
    suspend fun cancelAllSync() = sync.cancelAllSync()
    suspend fun cancelAndClearBackendData() {
        sync.cancelAllSync()
        cache.clearBackendData()
    }
    suspend fun awaitPreferenceWrites() = preferenceWrites?.awaitWrites()
    fun resyncNow() = sync.resyncNow()
    fun syncNow(forceFullSync: Boolean, userVisible: Boolean) =
        sync.syncNow(forceFullSync = forceFullSync, userVisible = userVisible)

    suspend fun ensureCacheOwner() = cache.ensureCacheOwner()
    suspend fun ensureCacheOwnerWhenConfigured() = cache.ensureCacheOwnerWhenConfigured()
    suspend fun clearBackendData() = cache.clearBackendData()
    suspend fun getModels(forceRefresh: Boolean) = aiModels.getModels(forceRefresh)
    suspend fun verifyConnection() = feeds.verifyConnection()
}
