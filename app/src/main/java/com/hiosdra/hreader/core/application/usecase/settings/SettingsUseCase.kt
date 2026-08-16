package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.OfflineReadinessStore
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.domain.model.BackendType

class SettingsUseCase(
    private val preferences: AppPreferences,
    private val feeds: FeedStore,
    private val aiModels: AiModelCatalog,
    private val cache: CacheStore,
    private val offlineReadiness: OfflineReadinessStore,
    private val sync: SyncRequester
) {
    fun getOpenRouterApiKey() = preferences.getOpenRouterApiKey()
    fun setOpenRouterApiKey(apiKey: String) = preferences.setOpenRouterApiKey(apiKey)
    fun getAiModelId() = preferences.getAiModelId()
    fun setAiModelId(modelId: String) = preferences.setAiModelId(modelId)
    fun getBackendType() = preferences.getBackendType()
    fun setBackendType(backendType: BackendType) =
        preferences.setBackendType(backendType)
    fun getServerUrl(backendType: BackendType) =
        preferences.getServerUrl(backendType)
    fun setServerUrl(backendType: BackendType, url: String) =
        preferences.setServerUrl(backendType, url)
    fun getFreshRssUsername() = preferences.getFreshRssUsername()
    fun setFreshRssUsername(username: String) = preferences.setFreshRssUsername(username)
    fun getBackendSecret(backendType: BackendType) =
        preferences.getBackendSecret(backendType)
    fun setBackendSecret(backendType: BackendType, secret: String) =
        preferences.setBackendSecret(backendType, secret)
    fun getLastSyncTimestamp() = preferences.getLastSyncTimestamp()
    fun getSyncIntervalMinutes() = preferences.getSyncIntervalMinutes()
    fun setSyncIntervalMinutes(minutes: Int) = preferences.setSyncIntervalMinutes(minutes)
    fun getSyncOnUnmeteredOnly() = preferences.getSyncOnUnmeteredOnly()
    fun setSyncOnUnmeteredOnly(enabled: Boolean) = preferences.setSyncOnUnmeteredOnly(enabled)
    fun getSyncWhileRoaming() = preferences.getSyncWhileRoaming()
    fun setSyncWhileRoaming(enabled: Boolean) = preferences.setSyncWhileRoaming(enabled)
    fun getQuietHoursEnabled() = preferences.getQuietHoursEnabled()
    fun setQuietHoursEnabled(enabled: Boolean) = preferences.setQuietHoursEnabled(enabled)
    fun getQuietHoursStartHour() = preferences.getQuietHoursStartHour()
    fun getQuietHoursEndHour() = preferences.getQuietHoursEndHour()
    fun setQuietHours(startHour: Int, endHour: Int) = preferences.setQuietHours(startHour, endHour)
    fun getOfflineBacklogTarget() = preferences.getOfflineBacklogTarget()
    fun setOfflineBacklogTarget(target: Int) = preferences.setOfflineBacklogTarget(target)
    fun getImageDownloadEnabled() = preferences.getImageDownloadEnabled()
    fun setImageDownloadEnabled(enabled: Boolean) = preferences.setImageDownloadEnabled(enabled)
    fun getImageCacheBudgetMegabytes() = preferences.getImageCacheBudgetMegabytes()
    fun setImageCacheBudgetMegabytes(megabytes: Int) = preferences.setImageCacheBudgetMegabytes(megabytes)

    fun observeOfflineReadiness() = offlineReadiness.observe()
    fun observeOfflinePreparation() = sync.observeOfflinePreparation()
    fun observeRequestedSync() = sync.observeRequestedSync()
    fun prepareForOffline() = sync.prepareForOffline()
    fun prepareFullOffline() = sync.prepareFullOffline()
    fun schedulePeriodicSync() = sync.schedulePeriodicSync()
    fun cancelAllSync() = sync.cancelAllSync()
    fun resyncNow() = sync.resyncNow()
    fun syncNow(forceFullSync: Boolean, userVisible: Boolean) =
        sync.syncNow(forceFullSync = forceFullSync, userVisible = userVisible)

    suspend fun ensureCacheOwner() = cache.ensureCacheOwner()
    suspend fun ensureCacheOwnerWhenConfigured() = cache.ensureCacheOwnerWhenConfigured()
    suspend fun clearBackendData() = cache.clearBackendData()
    suspend fun getModels(forceRefresh: Boolean) = aiModels.getModels(forceRefresh)
    suspend fun verifyConnection() = feeds.verifyConnection()
}
