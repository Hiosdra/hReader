package com.hiosdra.hreader.adapter.preferences

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.SharedPreferencesMigration
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
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides
import com.hiosdra.hreader.core.domain.model.BackendType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PreferencesManager(context: Context) : AppPreferences {
    private val applicationContext = context.applicationContext
    private val legacyPreferences = applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val legacySecretPreferences = applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val syncRecordsAdapter = moshi.adapter<List<SyncPerformanceRecord>>(
        Types.newParameterizedType(List::class.java, SyncPerformanceRecord::class.java)
    )

    private val preferencesDataStore by lazy {
        PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(
                    context = applicationContext,
                    sharedPreferencesName = PREFS_FILE,
                    keysToMigrate = NON_SECRET_KEYS
                )
            ),
            scope = scope,
            produceFile = { applicationContext.preferencesDataStoreFile(PREFS_FILE) }
        )
    }

    private val secretDataStore by lazy {
        PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(
                    context = applicationContext,
                    sharedPreferencesName = SECRETS_FILE,
                    keysToMigrate = SECRET_KEYS
                )
            ),
            scope = scope,
            produceFile = { applicationContext.preferencesDataStoreFile(SECRETS_FILE) }
        )
    }

    private val preferenceState: AtomicReference<PreferenceState>
    private val secretState: AtomicReference<SecretState>

    init {
        preferenceState = AtomicReference(PreferenceState())
        secretState = AtomicReference(SecretState())

        runBlocking(Dispatchers.IO) {
            migrateLegacySecrets()
            preferenceState.set(readLegacyPreferences())
            secretState.set(readLegacySecrets())
            preferenceState.set(preferencesDataStore.data.first().toPreferenceState())
            secretState.set(secretDataStore.data.first().toSecretState())
        }
    }

    override fun getBackendType(): BackendType = preferenceState.get().backendType

    override fun setBackendType(backendType: BackendType) {
        preferenceState.updateAndGet { it.copy(backendType = backendType) }
        writePreferences { this[backendTypeKey] = backendType.name }
    }

    override fun getServerUrl(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> preferenceState.get().freshRssServerUrl
        BackendType.MINIFLUX -> preferenceState.get().minifluxServerUrl
    }

    override fun setServerUrl(backendType: BackendType, url: String) {
        preferenceState.updateAndGet { state ->
            when (backendType) {
                BackendType.FRESHRSS -> state.copy(freshRssServerUrl = url)
                BackendType.MINIFLUX -> state.copy(minifluxServerUrl = url)
            }
        }
        writePreferences { this[serverUrlKey(backendType)] = url }
    }

    override fun getBackendSecret(backendType: BackendType): String = when (backendType) {
        BackendType.FRESHRSS -> secretState.get().freshRssApiPassword
        BackendType.MINIFLUX -> secretState.get().minifluxApiToken
    }

    override fun setBackendSecret(backendType: BackendType, secret: String) {
        secretState.updateAndGet { state ->
            when (backendType) {
                BackendType.FRESHRSS -> state.copy(freshRssApiPassword = secret)
                BackendType.MINIFLUX -> state.copy(minifluxApiToken = secret)
            }
        }
        writeSecrets { this[backendSecretKey(backendType)] = secret }
    }

    override fun getFreshRssUsername(): String = secretState.get().freshRssUsername

    override fun setFreshRssUsername(username: String) {
        secretState.updateAndGet { it.copy(freshRssUsername = username) }
        writeSecrets { this[freshRssUsernameKey] = username }
    }

    override fun hasBackendCredentials(): Boolean {
        val backendType = getBackendType()
        if (getServerUrl(backendType).isBlank() || getBackendSecret(backendType).isBlank()) return false
        return !backendType.requiresUsername || getFreshRssUsername().isNotBlank()
    }

    override fun getOpenRouterApiKey(): String = secretState.get().openRouterApiKey

    override fun setOpenRouterApiKey(apiKey: String) {
        secretState.updateAndGet { it.copy(openRouterApiKey = apiKey) }
        writeSecrets { this[openRouterApiKey] = apiKey }
    }

    override fun getPaywallBypassMethod(): PaywallBypassMethod = preferenceState.get().paywallBypassMethod

    override fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        preferenceState.updateAndGet { it.copy(paywallBypassMethod = method) }
        writePreferences { this[paywallBypassMethodKey] = method.name }
    }

    override fun getBionicReadingEnabled(): Boolean = preferenceState.get().bionicReadingEnabled

    override fun setBionicReadingEnabled(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(bionicReadingEnabled = enabled) }
        writePreferences { this[bionicReadingEnabledKey] = enabled }
    }

    override fun getSentryReportingEnabled(): Boolean = preferenceState.get().sentryReportingEnabled

    override fun setSentryReportingEnabled(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(sentryReportingEnabled = enabled) }
        writePreferences { this[sentryReportingEnabledKey] = enabled }
    }

    override fun observeBionicReadingEnabled(): Flow<Boolean> = preferencesDataStore.data
        .map { it[bionicReadingEnabledKey] ?: false }
        .distinctUntilChanged()

    override fun getAiModelId(): String = preferenceState.get().aiModelId

    override fun setAiModelId(modelId: String) {
        preferenceState.updateAndGet { it.copy(aiModelId = modelId) }
        writePreferences { this[aiModelIdKey] = modelId }
    }

    override fun getLastSyncTimestamp(): Long = preferenceState.get().lastSyncTimestamp

    override fun setLastSyncTimestamp(timestamp: Long) {
        preferenceState.updateAndGet { it.copy(lastSyncTimestamp = timestamp) }
        writePreferences { this[lastSyncTimestampKey] = timestamp }
    }

    override fun getCacheOwnerKey(): String = preferenceState.get().cacheOwnerKey

    override fun setCacheOwnerKey(ownerKey: String) {
        preferenceState.updateAndGet { it.copy(cacheOwnerKey = ownerKey) }
        writePreferences { this[cacheOwnerKey] = ownerKey }
    }

    override fun observeLastSyncTimestamp(): Flow<Long> = preferencesDataStore.data
        .map { it[lastSyncTimestampKey] ?: 0L }
        .distinctUntilChanged()

    override fun getLastFullSyncTimestamp(): Long = preferenceState.get().lastFullSyncTimestamp

    override fun setLastFullSyncTimestamp(timestamp: Long) {
        preferenceState.updateAndGet { it.copy(lastFullSyncTimestamp = timestamp) }
        writePreferences { this[lastFullSyncTimestampKey] = timestamp }
    }

    override fun getSyncPerformanceRecords(): List<SyncPerformanceRecord> =
        preferenceState.get().syncPerformanceRecords

    override fun addSyncPerformanceRecord(record: SyncPerformanceRecord) {
        preferenceState.updateAndGet { state ->
            state.copy(syncPerformanceRecords = (listOf(record) + state.syncPerformanceRecords)
                .take(MAX_PERFORMANCE_RECORDS))
        }
        writePreferences {
            val records = (listOf(record) + decodeSyncPerformanceRecords(this[syncPerformanceRecordsKey]))
                .take(MAX_PERFORMANCE_RECORDS)
            this[syncPerformanceRecordsKey] = syncRecordsAdapter.toJson(records)
        }
    }

    override fun clearSyncPerformanceRecords() {
        preferenceState.updateAndGet { it.copy(syncPerformanceRecords = emptyList()) }
        writePreferences { remove(syncPerformanceRecordsKey) }
    }

    override fun getOfflineBacklogTarget(): Int = preferenceState.get().offlineBacklogTarget

    override fun setOfflineBacklogTarget(target: Int) {
        val normalizedTarget = target.coerceAtLeast(0)
        preferenceState.updateAndGet { it.copy(offlineBacklogTarget = normalizedTarget) }
        writePreferences { this[offlineBacklogTargetKey] = normalizedTarget }
    }

    override fun getImageDownloadEnabled(): Boolean = preferenceState.get().imageDownloadEnabled

    override fun setImageDownloadEnabled(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(imageDownloadEnabled = enabled) }
        writePreferences { this[imageDownloadEnabledKey] = enabled }
    }

    override fun getImageCacheBudgetMegabytes(): Int = preferenceState.get().imageCacheBudgetMegabytes

    override fun setImageCacheBudgetMegabytes(megabytes: Int) {
        val normalizedMegabytes = megabytes.coerceAtLeast(0)
        preferenceState.updateAndGet { it.copy(imageCacheBudgetMegabytes = normalizedMegabytes) }
        writePreferences { this[imageCacheBudgetMegabytesKey] = normalizedMegabytes }
    }

    override fun getSyncIntervalMinutes(): Int = preferenceState.get().syncIntervalMinutes

    override fun setSyncIntervalMinutes(minutes: Int) {
        val normalizedMinutes = minutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)
        preferenceState.updateAndGet { it.copy(syncIntervalMinutes = normalizedMinutes) }
        writePreferences { this[syncIntervalMinutesKey] = normalizedMinutes }
    }

    override fun getSyncOnUnmeteredOnly(): Boolean = preferenceState.get().syncOnUnmeteredOnly

    override fun setSyncOnUnmeteredOnly(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(syncOnUnmeteredOnly = enabled) }
        writePreferences { this[syncOnUnmeteredOnlyKey] = enabled }
    }

    override fun getSyncWhileRoaming(): Boolean = preferenceState.get().syncWhileRoaming

    override fun setSyncWhileRoaming(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(syncWhileRoaming = enabled) }
        writePreferences { this[syncWhileRoamingKey] = enabled }
    }

    override fun getQuietHoursEnabled(): Boolean = preferenceState.get().quietHoursEnabled

    override fun setQuietHoursEnabled(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(quietHoursEnabled = enabled) }
        writePreferences { this[quietHoursEnabledKey] = enabled }
    }

    override fun getQuietHoursStartHour(): Int = preferenceState.get().quietHoursStartHour

    override fun getQuietHoursEndHour(): Int = preferenceState.get().quietHoursEndHour

    override fun setQuietHours(startHour: Int, endHour: Int) {
        val normalizedStartHour = startHour.coerceIn(0, 23)
        val normalizedEndHour = endHour.coerceIn(0, 23)
        preferenceState.updateAndGet {
            it.copy(
                quietHoursStartHour = normalizedStartHour,
                quietHoursEndHour = normalizedEndHour
            )
        }
        writePreferences {
            this[quietHoursStartHourKey] = normalizedStartHour
            this[quietHoursEndHourKey] = normalizedEndHour
        }
    }

    override fun getLastChainedSyncTimestamp(): Long = preferenceState.get().lastChainedSyncTimestamp

    override fun setLastChainedSyncTimestamp(timestamp: Long) {
        preferenceState.updateAndGet { it.copy(lastChainedSyncTimestamp = timestamp) }
        writePreferences { this[lastChainedSyncTimestampKey] = timestamp }
    }

    override fun getCredibilityScoreEnabled(): Boolean = preferenceState.get().credibilityScoreEnabled

    override fun setCredibilityScoreEnabled(enabled: Boolean) {
        preferenceState.updateAndGet { it.copy(credibilityScoreEnabled = enabled) }
        writePreferences { this[credibilityScoreEnabledKey] = enabled }
    }

    override fun getTtsModel(): TtsModel = preferenceState.get().ttsModel

    override fun setTtsModel(model: TtsModel) {
        preferenceState.updateAndGet { it.copy(ttsModel = model) }
        writePreferences { this[ttsModelKey] = model.name }
    }

    override fun getTtsModelForLanguage(language: String): TtsModel =
        preferenceState.get().ttsLanguageOverrides[language] ?: getTtsModel()

    override fun getTtsLanguageOverrides(): Map<String, TtsModel> =
        preferenceState.get().ttsLanguageOverrides

    override fun setTtsLanguageOverride(language: String, model: TtsModel?) {
        preferenceState.updateAndGet { state ->
            state.copy(ttsLanguageOverrides = state.ttsLanguageOverrides.updated(language, model))
        }
        writePreferences {
            val overrides = parseTtsLanguageOverrides(this[ttsLanguageOverridesKey].orEmpty())
                .updated(language, model)
            this[ttsLanguageOverridesKey] = overrides.toPreferenceSet()
        }
    }

    override fun getTtsSpeed(): Float = preferenceState.get().ttsSpeed

    override fun setTtsSpeed(speed: Float) {
        val normalizedSpeed = speed.coerceIn(0.7f, 1.4f)
        preferenceState.updateAndGet { it.copy(ttsSpeed = normalizedSpeed) }
        writePreferences { this[ttsSpeedKey] = normalizedSpeed }
    }

    override fun getTtsAdvancedSettings(): TtsAdvancedSettings = preferenceState.get().ttsAdvancedSettings

    override fun setTtsAdvancedSettings(settings: TtsAdvancedSettings) {
        val normalizedSettings = settings.normalized()
        preferenceState.updateAndGet { it.copy(ttsAdvancedSettings = normalizedSettings) }
        writePreferences {
            this[ttsThreadsKey] = normalizedSettings.numThreads
            this[ttsSilenceScaleKey] = normalizedSettings.silenceScale
            this[ttsSupertonicSpeakerKey] = normalizedSettings.supertonicSpeaker
            this[ttsSupertonicStepsKey] = normalizedSettings.supertonicSteps
            this[ttsKokoroSpeakerKey] = normalizedSettings.kokoroSpeaker
            this[ttsGosiaNoiseScaleKey] = normalizedSettings.gosiaNoiseScale
            this[ttsGosiaDurationNoiseScaleKey] = normalizedSettings.gosiaDurationNoiseScale
        }
    }

    private fun writePreferences(transform: suspend MutablePreferences.() -> Unit) {
        scope.launch { preferencesDataStore.edit(transform) }
    }

    private fun writeSecrets(transform: suspend MutablePreferences.() -> Unit) {
        scope.launch { secretDataStore.edit(transform) }
    }

    private fun migrateLegacySecrets() {
        val keysToMigrate = SECRET_KEYS.filter(legacyPreferences::contains)
        if (keysToMigrate.isEmpty()) return

        val editor = legacySecretPreferences.edit()
        keysToMigrate.forEach { key ->
            val value = legacyPreferences.getString(key, null)
            if (!value.isNullOrBlank() && legacySecretPreferences.getString(key, null).isNullOrBlank()) {
                editor.putString(key, value)
            }
        }
        if (!editor.commit()) return

        legacyPreferences.edit {
            keysToMigrate.forEach(::remove)
            remove(KEY_SECRETS_MIGRATED)
        }
    }

    private fun readLegacyPreferences() = PreferenceState(
        backendType = BackendType.fromName(legacyPreferences.getString(KEY_BACKEND_TYPE, null)),
        freshRssServerUrl = legacyPreferences.getString(KEY_FRESHRSS_SERVER_URL, "").orEmpty(),
        minifluxServerUrl = legacyPreferences.getString(KEY_MINIFLUX_SERVER_URL, "").orEmpty(),
        paywallBypassMethod = legacyPreferences.getString(
            KEY_PAYWALL_BYPASS_METHOD,
            PaywallBypassMethod.SMRY_AI.name
        ).toPaywallBypassMethod(),
        bionicReadingEnabled = legacyPreferences.getBoolean(KEY_BIONIC_READING_ENABLED, false),
        sentryReportingEnabled = legacyPreferences.getBoolean(KEY_SENTRY_REPORTING_ENABLED, true),
        aiModelId = legacyPreferences.getString(KEY_AI_MODEL, AiModel.DEFAULT_ID) ?: AiModel.DEFAULT_ID,
        lastSyncTimestamp = legacyPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L),
        cacheOwnerKey = legacyPreferences.getString(KEY_CACHE_OWNER, "").orEmpty(),
        lastFullSyncTimestamp = legacyPreferences.getLong(KEY_LAST_FULL_SYNC_TIMESTAMP, 0L),
        syncPerformanceRecords = decodeSyncPerformanceRecords(
            legacyPreferences.getString(KEY_SYNC_PERFORMANCE_RECORDS, null)
        ),
        offlineBacklogTarget = legacyPreferences
            .getInt(KEY_OFFLINE_BACKLOG_TARGET, DEFAULT_OFFLINE_BACKLOG_TARGET)
            .coerceAtLeast(0),
        imageDownloadEnabled = legacyPreferences.getBoolean(KEY_IMAGE_DOWNLOAD_ENABLED, true),
        imageCacheBudgetMegabytes = legacyPreferences
            .getInt(KEY_IMAGE_CACHE_BUDGET_MB, DEFAULT_IMAGE_CACHE_BUDGET_MB)
            .coerceAtLeast(0),
        lastChainedSyncTimestamp = legacyPreferences.getLong(KEY_LAST_CHAINED_SYNC_TIMESTAMP, 0L),
        syncIntervalMinutes = legacyPreferences
            .getInt(KEY_SYNC_INTERVAL_MINUTES, SyncDefaults.INTERVAL_MINUTES)
            .coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES),
        syncOnUnmeteredOnly = legacyPreferences.getBoolean(KEY_SYNC_UNMETERED_ONLY, false),
        syncWhileRoaming = legacyPreferences.getBoolean(KEY_SYNC_WHILE_ROAMING, true),
        quietHoursEnabled = legacyPreferences.getBoolean(KEY_QUIET_HOURS_ENABLED, false),
        quietHoursStartHour = legacyPreferences
            .getInt(KEY_QUIET_HOURS_START, SyncDefaults.QUIET_HOURS_START)
            .coerceIn(0, 23),
        quietHoursEndHour = legacyPreferences
            .getInt(KEY_QUIET_HOURS_END, SyncDefaults.QUIET_HOURS_END)
            .coerceIn(0, 23),
        credibilityScoreEnabled = legacyPreferences.getBoolean(KEY_CREDIBILITY_SCORE_ENABLED, false),
        ttsModel = TtsModel.fromName(legacyPreferences.getString(KEY_TTS_MODEL, null)),
        ttsLanguageOverrides = parseTtsLanguageOverrides(
            legacyPreferences.getStringSet(KEY_TTS_LANGUAGE_OVERRIDES, emptySet()).orEmpty()
        ),
        ttsSpeed = legacyPreferences.getFloat(KEY_TTS_SPEED, 1f).coerceIn(0.7f, 1.4f),
        ttsAdvancedSettings = readLegacyTtsAdvancedSettings()
    )

    private fun readLegacySecrets() = SecretState(
        freshRssUsername = legacySecretPreferences.getString(KEY_FRESHRSS_USERNAME, "").orEmpty(),
        freshRssApiPassword = legacySecretPreferences.getString(KEY_FRESHRSS_API_PASSWORD, "").orEmpty(),
        minifluxApiToken = legacySecretPreferences.getString(KEY_MINIFLUX_API_TOKEN, "").orEmpty(),
        openRouterApiKey = legacySecretPreferences.getString(KEY_OPENROUTER_API_KEY, "").orEmpty()
    )

    private fun readLegacyTtsAdvancedSettings() = TtsAdvancedSettings(
        numThreads = legacyPreferences.getInt(KEY_TTS_THREADS, 4).coerceIn(1, 4),
        silenceScale = legacyPreferences.getFloat(KEY_TTS_SILENCE_SCALE, 0.2f).coerceIn(0f, 1f),
        supertonicSpeaker = legacyPreferences.getInt(KEY_TTS_SUPERTONIC_SPEAKER, 0).coerceIn(0, 9),
        supertonicSteps = legacyPreferences.getInt(KEY_TTS_SUPERTONIC_STEPS, 8).coerceIn(4, 12),
        kokoroSpeaker = legacyPreferences.getInt(KEY_TTS_KOKORO_SPEAKER, 0).coerceIn(0, 102),
        gosiaNoiseScale = legacyPreferences.getFloat(KEY_TTS_GOSIA_NOISE_SCALE, 0.667f).coerceIn(0f, 1f),
        gosiaDurationNoiseScale = legacyPreferences
            .getFloat(KEY_TTS_GOSIA_DURATION_NOISE_SCALE, 0.8f)
            .coerceIn(0f, 1f)
    )

    private fun Preferences.toPreferenceState() = PreferenceState(
        backendType = BackendType.fromName(this[backendTypeKey]),
        freshRssServerUrl = this[freshRssServerUrlKey].orEmpty(),
        minifluxServerUrl = this[minifluxServerUrlKey].orEmpty(),
        paywallBypassMethod = this[paywallBypassMethodKey].toPaywallBypassMethod(),
        bionicReadingEnabled = this[bionicReadingEnabledKey] ?: false,
        sentryReportingEnabled = this[sentryReportingEnabledKey] ?: true,
        aiModelId = this[aiModelIdKey] ?: AiModel.DEFAULT_ID,
        lastSyncTimestamp = this[lastSyncTimestampKey] ?: 0L,
        cacheOwnerKey = this[cacheOwnerKey].orEmpty(),
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
            gosiaNoiseScale = (this[ttsGosiaNoiseScaleKey] ?: 0.667f).coerceIn(0f, 1f),
            gosiaDurationNoiseScale = (this[ttsGosiaDurationNoiseScaleKey] ?: 0.8f).coerceIn(0f, 1f)
        )
    )

    private fun Preferences.toSecretState() = SecretState(
        freshRssUsername = this[freshRssUsernameKey].orEmpty(),
        freshRssApiPassword = this[freshRssApiPasswordKey].orEmpty(),
        minifluxApiToken = this[minifluxApiTokenKey].orEmpty(),
        openRouterApiKey = this[openRouterApiKey].orEmpty()
    )

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
        gosiaNoiseScale = gosiaNoiseScale.coerceIn(0f, 1f),
        gosiaDurationNoiseScale = gosiaDurationNoiseScale.coerceIn(0f, 1f)
    )

    private data class PreferenceState(
        val backendType: BackendType = BackendType.FRESHRSS,
        val freshRssServerUrl: String = "",
        val minifluxServerUrl: String = "",
        val paywallBypassMethod: PaywallBypassMethod = PaywallBypassMethod.SMRY_AI,
        val bionicReadingEnabled: Boolean = false,
        val sentryReportingEnabled: Boolean = true,
        val aiModelId: String = AiModel.DEFAULT_ID,
        val lastSyncTimestamp: Long = 0L,
        val cacheOwnerKey: String = "",
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
        private const val KEY_SECRETS_MIGRATED = "secrets_migrated"
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_BIONIC_READING_ENABLED = "bionic_reading_enabled"
        private const val KEY_SENTRY_REPORTING_ENABLED = "sentry_reporting_enabled"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_CACHE_OWNER = "cache_owner"
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

        private val SECRET_KEYS = setOf(
            KEY_FRESHRSS_USERNAME,
            KEY_FRESHRSS_API_PASSWORD,
            KEY_MINIFLUX_API_TOKEN,
            KEY_OPENROUTER_API_KEY
        )

        private val NON_SECRET_KEYS = setOf(
            KEY_BACKEND_TYPE,
            KEY_FRESHRSS_SERVER_URL,
            KEY_MINIFLUX_SERVER_URL,
            KEY_SECRETS_MIGRATED,
            KEY_PAYWALL_BYPASS_METHOD,
            KEY_BIONIC_READING_ENABLED,
            KEY_SENTRY_REPORTING_ENABLED,
            KEY_AI_MODEL,
            KEY_LAST_SYNC_TIMESTAMP,
            KEY_CACHE_OWNER,
            KEY_LAST_FULL_SYNC_TIMESTAMP,
            KEY_SYNC_PERFORMANCE_RECORDS,
            KEY_CREDIBILITY_SCORE_ENABLED,
            KEY_TTS_MODEL,
            KEY_TTS_SPEED,
            KEY_TTS_LANGUAGE_OVERRIDES,
            KEY_TTS_THREADS,
            KEY_TTS_SILENCE_SCALE,
            KEY_TTS_SUPERTONIC_SPEAKER,
            KEY_TTS_SUPERTONIC_STEPS,
            KEY_TTS_KOKORO_SPEAKER,
            KEY_TTS_GOSIA_NOISE_SCALE,
            KEY_TTS_GOSIA_DURATION_NOISE_SCALE,
            KEY_OFFLINE_BACKLOG_TARGET,
            KEY_IMAGE_DOWNLOAD_ENABLED,
            KEY_IMAGE_CACHE_BUDGET_MB,
            KEY_LAST_CHAINED_SYNC_TIMESTAMP,
            KEY_SYNC_INTERVAL_MINUTES,
            KEY_SYNC_UNMETERED_ONLY,
            KEY_SYNC_WHILE_ROAMING,
            KEY_QUIET_HOURS_ENABLED,
            KEY_QUIET_HOURS_START,
            KEY_QUIET_HOURS_END
        )

        private val backendTypeKey = stringPreferencesKey(KEY_BACKEND_TYPE)
        private val freshRssServerUrlKey = stringPreferencesKey(KEY_FRESHRSS_SERVER_URL)
        private val minifluxServerUrlKey = stringPreferencesKey(KEY_MINIFLUX_SERVER_URL)
        private val freshRssUsernameKey = stringPreferencesKey(KEY_FRESHRSS_USERNAME)
        private val freshRssApiPasswordKey = stringPreferencesKey(KEY_FRESHRSS_API_PASSWORD)
        private val minifluxApiTokenKey = stringPreferencesKey(KEY_MINIFLUX_API_TOKEN)
        private val openRouterApiKey = stringPreferencesKey(KEY_OPENROUTER_API_KEY)
        private val paywallBypassMethodKey = stringPreferencesKey(KEY_PAYWALL_BYPASS_METHOD)
        private val bionicReadingEnabledKey = booleanPreferencesKey(KEY_BIONIC_READING_ENABLED)
        private val sentryReportingEnabledKey = booleanPreferencesKey(KEY_SENTRY_REPORTING_ENABLED)
        private val aiModelIdKey = stringPreferencesKey(KEY_AI_MODEL)
        private val lastSyncTimestampKey = longPreferencesKey(KEY_LAST_SYNC_TIMESTAMP)
        private val cacheOwnerKey = stringPreferencesKey(KEY_CACHE_OWNER)
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
