package com.hiosdra.hreader.adapter.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.hiosdra.hreader.core.application.ai.AiModel
import com.hiosdra.hreader.core.application.ai.GemmaBackend
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.port.out.PreferenceWriteBarrier
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides
import com.hiosdra.hreader.core.domain.model.BackendType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PreferencesManager(context: Context) : AppPreferences, PreferenceWriteBarrier {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secretCipher = SecretCipher(AndroidKeystoreSecretKeyProvider()::invoke)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val syncRecordsAdapter = moshi.adapter<List<SyncPerformanceRecord>>(
        Types.newParameterizedType(List::class.java, SyncPerformanceRecord::class.java)
    )

    private val preferencesDataStore by lazy {
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { applicationContext.preferencesDataStoreFile(PREFS_FILE) }
        )
    }

    private val secretDataStore by lazy {
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { applicationContext.preferencesDataStoreFile(SECRETS_FILE) }
        )
    }

    private val preferenceState: AtomicReference<PreferenceState>
    private val secretState: AtomicReference<SecretState>
    private val preferenceStateLock = Any()
    private val secretStateLock = Any()
    private val pendingPreferenceTransforms = mutableListOf<(PreferenceState) -> PreferenceState>()
    private val pendingSecretTransforms = mutableListOf<(SecretState) -> SecretState>()
    private val preferenceReady = CompletableDeferred<Unit>()
    private val secretReady = CompletableDeferred<Unit>()
    private val preferenceWrites = Channel<WriteRequest>(Channel.UNLIMITED)
    private val secretWrites = Channel<WriteRequest>(Channel.UNLIMITED)
    private val preferenceWriteFailure = AtomicReference<Throwable?>(null)
    private val secretWriteFailure = AtomicReference<Throwable?>(null)

    init {
        preferenceState = AtomicReference(PreferenceState())
        secretState = AtomicReference(SecretState())

        scope.launch {
            hydratePreferences()
        }
        scope.launch {
            hydrateSecrets()
        }
        scope.launch {
            processWrites(
                preferencesDataStore,
                preferenceWrites,
                preferenceWriteFailure,
                preferenceReady
            )
        }
        scope.launch {
            processWrites(secretDataStore, secretWrites, secretWriteFailure, secretReady)
        }
    }

    override suspend fun awaitReady() {
        preferenceReady.await()
        secretReady.await()
    }

    override suspend fun awaitWrites() {
        awaitReady()
        val preferenceCompletion = CompletableDeferred<Unit>()
        val secretCompletion = CompletableDeferred<Unit>()
        preferenceWrites.send(WriteRequest({}, preferenceCompletion))
        secretWrites.send(WriteRequest({}, secretCompletion))
        val preferenceFailure = awaitCompletion(preferenceCompletion)
        val secretFailure = awaitCompletion(secretCompletion)
        preferenceWriteFailure.getAndSet(null)?.let { preferenceFailure ?: throw it }
        secretWriteFailure.getAndSet(null)?.let { secretFailure ?: throw it }
        preferenceFailure?.let { throw it }
        secretFailure?.let { throw it }
    }

    override fun getBackendType(): BackendType = preferenceState.get().backendType

    override fun setBackendType(backendType: BackendType) {
        updatePreferences(
            transform = { it.copy(backendType = backendType) },
            write = { this[backendTypeKey] = backendType.name }
        )
    }

    override fun getServerUrl(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> preferenceState.get().freshRssServerUrl
        BackendType.MINIFLUX -> preferenceState.get().minifluxServerUrl
    }

    override fun setServerUrl(backendType: BackendType, url: String) {
        updatePreferences(
            transform = { state ->
                when (backendType) {
                    BackendType.FRESHRSS -> state.copy(freshRssServerUrl = url)
                    BackendType.MINIFLUX -> state.copy(minifluxServerUrl = url)
                }
            },
            write = { this[serverUrlKey(backendType)] = url }
        )
    }

    override fun getBackendSecret(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> secretState.get().freshRssApiPassword
        BackendType.MINIFLUX -> secretState.get().minifluxApiToken
    }

    override fun setBackendSecret(backendType: BackendType, secret: String) {
        updateSecrets(
            transform = { state ->
                when (backendType) {
                    BackendType.FRESHRSS -> state.copy(freshRssApiPassword = secret)
                    BackendType.MINIFLUX -> state.copy(minifluxApiToken = secret)
                }
            },
            write = { writeSecret(backendSecretKey(backendType), secret) }
        )
    }

    override fun getFreshRssUsername(): String = secretState.get().freshRssUsername

    override fun setFreshRssUsername(username: String) {
        updateSecrets(
            transform = { it.copy(freshRssUsername = username) },
            write = { writeSecret(freshRssUsernameKey, username) }
        )
    }

    override fun hasBackendCredentials(): Boolean {
        val backendType = getBackendType()
        if (getServerUrl(backendType).isBlank() || getBackendSecret(backendType).isBlank()) return false
        return !backendType.requiresUsername || getFreshRssUsername().isNotBlank()
    }

    override fun getOpenRouterApiKey(): String = secretState.get().openRouterApiKey

    override fun setOpenRouterApiKey(apiKey: String) {
        updateSecrets(
            transform = { it.copy(openRouterApiKey = apiKey) },
            write = { writeSecret(openRouterApiKey, apiKey) }
        )
    }

    override fun getPaywallBypassMethod(): PaywallBypassMethod = preferenceState.get().paywallBypassMethod

    override fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        updatePreferences(
            transform = { it.copy(paywallBypassMethod = method) },
            write = { this[paywallBypassMethodKey] = method.name }
        )
    }

    override fun getBionicReadingEnabled(): Boolean = preferenceState.get().bionicReadingEnabled

    override fun setBionicReadingEnabled(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(bionicReadingEnabled = enabled) },
            write = { this[bionicReadingEnabledKey] = enabled }
        )
    }

    override fun getSentryReportingEnabled(): Boolean = preferenceState.get().sentryReportingEnabled

    override fun setSentryReportingEnabled(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(sentryReportingEnabled = enabled) },
            write = { this[sentryReportingEnabledKey] = enabled }
        )
    }

    override fun observeBionicReadingEnabled(): Flow<Boolean> = preferencesDataStore.data
        .map { it[bionicReadingEnabledKey] ?: false }
        .distinctUntilChanged()

    override fun getAiModelId(): String = preferenceState.get().aiModelId

    override fun setAiModelId(modelId: String) {
        updatePreferences(
            transform = { it.copy(aiModelId = modelId) },
            write = { this[aiModelIdKey] = modelId }
        )
    }

    override fun observeAiModelId(): Flow<String> = preferencesDataStore.data
        .map { it[aiModelIdKey] ?: AiModel.DEFAULT_ID }
        .distinctUntilChanged()

    override fun getGemmaBackend(): GemmaBackend = preferenceState.get().gemmaBackend

    override fun setGemmaBackend(backend: GemmaBackend) {
        updatePreferences(
            transform = { it.copy(gemmaBackend = backend) },
            write = { this[gemmaBackendKey] = backend.name }
        )
    }

    override fun getGemmaDownloadOnUnmeteredOnly(): Boolean =
        preferenceState.get().gemmaDownloadOnUnmeteredOnly

    override fun setGemmaDownloadOnUnmeteredOnly(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(gemmaDownloadOnUnmeteredOnly = enabled) },
            write = { this[gemmaDownloadOnUnmeteredOnlyKey] = enabled }
        )
    }

    override fun getLastSyncTimestamp(): Long = preferenceState.get().lastSyncTimestamp

    override fun setLastSyncTimestamp(timestamp: Long) {
        updatePreferences(
            transform = { it.copy(lastSyncTimestamp = timestamp) },
            write = { this[lastSyncTimestampKey] = timestamp }
        )
    }

    override fun getCacheOwnerKey(): String = preferenceState.get().cacheOwnerKey

    override fun setCacheOwnerKey(ownerKey: String) {
        updatePreferences(
            transform = { it.copy(cacheOwnerKey = ownerKey) },
            write = { this[cacheOwnerKey] = ownerKey }
        )
    }

    override fun isCacheCleanupPending(): Boolean = preferenceState.get().cacheCleanupPending

    override fun setCacheCleanupPending(pending: Boolean) {
        updatePreferences(
            transform = { it.copy(cacheCleanupPending = pending) },
            write = { this[cacheCleanupPendingKey] = pending }
        )
    }

    override fun observeLastSyncTimestamp(): Flow<Long> = preferencesDataStore.data
        .map { it[lastSyncTimestampKey] ?: 0L }
        .distinctUntilChanged()

    override fun getLastFullSyncTimestamp(): Long = preferenceState.get().lastFullSyncTimestamp

    override fun setLastFullSyncTimestamp(timestamp: Long) {
        updatePreferences(
            transform = { it.copy(lastFullSyncTimestamp = timestamp) },
            write = { this[lastFullSyncTimestampKey] = timestamp }
        )
    }

    override fun getSyncPerformanceRecords(): List<SyncPerformanceRecord> =
        preferenceState.get().syncPerformanceRecords

    override fun addSyncPerformanceRecord(record: SyncPerformanceRecord) {
        updatePreferences(
            transform = { state ->
                state.copy(syncPerformanceRecords = (listOf(record) + state.syncPerformanceRecords)
                    .take(MAX_PERFORMANCE_RECORDS))
            },
            write = {
                val records = (listOf(record) + decodeSyncPerformanceRecords(this[syncPerformanceRecordsKey]))
                    .take(MAX_PERFORMANCE_RECORDS)
                this[syncPerformanceRecordsKey] = syncRecordsAdapter.toJson(records)
            }
        )
    }

    override fun clearSyncPerformanceRecords() {
        updatePreferences(
            transform = { it.copy(syncPerformanceRecords = emptyList()) },
            write = { remove(syncPerformanceRecordsKey) }
        )
    }

    override fun getOfflineBacklogTarget(): Int = preferenceState.get().offlineBacklogTarget

    override fun setOfflineBacklogTarget(target: Int) {
        val normalizedTarget = target.coerceAtLeast(0)
        updatePreferences(
            transform = { it.copy(offlineBacklogTarget = normalizedTarget) },
            write = { this[offlineBacklogTargetKey] = normalizedTarget }
        )
    }

    override fun getImageDownloadEnabled(): Boolean = preferenceState.get().imageDownloadEnabled

    override fun setImageDownloadEnabled(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(imageDownloadEnabled = enabled) },
            write = { this[imageDownloadEnabledKey] = enabled }
        )
    }

    override fun getImageCacheBudgetMegabytes(): Int = preferenceState.get().imageCacheBudgetMegabytes

    override fun setImageCacheBudgetMegabytes(megabytes: Int) {
        val normalizedMegabytes = megabytes.coerceAtLeast(0)
        updatePreferences(
            transform = { it.copy(imageCacheBudgetMegabytes = normalizedMegabytes) },
            write = { this[imageCacheBudgetMegabytesKey] = normalizedMegabytes }
        )
    }

    override fun getSyncIntervalMinutes(): Int = preferenceState.get().syncIntervalMinutes

    override fun setSyncIntervalMinutes(minutes: Int) {
        val normalizedMinutes = minutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        updatePreferences(
            transform = { it.copy(syncIntervalMinutes = normalizedMinutes) },
            write = { this[syncIntervalMinutesKey] = normalizedMinutes }
        )
    }

    override fun getSyncOnUnmeteredOnly(): Boolean = preferenceState.get().syncOnUnmeteredOnly

    override fun setSyncOnUnmeteredOnly(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(syncOnUnmeteredOnly = enabled) },
            write = { this[syncOnUnmeteredOnlyKey] = enabled }
        )
    }

    override fun getSyncWhileRoaming(): Boolean = preferenceState.get().syncWhileRoaming

    override fun setSyncWhileRoaming(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(syncWhileRoaming = enabled) },
            write = { this[syncWhileRoamingKey] = enabled }
        )
    }

    override fun getQuietHoursEnabled(): Boolean = preferenceState.get().quietHoursEnabled

    override fun setQuietHoursEnabled(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(quietHoursEnabled = enabled) },
            write = { this[quietHoursEnabledKey] = enabled }
        )
    }

    override fun getQuietHoursStartHour(): Int = preferenceState.get().quietHoursStartHour

    override fun getQuietHoursEndHour(): Int = preferenceState.get().quietHoursEndHour

    override fun setQuietHours(startHour: Int, endHour: Int) {
        val normalizedStartHour = startHour.coerceIn(0, 23)
        val normalizedEndHour = endHour.coerceIn(0, 23)
        updatePreferences(
            transform = {
                it.copy(
                    quietHoursStartHour = normalizedStartHour,
                    quietHoursEndHour = normalizedEndHour
                )
            },
            write = {
                this[quietHoursStartHourKey] = normalizedStartHour
                this[quietHoursEndHourKey] = normalizedEndHour
            }
        )
    }

    override fun getLastChainedSyncTimestamp(): Long = preferenceState.get().lastChainedSyncTimestamp

    override fun setLastChainedSyncTimestamp(timestamp: Long) {
        updatePreferences(
            transform = { it.copy(lastChainedSyncTimestamp = timestamp) },
            write = { this[lastChainedSyncTimestampKey] = timestamp }
        )
    }

    override fun getCredibilityScoreEnabled(): Boolean = preferenceState.get().credibilityScoreEnabled

    override fun setCredibilityScoreEnabled(enabled: Boolean) {
        updatePreferences(
            transform = { it.copy(credibilityScoreEnabled = enabled) },
            write = { this[credibilityScoreEnabledKey] = enabled }
        )
    }

    override fun getTtsModel(): TtsModel = preferenceState.get().ttsModel

    override fun setTtsModel(model: TtsModel) {
        updatePreferences(
            transform = { it.copy(ttsModel = model) },
            write = { this[ttsModelKey] = model.name }
        )
    }

    override fun getTtsModelForLanguage(language: String): TtsModel =
        preferenceState.get().ttsLanguageOverrides[language] ?: getTtsModel()

    override fun getTtsLanguageOverrides(): Map<String, TtsModel> =
        preferenceState.get().ttsLanguageOverrides

    override fun setTtsLanguageOverride(language: String, model: TtsModel?) {
        updatePreferences(
            transform = { state ->
                state.copy(ttsLanguageOverrides = state.ttsLanguageOverrides.updated(language, model))
            },
            write = {
                val overrides = parseTtsLanguageOverrides(this[ttsLanguageOverridesKey].orEmpty())
                    .updated(language, model)
                this[ttsLanguageOverridesKey] = overrides.toPreferenceSet()
            }
        )
    }

    override fun getTtsSpeed(): Float = preferenceState.get().ttsSpeed

    override fun setTtsSpeed(speed: Float) {
        val normalizedSpeed = speed.coerceIn(0.7f, 1.4f)
        updatePreferences(
            transform = { it.copy(ttsSpeed = normalizedSpeed) },
            write = { this[ttsSpeedKey] = normalizedSpeed }
        )
    }

    override fun getTtsAdvancedSettings(): TtsAdvancedSettings = preferenceState.get().ttsAdvancedSettings

    override fun setTtsAdvancedSettings(settings: TtsAdvancedSettings) {
        val normalizedSettings = settings.normalized()
        updatePreferences(
            transform = { it.copy(ttsAdvancedSettings = normalizedSettings) },
            write = {
                this[ttsThreadsKey] = normalizedSettings.numThreads
                this[ttsSilenceScaleKey] = normalizedSettings.silenceScale
                this[ttsSupertonicSpeakerKey] = normalizedSettings.supertonicSpeaker
                this[ttsSupertonicStepsKey] = normalizedSettings.supertonicSteps
                this[ttsKokoroSpeakerKey] = normalizedSettings.kokoroSpeaker
                this[ttsKittenSpeakerKey] = normalizedSettings.kittenSpeaker
                this[ttsGosiaNoiseScaleKey] = normalizedSettings.vitsNoiseScale
                this[ttsGosiaDurationNoiseScaleKey] = normalizedSettings.vitsDurationNoiseScale
            }
        )
    }

    private fun updatePreferences(
        transform: (PreferenceState) -> PreferenceState,
        write: suspend MutablePreferences.() -> Unit
    ) {
        synchronized(preferenceStateLock) {
            if (!preferenceReady.isCompleted) pendingPreferenceTransforms += transform
            preferenceState.updateAndGet(transform)
            preferenceWrites.trySend(WriteRequest(write))
        }
    }

    private fun updateSecrets(
        transform: (SecretState) -> SecretState,
        write: suspend MutablePreferences.() -> Unit
    ) {
        synchronized(secretStateLock) {
            if (!secretReady.isCompleted) pendingSecretTransforms += transform
            secretState.updateAndGet(transform)
            secretWrites.trySend(WriteRequest(write))
        }
    }

    private suspend fun hydratePreferences() {
        try {
            val loaded = preferencesDataStore.data.first().toPreferenceState()
            synchronized(preferenceStateLock) {
                preferenceState.set(pendingPreferenceTransforms.fold(loaded) { state, transform ->
                    transform(state)
                })
                pendingPreferenceTransforms.clear()
                preferenceReady.complete(Unit)
            }
        } catch (error: Throwable) {
            synchronized(preferenceStateLock) {
                preferenceReady.completeExceptionally(error)
            }
            throw error
        } finally {
            if (!preferenceReady.isCompleted) preferenceReady.complete(Unit)
        }
    }

    private suspend fun hydrateSecrets() {
        try {
            migrateSecrets()
            val loaded = secretDataStore.data.first().toSecretState()
            synchronized(secretStateLock) {
                secretState.set(pendingSecretTransforms.fold(loaded) { state, transform ->
                    transform(state)
                })
                pendingSecretTransforms.clear()
                secretReady.complete(Unit)
            }
        } catch (error: Throwable) {
            synchronized(secretStateLock) {
                secretReady.completeExceptionally(error)
            }
            throw error
        } finally {
            if (!secretReady.isCompleted) secretReady.complete(Unit)
        }
    }

    private suspend fun migrateSecrets() {
        val loaded = secretDataStore.data.first()
        val legacyValueCount = legacySecretKeys.count { key ->
            loaded[key]?.isNullOrBlank() == false
        }
        val encryptedValueCount = encryptedSecretKeys.count { key ->
            loaded[key]?.isNullOrBlank() == false
        }
        if (legacyValueCount == 0 && encryptedValueCount == 0) return

        val values = try {
            secretDefinitions.mapNotNull { definition ->
                val legacy = loaded[definition.legacyKey]
                val encrypted = loaded[definition.encryptedKey]
                when {
                    encrypted != null -> definition to secretCipher.decrypt(encrypted)
                    !legacy.isNullOrBlank() -> definition to legacy
                    else -> null
                }
            }.toMap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            clearSecretStorage()
            return
        }

        if (legacyValueCount > 0) {
            secretDataStore.edit { preferences ->
                values.forEach { (definition, value) ->
                    preferences[definition.encryptedKey] = secretCipher.encrypt(value)
                }
                preferences[secretMigrationStateKey] = SECRET_MIGRATION_PREPARED
            }
            val verified = runCatching {
                val prepared = secretDataStore.data.first()
                values.all { (definition, expected) ->
                    prepared[definition.encryptedKey]?.let(secretCipher::decrypt) == expected
                }
            }.getOrDefault(false)
            if (!verified) {
                clearSecretStorage()
                return
            }
            secretDataStore.edit { preferences ->
                secretDefinitions.forEach { definition -> preferences.remove(definition.legacyKey) }
                preferences[secretMigrationStateKey] = SECRET_MIGRATION_COMPLETE
            }
        } else if (values.size != encryptedValueCount) {
            clearSecretStorage()
        }
    }

    private suspend fun clearSecretStorage() {
        secretDataStore.edit { preferences ->
            legacySecretKeys.forEach(preferences::remove)
            encryptedSecretKeys.forEach(preferences::remove)
            preferences.remove(secretMigrationStateKey)
        }
    }

    private fun MutablePreferences.writeSecret(
        legacyKey: Preferences.Key<String>,
        value: String
    ) {
        val encryptedKey = secretDefinitions.first { it.legacyKey == legacyKey }.encryptedKey
        if (value.isBlank()) {
            remove(encryptedKey)
            remove(legacyKey)
        } else {
            this[encryptedKey] = secretCipher.encrypt(value)
            remove(legacyKey)
            this[secretMigrationStateKey] = SECRET_MIGRATION_COMPLETE
        }
    }

    private suspend fun processWrites(
        dataStore: androidx.datastore.core.DataStore<Preferences>,
        writes: Channel<WriteRequest>,
        failure: AtomicReference<Throwable?>,
        ready: CompletableDeferred<Unit>
    ) {
        ready.await()
        for (request in writes) {
            try {
                dataStore.edit(request.transform)
                request.completion?.complete(Unit)
            } catch (error: CancellationException) {
                request.completion?.cancel(error)
                throw error
            } catch (error: Throwable) {
                failure.set(error)
                request.completion?.completeExceptionally(error)
            }
        }
    }

    private suspend fun awaitCompletion(completion: CompletableDeferred<Unit>): Throwable? =
        try {
            completion.await()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error
        }

    private fun Preferences.toPreferenceState() = PreferenceState(
        backendType = BackendType.fromName(this[backendTypeKey]),
        freshRssServerUrl = this[freshRssServerUrlKey].orEmpty(),
        minifluxServerUrl = this[minifluxServerUrlKey].orEmpty(),
        paywallBypassMethod = this[paywallBypassMethodKey].toPaywallBypassMethod(),
        bionicReadingEnabled = this[bionicReadingEnabledKey] ?: false,
        sentryReportingEnabled = this[sentryReportingEnabledKey] ?: true,
        aiModelId = this[aiModelIdKey] ?: AiModel.DEFAULT_ID,
        gemmaBackend = GemmaBackend.fromName(this[gemmaBackendKey]),
        gemmaDownloadOnUnmeteredOnly = this[gemmaDownloadOnUnmeteredOnlyKey] ?: true,
        lastSyncTimestamp = this[lastSyncTimestampKey] ?: 0L,
        cacheOwnerKey = this[cacheOwnerKey].orEmpty(),
        cacheCleanupPending = this[cacheCleanupPendingKey] ?: false,
        lastFullSyncTimestamp = this[lastFullSyncTimestampKey] ?: 0L,
        syncPerformanceRecords = decodeSyncPerformanceRecords(this[syncPerformanceRecordsKey]),
        offlineBacklogTarget = (this[offlineBacklogTargetKey] ?: DEFAULT_OFFLINE_BACKLOG_TARGET)
            .coerceAtLeast(0),
        imageDownloadEnabled = this[imageDownloadEnabledKey] ?: true,
        imageCacheBudgetMegabytes = (this[imageCacheBudgetMegabytesKey] ?: DEFAULT_IMAGE_CACHE_BUDGET_MB)
            .coerceAtLeast(0),
        lastChainedSyncTimestamp = this[lastChainedSyncTimestampKey] ?: 0L,
        syncIntervalMinutes = (this[syncIntervalMinutesKey] ?: SyncDefaults.INTERVAL_MINUTES)
            .coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES),
        syncOnUnmeteredOnly = this[syncOnUnmeteredOnlyKey] ?: false,
        syncWhileRoaming = this[syncWhileRoamingKey] ?: true,
        quietHoursEnabled = this[quietHoursEnabledKey] ?: false,
        quietHoursStartHour = (this[quietHoursStartHourKey] ?: SyncDefaults.QUIET_HOURS_START).coerceIn(0, 23),
        quietHoursEndHour = (this[quietHoursEndHourKey] ?: SyncDefaults.QUIET_HOURS_END).coerceIn(0, 23),
        credibilityScoreEnabled = this[credibilityScoreEnabledKey] ?: false,
        ttsModel = TtsModel.fromName(this[ttsModelKey]),
        ttsLanguageOverrides = parseTtsLanguageOverrides(this[ttsLanguageOverridesKey].orEmpty()),
        ttsSpeed = (this[ttsSpeedKey] ?: 1f).coerceIn(0.7f, 1.4f),
        ttsAdvancedSettings = TtsAdvancedSettings(
            numThreads = (this[ttsThreadsKey] ?: 4).coerceIn(1, 4),
            silenceScale = (this[ttsSilenceScaleKey] ?: 0.2f).coerceIn(0f, 1f),
            supertonicSpeaker = (this[ttsSupertonicSpeakerKey] ?: 0).coerceIn(0, 9),
            supertonicSteps = (this[ttsSupertonicStepsKey] ?: 8).coerceIn(4, 12),
            kokoroSpeaker = (this[ttsKokoroSpeakerKey] ?: 0).coerceIn(0, 102),
            kittenSpeaker = (this[ttsKittenSpeakerKey] ?: 0).coerceIn(0, 7),
            vitsNoiseScale = (this[ttsGosiaNoiseScaleKey] ?: 0.667f).coerceIn(0f, 1f),
            vitsDurationNoiseScale = (this[ttsGosiaDurationNoiseScaleKey] ?: 0.8f).coerceIn(0f, 1f)
        )
    )

    private fun Preferences.toSecretState() = SecretState(
        freshRssUsername = readSecret(freshRssUsernameKey),
        freshRssApiPassword = readSecret(freshRssApiPasswordKey),
        minifluxApiToken = readSecret(minifluxApiTokenKey),
        openRouterApiKey = readSecret(openRouterApiKey)
    )

    private fun Preferences.readSecret(legacyKey: Preferences.Key<String>): String {
        val encryptedKey = secretDefinitions.first { it.legacyKey == legacyKey }.encryptedKey
        return this[encryptedKey]?.let { encoded ->
            runCatching { secretCipher.decrypt(encoded) }.getOrDefault("")
        }.orEmpty()
    }

    private fun decodeSyncPerformanceRecords(json: String?): List<SyncPerformanceRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { syncRecordsAdapter.fromJson(json).orEmpty().toList() }
            .getOrDefault(emptyList())
    }

    private fun String?.toPaywallBypassMethod(): PaywallBypassMethod =
        PaywallBypassMethod.entries.firstOrNull { it.name == this } ?: PaywallBypassMethod.SMRY_AI

    private fun Map<String, TtsModel>.updated(language: String, model: TtsModel?): Map<String, TtsModel> =
        toMutableMap().apply {
            if (model == null) remove(language) else this[language] = model
        }.toMap()

    private fun Map<String, TtsModel>.toPreferenceSet(): Set<String> =
        map { (language, model) -> "$language=${model.name}" }.toSet()

    private fun TtsAdvancedSettings.normalized() = copy(
        numThreads = numThreads.coerceIn(1, 4),
        silenceScale = silenceScale.coerceIn(0f, 1f),
        supertonicSpeaker = supertonicSpeaker.coerceIn(0, 9),
        supertonicSteps = supertonicSteps.coerceIn(4, 12),
        kokoroSpeaker = kokoroSpeaker.coerceIn(0, 102),
        kittenSpeaker = kittenSpeaker.coerceIn(0, 7),
        vitsNoiseScale = vitsNoiseScale.coerceIn(0f, 1f),
        vitsDurationNoiseScale = vitsDurationNoiseScale.coerceIn(0f, 1f)
    )

    private data class PreferenceState(
        val backendType: BackendType = BackendType.FRESHRSS,
        val freshRssServerUrl: String = "",
        val minifluxServerUrl: String = "",
        val paywallBypassMethod: PaywallBypassMethod = PaywallBypassMethod.SMRY_AI,
        val bionicReadingEnabled: Boolean = false,
        val sentryReportingEnabled: Boolean = true,
        val aiModelId: String = AiModel.DEFAULT_ID,
        val gemmaBackend: GemmaBackend = GemmaBackend.AUTO,
        val gemmaDownloadOnUnmeteredOnly: Boolean = true,
        val lastSyncTimestamp: Long = 0L,
        val cacheOwnerKey: String = "",
        val cacheCleanupPending: Boolean = false,
        val lastFullSyncTimestamp: Long = 0L,
        val syncPerformanceRecords: List<SyncPerformanceRecord> = emptyList(),
        val offlineBacklogTarget: Int = DEFAULT_OFFLINE_BACKLOG_TARGET,
        val imageDownloadEnabled: Boolean = true,
        val imageCacheBudgetMegabytes: Int = DEFAULT_IMAGE_CACHE_BUDGET_MB,
        val lastChainedSyncTimestamp: Long = 0L,
        val syncIntervalMinutes: Int = SyncDefaults.INTERVAL_MINUTES,
        val syncOnUnmeteredOnly: Boolean = false,
        val syncWhileRoaming: Boolean = true,
        val quietHoursEnabled: Boolean = false,
        val quietHoursStartHour: Int = SyncDefaults.QUIET_HOURS_START,
        val quietHoursEndHour: Int = SyncDefaults.QUIET_HOURS_END,
        val credibilityScoreEnabled: Boolean = false,
        val ttsModel: TtsModel = TtsModel.SUPERTONIC,
        val ttsLanguageOverrides: Map<String, TtsModel> = emptyMap(),
        val ttsSpeed: Float = 1f,
        val ttsAdvancedSettings: TtsAdvancedSettings = TtsAdvancedSettings()
    )

    private data class SecretState(
        val freshRssUsername: String = "",
        val freshRssApiPassword: String = "",
        val minifluxApiToken: String = "",
        val openRouterApiKey: String = ""
    )

    companion object {
        private const val PREFS_FILE = "hreader_prefs"
        private const val SECRETS_FILE = "hreader_secrets"
        private const val KEY_BACKEND_TYPE = "backend_type"
        private const val KEY_FRESHRSS_SERVER_URL = "freshrss_server_url"
        private const val KEY_FRESHRSS_USERNAME = "freshrss_username"
        private const val KEY_FRESHRSS_API_PASSWORD = "freshrss_api_password"
        private const val KEY_MINIFLUX_SERVER_URL = "miniflux_server_url"
        private const val KEY_MINIFLUX_API_TOKEN = "miniflux_api_token"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_BIONIC_READING_ENABLED = "bionic_reading_enabled"
        private const val KEY_SENTRY_REPORTING_ENABLED = "sentry_reporting_enabled"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_GEMMA_BACKEND = "gemma_backend"
        private const val KEY_GEMMA_DOWNLOAD_UNMETERED_ONLY = "gemma_download_unmetered_only"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_CACHE_OWNER = "cache_owner"
        private const val KEY_CACHE_CLEANUP_PENDING = "cache_cleanup_pending"
        private const val KEY_LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp"
        private const val KEY_SYNC_PERFORMANCE_RECORDS = "sync_performance_records"
        private const val KEY_CREDIBILITY_SCORE_ENABLED = "credibility_score_enabled"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_LANGUAGE_OVERRIDES = "tts_language_overrides"
        private const val KEY_TTS_THREADS = "tts_threads"
        private const val KEY_TTS_SILENCE_SCALE = "tts_silence_scale"
        private const val KEY_TTS_SUPERTONIC_SPEAKER = "tts_supertonic_speaker"
        private const val KEY_TTS_SUPERTONIC_STEPS = "tts_supertonic_steps"
        private const val KEY_TTS_KOKORO_SPEAKER = "tts_kokoro_speaker"
        private const val KEY_TTS_KITTEN_SPEAKER = "tts_kitten_speaker"
        private const val KEY_TTS_GOSIA_NOISE_SCALE = "tts_gosia_noise_scale"
        private const val KEY_TTS_GOSIA_DURATION_NOISE_SCALE = "tts_gosia_duration_noise_scale"
        private const val KEY_OFFLINE_BACKLOG_TARGET = "offline_backlog_target"
        private const val KEY_IMAGE_DOWNLOAD_ENABLED = "image_download_enabled"
        private const val KEY_IMAGE_CACHE_BUDGET_MB = "image_cache_budget_mb"
        private const val KEY_LAST_CHAINED_SYNC_TIMESTAMP = "last_chained_sync_timestamp"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_SYNC_UNMETERED_ONLY = "sync_unmetered_only"
        private const val KEY_SYNC_WHILE_ROAMING = "sync_while_roaming"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"

        private val backendTypeKey = stringPreferencesKey(KEY_BACKEND_TYPE)
        private val freshRssServerUrlKey = stringPreferencesKey(KEY_FRESHRSS_SERVER_URL)
        private val minifluxServerUrlKey = stringPreferencesKey(KEY_MINIFLUX_SERVER_URL)
        private val freshRssUsernameKey = stringPreferencesKey(KEY_FRESHRSS_USERNAME)
        private val freshRssApiPasswordKey = stringPreferencesKey(KEY_FRESHRSS_API_PASSWORD)
        private val minifluxApiTokenKey = stringPreferencesKey(KEY_MINIFLUX_API_TOKEN)
        private val openRouterApiKey = stringPreferencesKey(KEY_OPENROUTER_API_KEY)
        private val freshRssUsernameEncryptedKey = stringPreferencesKey("${KEY_FRESHRSS_USERNAME}_encrypted_v1")
        private val freshRssApiPasswordEncryptedKey = stringPreferencesKey("${KEY_FRESHRSS_API_PASSWORD}_encrypted_v1")
        private val minifluxApiTokenEncryptedKey = stringPreferencesKey("${KEY_MINIFLUX_API_TOKEN}_encrypted_v1")
        private val openRouterApiKeyEncryptedKey = stringPreferencesKey("${KEY_OPENROUTER_API_KEY}_encrypted_v1")
        private val secretMigrationStateKey = stringPreferencesKey("secret_migration_state")
        private val paywallBypassMethodKey = stringPreferencesKey(KEY_PAYWALL_BYPASS_METHOD)
        private val bionicReadingEnabledKey = booleanPreferencesKey(KEY_BIONIC_READING_ENABLED)
        private val sentryReportingEnabledKey = booleanPreferencesKey(KEY_SENTRY_REPORTING_ENABLED)
        private val aiModelIdKey = stringPreferencesKey(KEY_AI_MODEL)
        private val gemmaBackendKey = stringPreferencesKey(KEY_GEMMA_BACKEND)
        private val gemmaDownloadOnUnmeteredOnlyKey =
            booleanPreferencesKey(KEY_GEMMA_DOWNLOAD_UNMETERED_ONLY)
        private val lastSyncTimestampKey = longPreferencesKey(KEY_LAST_SYNC_TIMESTAMP)
        private val cacheOwnerKey = stringPreferencesKey(KEY_CACHE_OWNER)
        private val cacheCleanupPendingKey = booleanPreferencesKey(KEY_CACHE_CLEANUP_PENDING)
        private val lastFullSyncTimestampKey = longPreferencesKey(KEY_LAST_FULL_SYNC_TIMESTAMP)
        private val syncPerformanceRecordsKey = stringPreferencesKey(KEY_SYNC_PERFORMANCE_RECORDS)
        private val credibilityScoreEnabledKey = booleanPreferencesKey(KEY_CREDIBILITY_SCORE_ENABLED)
        private val ttsModelKey = stringPreferencesKey(KEY_TTS_MODEL)
        private val ttsSpeedKey = floatPreferencesKey(KEY_TTS_SPEED)
        private val ttsLanguageOverridesKey = stringSetPreferencesKey(KEY_TTS_LANGUAGE_OVERRIDES)
        private val ttsThreadsKey = intPreferencesKey(KEY_TTS_THREADS)
        private val ttsSilenceScaleKey = floatPreferencesKey(KEY_TTS_SILENCE_SCALE)
        private val ttsSupertonicSpeakerKey = intPreferencesKey(KEY_TTS_SUPERTONIC_SPEAKER)
        private val ttsSupertonicStepsKey = intPreferencesKey(KEY_TTS_SUPERTONIC_STEPS)
        private val ttsKokoroSpeakerKey = intPreferencesKey(KEY_TTS_KOKORO_SPEAKER)
        private val ttsKittenSpeakerKey = intPreferencesKey(KEY_TTS_KITTEN_SPEAKER)
        private val ttsGosiaNoiseScaleKey = floatPreferencesKey(KEY_TTS_GOSIA_NOISE_SCALE)
        private val ttsGosiaDurationNoiseScaleKey = floatPreferencesKey(KEY_TTS_GOSIA_DURATION_NOISE_SCALE)
        private val offlineBacklogTargetKey = intPreferencesKey(KEY_OFFLINE_BACKLOG_TARGET)
        private val imageDownloadEnabledKey = booleanPreferencesKey(KEY_IMAGE_DOWNLOAD_ENABLED)
        private val imageCacheBudgetMegabytesKey = intPreferencesKey(KEY_IMAGE_CACHE_BUDGET_MB)
        private val lastChainedSyncTimestampKey = longPreferencesKey(KEY_LAST_CHAINED_SYNC_TIMESTAMP)
        private val syncIntervalMinutesKey = intPreferencesKey(KEY_SYNC_INTERVAL_MINUTES)
        private val syncOnUnmeteredOnlyKey = booleanPreferencesKey(KEY_SYNC_UNMETERED_ONLY)
        private val syncWhileRoamingKey = booleanPreferencesKey(KEY_SYNC_WHILE_ROAMING)
        private val quietHoursEnabledKey = booleanPreferencesKey(KEY_QUIET_HOURS_ENABLED)
        private val quietHoursStartHourKey = intPreferencesKey(KEY_QUIET_HOURS_START)
        private val quietHoursEndHourKey = intPreferencesKey(KEY_QUIET_HOURS_END)

        private const val MAX_PERFORMANCE_RECORDS = 50
        private const val DEFAULT_OFFLINE_BACKLOG_TARGET = 0
        private const val DEFAULT_IMAGE_CACHE_BUDGET_MB = 500
        private const val MIN_SYNC_INTERVAL_MINUTES = 15
        private const val SECRET_MIGRATION_PREPARED = "prepared"
        private const val SECRET_MIGRATION_COMPLETE = "complete"

        private val secretDefinitions = listOf(
            SecretDefinition(freshRssUsernameKey, freshRssUsernameEncryptedKey),
            SecretDefinition(freshRssApiPasswordKey, freshRssApiPasswordEncryptedKey),
            SecretDefinition(minifluxApiTokenKey, minifluxApiTokenEncryptedKey),
            SecretDefinition(openRouterApiKey, openRouterApiKeyEncryptedKey)
        )
        private val legacySecretKeys = secretDefinitions.map { it.legacyKey }
        private val encryptedSecretKeys = secretDefinitions.map { it.encryptedKey }

        private data class SecretDefinition(
            val legacyKey: Preferences.Key<String>,
            val encryptedKey: Preferences.Key<String>
        )

        private data class WriteRequest(
            val transform: suspend MutablePreferences.() -> Unit,
            val completion: CompletableDeferred<Unit>? = null
        )

        private fun serverUrlKey(backendType: BackendType): Preferences.Key<String> = when (backendType) {
            BackendType.FRESHRSS -> freshRssServerUrlKey
            BackendType.MINIFLUX -> minifluxServerUrlKey
        }

        private fun backendSecretKey(backendType: BackendType): Preferences.Key<String> = when (backendType) {
            BackendType.FRESHRSS -> freshRssApiPasswordKey
            BackendType.MINIFLUX -> minifluxApiTokenKey
        }
    }
}
