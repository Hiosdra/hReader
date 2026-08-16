package com.hiosdra.hreader.adapter.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.hiosdra.hreader.core.application.observability.SyncPerformanceRecord
import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.AppPreferences
import com.hiosdra.hreader.core.application.sync.SyncDefaults
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides
import com.hiosdra.hreader.core.domain.model.BackendType
import com.hiosdra.hreader.core.application.ai.AiModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Server credentials, the OpenRouter key and everything else the app remembers.
 *
 * Secrets live in their own file so `backup_rules.xml` can leave them out of cloud and adb
 * backups. Everything else is a preference worth restoring onto a new device; a server token is
 * not, and used to travel with it.
 */
class PreferencesManager(context: Context) : AppPreferences {
    private val sharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val secretPreferences = context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val syncRecordsAdapter = moshi.adapter<List<SyncPerformanceRecord>>(
        Types.newParameterizedType(List::class.java, SyncPerformanceRecord::class.java)
    )

    init {
        migrateSecretsOutOfBackedUpFile()
    }

    override fun getBackendType(): BackendType = BackendType.fromName(sharedPreferences.getString(KEY_BACKEND_TYPE, null))

    override fun setBackendType(backendType: BackendType) {
        sharedPreferences.edit { putString(KEY_BACKEND_TYPE, backendType.name) }
    }

    override fun getServerUrl(backendType: BackendType): String =
        sharedPreferences.getString(serverUrlKeyFor(backendType), "").orEmpty()

    override fun setServerUrl(backendType: BackendType, url: String) {
        sharedPreferences.edit { putString(serverUrlKeyFor(backendType), url) }
    }

    override fun getBackendSecret(backendType: BackendType): String =
        secretPreferences.getString(secretKeyFor(backendType), "").orEmpty()

    override fun setBackendSecret(backendType: BackendType, secret: String) {
        secretPreferences.edit { putString(secretKeyFor(backendType), secret) }
    }

    override fun getFreshRssUsername(): String = secretPreferences.getString(KEY_FRESHRSS_USERNAME, "").orEmpty()

    override fun setFreshRssUsername(username: String) {
        secretPreferences.edit { putString(KEY_FRESHRSS_USERNAME, username) }
    }

    override fun hasBackendCredentials(): Boolean {
        val backendType = getBackendType()
        if (getServerUrl(backendType).isBlank() || getBackendSecret(backendType).isBlank()) return false
        return !backendType.requiresUsername || getFreshRssUsername().isNotBlank()
    }

    private fun serverUrlKeyFor(backendType: BackendType) = when (backendType) {
        BackendType.FRESHRSS -> KEY_FRESHRSS_SERVER_URL
        BackendType.MINIFLUX -> KEY_MINIFLUX_SERVER_URL
    }

    private fun secretKeyFor(backendType: BackendType) = when (backendType) {
        BackendType.FRESHRSS -> KEY_FRESHRSS_API_PASSWORD
        BackendType.MINIFLUX -> KEY_MINIFLUX_API_TOKEN
    }

    override fun getOpenRouterApiKey(): String = secretPreferences.getString(KEY_OPENROUTER_API_KEY, "").orEmpty()

    override fun setOpenRouterApiKey(apiKey: String) {
        secretPreferences.edit { putString(KEY_OPENROUTER_API_KEY, apiKey) }
    }

    /**
     * Credentials written before they had a file of their own. Moving them clears them from the
     * backed-up file, which is the whole point of the move.
     *
     * The copy is committed before the original is removed, and the removal only happens if the
     * copy landed. Two `apply()` calls against two files complete in no particular order, so a
     * process death between them used to leave the secrets deleted from one file, never written to
     * the other, and the migration marked done — the server password gone for good.
     */
    private fun migrateSecretsOutOfBackedUpFile() {
        if (sharedPreferences.getBoolean(KEY_SECRETS_MIGRATED, false)) return

        val secretEditor = secretPreferences.edit()
        val legacyKeys = SECRET_KEYS.filter { sharedPreferences.contains(it) }
        legacyKeys.forEach { key ->
            val value = sharedPreferences.getString(key, null)
            if (!value.isNullOrBlank() && secretPreferences.getString(key, null).isNullOrBlank()) {
                secretEditor.putString(key, value)
            }
        }
        if (!secretEditor.commit()) return

        sharedPreferences.edit {
            legacyKeys.forEach(::remove)
            putBoolean(KEY_SECRETS_MIGRATED, true)
        }
    }

