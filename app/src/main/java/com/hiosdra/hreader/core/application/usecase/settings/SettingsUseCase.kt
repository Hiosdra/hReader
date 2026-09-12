package com.hiosdra.hreader.core.application.usecase.settings

import com.hiosdra.hreader.core.application.port.out.AiModelCatalog
import com.hiosdra.hreader.core.application.port.out.AiPreferences
import com.hiosdra.hreader.core.application.port.out.BackendSessionStore
import com.hiosdra.hreader.core.application.port.out.CacheStore
import com.hiosdra.hreader.core.application.port.out.FeedStore
import com.hiosdra.hreader.core.application.port.out.OfflineReadinessStore
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.port.out.SyncPreferences
import com.hiosdra.hreader.core.application.port.out.SyncRequester
import com.hiosdra.hreader.core.application.port.out.SyncSessionGate
import com.hiosdra.hreader.core.application.port.out.NoopSyncSessionGate
import com.hiosdra.hreader.core.application.settings.BackendConfiguration
import com.hiosdra.hreader.core.application.sync.SyncIntent
import com.hiosdra.hreader.core.domain.model.BackendType

class SettingsUseCase(
    private val backendSession: BackendSessionStore,
    private val aiPreferences: AiPreferences,
    private val syncPreferences: SyncPreferences,
    private val feeds: FeedStore,
    private val aiModels: AiModelCatalog,
    private val cache: CacheStore,
    private val offlineReadiness: OfflineReadinessStore,
    private val sync: SyncRequester,
    private val preferenceWrites: PreferenceWriteBarrier? = null,
    private val sessionGate: SyncSessionGate = NoopSyncSessionGate
) {
    fun getOpenRouterApiKey() = aiPreferences.getOpenRouterApiKey()
    fun setOpenRouterApiKey(apiKey: String) = aiPreferences.setOpenRouterApiKey(apiKey)
    fun getAiModelId() = aiPreferences.getAiModelId()
    fun setAiModelId(modelId: String) = aiPreferences.setAiModelId(modelId)
    fun getBackendType() = backendSession.getBackendConfiguration().backendType
    fun getServerUrl(backendType: BackendType) =
        backendSession.getBackendConfiguration().serverUrlFor(backendType)
    fun getFreshRssUsername() = backendSession.getBackendConfiguration().freshRssUsername
    fun getBackendSecret(backendType: BackendType) =
        backendSession.getBackendConfiguration().secretFor(backendType)
    fun getBackendConfiguration(): BackendConfiguration = backendSession.getBackendConfiguration()

    suspend fun applyBackendConfiguration(configuration: BackendConfiguration): Boolean {
        sync.cancelAllSync()
        return sessionGate.withSessionChange {
            backendSession.commitBackendConfiguration(configuration)
            preferenceWrites?.awaitWrites()
            cache.ensureCacheOwner()
        }
    }

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
    fun prepareForOffline() = sync.request(SyncIntent.PrepareOffline)
    fun prepareFullOffline() = sync.request(SyncIntent.PrepareFullOffline)
    fun schedulePeriodicSync() = sync.schedulePeriodicSync()
    suspend fun cancelAllSync() = sync.cancelAllSync()
    suspend fun cancelAndClearBackendData() {
        sync.cancelAllSync()
        cache.clearBackendData()
    }
    suspend fun awaitPreferenceWrites() = preferenceWrites?.awaitWrites()
    fun resyncNow() = sync.request(SyncIntent.Resync)
    fun syncNow(forceFullSync: Boolean, userVisible: Boolean) =
        sync.request(SyncIntent.User(forceFullSync, userVisible))

    suspend fun ensureCacheOwner() = cache.ensureCacheOwner()
    suspend fun ensureCacheOwnerWhenConfigured() = cache.ensureCacheOwnerWhenConfigured()
    suspend fun clearBackendData() = cache.clearBackendData()
    suspend fun getModels(forceRefresh: Boolean) = aiModels.getModels(forceRefresh)
    suspend fun verifyConnection() = feeds.verifyConnection()
    suspend fun verifyConnection(configuration: BackendConfiguration) =
        backendSession.withTemporaryBackendConfiguration(configuration) {
            feeds.verifyConnection()
        }
}