    override fun getPaywallBypassMethod(): PaywallBypassMethod {
        val savedMethod = sharedPreferences.getString(KEY_PAYWALL_BYPASS_METHOD, PaywallBypassMethod.SMRY_AI.name)
        return PaywallBypassMethod.entries.find { it.name == savedMethod } ?: PaywallBypassMethod.SMRY_AI
    }

    override fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        sharedPreferences.edit { putString(KEY_PAYWALL_BYPASS_METHOD, method.name) }
    }

    override fun getBionicReadingEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_BIONIC_READING_ENABLED, false)

    override fun setBionicReadingEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_BIONIC_READING_ENABLED, enabled) }
    }

    override fun getSentryReportingEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_SENTRY_REPORTING_ENABLED, true)

    override fun setSentryReportingEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SENTRY_REPORTING_ENABLED, enabled) }
    }

    /**
     * Reading settings are watched rather than read once: an article already open used to keep the
     * rendering it was composed with until it was navigated away from and back.
     */
    override fun observeBionicReadingEnabled(): Flow<Boolean> = observeBoolean(KEY_BIONIC_READING_ENABLED, false)

    override fun getAiModelId(): String =
        sharedPreferences.getString(KEY_AI_MODEL, AiModel.DEFAULT_ID) ?: AiModel.DEFAULT_ID

    override fun setAiModelId(modelId: String) {
        sharedPreferences.edit { putString(KEY_AI_MODEL, modelId) }
    }

    override fun getLastSyncTimestamp(): Long = sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    override fun setLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getCacheOwnerKey(): String = sharedPreferences.getString(KEY_CACHE_OWNER, "").orEmpty()

    override fun setCacheOwnerKey(ownerKey: String) {
        sharedPreferences.edit { putString(KEY_CACHE_OWNER, ownerKey) }
    }

    override fun observeLastSyncTimestamp(): Flow<Long> = callbackFlow {
        trySend(getLastSyncTimestamp())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == KEY_LAST_SYNC_TIMESTAMP) trySend(getLastSyncTimestamp())
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun getLastFullSyncTimestamp(): Long = sharedPreferences.getLong(KEY_LAST_FULL_SYNC_TIMESTAMP, 0L)

    override fun setLastFullSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_FULL_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getSyncPerformanceRecords(): List<SyncPerformanceRecord> {
        val json = sharedPreferences.getString(KEY_SYNC_PERFORMANCE_RECORDS, null) ?: return emptyList()
        return try {
            syncRecordsAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun addSyncPerformanceRecord(record: SyncPerformanceRecord) {
        val currentRecords = getSyncPerformanceRecords().toMutableList()
        currentRecords.add(0, record)

        if (currentRecords.size > MAX_PERFORMANCE_RECORDS) {
            currentRecords.subList(MAX_PERFORMANCE_RECORDS, currentRecords.size).clear()
        }

        sharedPreferences.edit {
            putString(KEY_SYNC_PERFORMANCE_RECORDS, syncRecordsAdapter.toJson(currentRecords))
        }
    }

    override fun clearSyncPerformanceRecords() {
        sharedPreferences.edit { remove(KEY_SYNC_PERFORMANCE_RECORDS) }
    }

    /**
     * How many articles to keep readable offline. Above what the backend still reports as unread,
     * the sync tops the cache up with recent entries regardless of their read state. Zero keeps the
     * old behaviour of caching unread articles only.
     */
    override fun getOfflineBacklogTarget(): Int =
        sharedPreferences.getInt(KEY_OFFLINE_BACKLOG_TARGET, DEFAULT_OFFLINE_BACKLOG_TARGET)

    override fun setOfflineBacklogTarget(target: Int) {
        sharedPreferences.edit { putInt(KEY_OFFLINE_BACKLOG_TARGET, target.coerceAtLeast(0)) }
    }

    override fun getImageDownloadEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_IMAGE_DOWNLOAD_ENABLED, true)

    override fun setImageDownloadEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_IMAGE_DOWNLOAD_ENABLED, enabled) }
    }

    /** Ceiling on the downloaded-image directory. Oldest images are evicted to stay under it. */
    override fun getImageCacheBudgetMegabytes(): Int =
        sharedPreferences.getInt(KEY_IMAGE_CACHE_BUDGET_MB, DEFAULT_IMAGE_CACHE_BUDGET_MB)

    override fun setImageCacheBudgetMegabytes(megabytes: Int) {
        sharedPreferences.edit { putInt(KEY_IMAGE_CACHE_BUDGET_MB, megabytes.coerceAtLeast(0)) }
    }

    /** How often the background sync runs. WorkManager clamps anything below its own floor. */
    override fun getSyncIntervalMinutes(): Int =
        sharedPreferences.getInt(KEY_SYNC_INTERVAL_MINUTES, SyncDefaults.INTERVAL_MINUTES)

    override fun setSyncIntervalMinutes(minutes: Int) {
        sharedPreferences.edit {
            putInt(KEY_SYNC_INTERVAL_MINUTES, minutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES))
        }
    }

    /**
     * Article bodies and images are the bulk of what a sync moves. Left on a metered connection a
     * backlog of a thousand articles is a real bill, so the download can be held back for Wi-Fi.
     */
    override fun getSyncOnUnmeteredOnly(): Boolean = sharedPreferences.getBoolean(KEY_SYNC_UNMETERED_ONLY, false)

    override fun setSyncOnUnmeteredOnly(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SYNC_UNMETERED_ONLY, enabled) }
    }

    /** Whether background syncing may run while the device is roaming. */
    override fun getSyncWhileRoaming(): Boolean = sharedPreferences.getBoolean(KEY_SYNC_WHILE_ROAMING, true)

    override fun setSyncWhileRoaming(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SYNC_WHILE_ROAMING, enabled) }
    }

    override fun getQuietHoursEnabled(): Boolean = sharedPreferences.getBoolean(KEY_QUIET_HOURS_ENABLED, false)

    override fun setQuietHoursEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_QUIET_HOURS_ENABLED, enabled) }
    }

    override fun getQuietHoursStartHour(): Int =
        sharedPreferences.getInt(KEY_QUIET_HOURS_START, SyncDefaults.QUIET_HOURS_START)

    override fun getQuietHoursEndHour(): Int =
        sharedPreferences.getInt(KEY_QUIET_HOURS_END, SyncDefaults.QUIET_HOURS_END)

    override fun setQuietHours(startHour: Int, endHour: Int) {
        sharedPreferences.edit {
            putInt(KEY_QUIET_HOURS_START, startHour.coerceIn(0, 23))
            putInt(KEY_QUIET_HOURS_END, endHour.coerceIn(0, 23))
        }
    }

    /** When the app last enqueued a background sync chain, so the throttle survives process death. */
    override fun getLastChainedSyncTimestamp(): Long =
        sharedPreferences.getLong(KEY_LAST_CHAINED_SYNC_TIMESTAMP, 0L)

    override fun setLastChainedSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit { putLong(KEY_LAST_CHAINED_SYNC_TIMESTAMP, timestamp) }
    }

    override fun getCredibilityScoreEnabled(): Boolean =
        sharedPreferences.getBoolean(KEY_CREDIBILITY_SCORE_ENABLED, false)

    override fun setCredibilityScoreEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_CREDIBILITY_SCORE_ENABLED, enabled) }
    }

    override fun getTtsModel(): TtsModel = TtsModel.fromName(sharedPreferences.getString(KEY_TTS_MODEL, null))

    override fun setTtsModel(model: TtsModel) {
        sharedPreferences.edit { putString(KEY_TTS_MODEL, model.name) }
    }

    override fun getTtsModelForLanguage(language: String): TtsModel =
        getTtsLanguageOverrides()[language] ?: getTtsModel()

    override fun getTtsLanguageOverrides(): Map<String, TtsModel> =
        parseTtsLanguageOverrides(
            sharedPreferences.getStringSet(KEY_TTS_LANGUAGE_OVERRIDES, emptySet()).orEmpty()
        )

    override fun setTtsLanguageOverride(language: String, model: TtsModel?) {
        val updated = getTtsLanguageOverrides().toMutableMap()
        if (model == null) updated.remove(language) else updated[language] = model
        sharedPreferences.edit {
            putStringSet(KEY_TTS_LANGUAGE_OVERRIDES, updated.map { "${it.key}=${it.value.name}" }.toSet())
        }
    }

    override fun getTtsSpeed(): Float = sharedPreferences.getFloat(KEY_TTS_SPEED, 1f)

    override fun setTtsSpeed(speed: Float) {
        sharedPreferences.edit { putFloat(KEY_TTS_SPEED, speed.coerceIn(0.7f, 1.4f)) }
    }

    override fun getTtsAdvancedSettings() = TtsAdvancedSettings(
        numThreads = sharedPreferences.getInt(KEY_TTS_THREADS, 4).coerceIn(1, 4),
        silenceScale = sharedPreferences.getFloat(KEY_TTS_SILENCE_SCALE, 0.2f).coerceIn(0f, 1f),
        supertonicSpeaker = sharedPreferences.getInt(KEY_TTS_SUPERTONIC_SPEAKER, 0).coerceIn(0, 9),
        supertonicSteps = sharedPreferences.getInt(KEY_TTS_SUPERTONIC_STEPS, 8).coerceIn(4, 12),
        kokoroSpeaker = sharedPreferences.getInt(KEY_TTS_KOKORO_SPEAKER, 0).coerceIn(0, 102),
        gosiaNoiseScale = sharedPreferences.getFloat(KEY_TTS_GOSIA_NOISE_SCALE, 0.667f).coerceIn(0f, 1f),
        gosiaDurationNoiseScale = sharedPreferences
            .getFloat(KEY_TTS_GOSIA_DURATION_NOISE_SCALE, 0.8f)
            .coerceIn(0f, 1f)
    )

    override fun setTtsAdvancedSettings(settings: TtsAdvancedSettings) {
        sharedPreferences.edit {
            putInt(KEY_TTS_THREADS, settings.numThreads.coerceIn(1, 4))
            putFloat(KEY_TTS_SILENCE_SCALE, settings.silenceScale.coerceIn(0f, 1f))
            putInt(KEY_TTS_SUPERTONIC_SPEAKER, settings.supertonicSpeaker.coerceIn(0, 9))
            putInt(KEY_TTS_SUPERTONIC_STEPS, settings.supertonicSteps.coerceIn(4, 12))
            putInt(KEY_TTS_KOKORO_SPEAKER, settings.kokoroSpeaker.coerceIn(0, 102))
            putFloat(KEY_TTS_GOSIA_NOISE_SCALE, settings.gosiaNoiseScale.coerceIn(0f, 1f))
            putFloat(
                KEY_TTS_GOSIA_DURATION_NOISE_SCALE,
                settings.gosiaDurationNoiseScale.coerceIn(0f, 1f)
            )
        }
    }

    private fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
        trySend(sharedPreferences.getBoolean(key, default))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == key) trySend(prefs.getBoolean(key, default))
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

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

        private val SECRET_KEYS = listOf(
            KEY_FRESHRSS_USERNAME,
            KEY_FRESHRSS_API_PASSWORD,
            KEY_MINIFLUX_API_TOKEN,
            KEY_OPENROUTER_API_KEY
        )

        private const val MAX_PERFORMANCE_RECORDS = 50
        private const val DEFAULT_OFFLINE_BACKLOG_TARGET = 0
        private const val DEFAULT_IMAGE_CACHE_BUDGET_MB = 500
        /** WorkManager's own floor for a periodic worker; anything shorter is silently raised. */
        private const val MIN_SYNC_INTERVAL_MINUTES = 15
    }
}
