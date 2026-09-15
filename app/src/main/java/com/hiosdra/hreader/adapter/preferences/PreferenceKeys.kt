package com.hiosdra.hreader.adapter.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object BackendPreferenceKeys {
    const val BACKEND_TYPE = "backend_type"
    const val FRESHRSS_SERVER_URL = "freshrss_server_url"
    const val MINIFLUX_SERVER_URL = "miniflux_server_url"

    val backendType = stringPreferencesKey(BACKEND_TYPE)
    val freshRssServerUrl = stringPreferencesKey(FRESHRSS_SERVER_URL)
    val minifluxServerUrl = stringPreferencesKey(MINIFLUX_SERVER_URL)
}

internal object SecretPreferenceKeys {
    const val FRESHRSS_USERNAME = "freshrss_username"
    const val FRESHRSS_API_PASSWORD = "freshrss_api_password"
    const val MINIFLUX_API_TOKEN = "miniflux_api_token"
    const val OPENROUTER_API_KEY = "openrouter_api_key"

    val freshRssUsername = stringPreferencesKey(FRESHRSS_USERNAME)
    val freshRssApiPassword = stringPreferencesKey(FRESHRSS_API_PASSWORD)
    val minifluxApiToken = stringPreferencesKey(MINIFLUX_API_TOKEN)
    val openRouterApiKey = stringPreferencesKey(OPENROUTER_API_KEY)
    val freshRssUsernameEncrypted = stringPreferencesKey("${FRESHRSS_USERNAME}_encrypted_v1")
    val freshRssApiPasswordEncrypted = stringPreferencesKey("${FRESHRSS_API_PASSWORD}_encrypted_v1")
    val minifluxApiTokenEncrypted = stringPreferencesKey("${MINIFLUX_API_TOKEN}_encrypted_v1")
    val openRouterApiKeyEncrypted = stringPreferencesKey("${OPENROUTER_API_KEY}_encrypted_v1")
    val migrationState = stringPreferencesKey("secret_migration_state")
}

internal object AiPreferenceKeys {
    const val AI_MODEL = "ai_model"
    const val GEMMA_BACKEND = "gemma_backend"
    const val GEMMA_DOWNLOAD_UNMETERED_ONLY = "gemma_download_unmetered_only"

    val aiModel = stringPreferencesKey(AI_MODEL)
    val gemmaBackend = stringPreferencesKey(GEMMA_BACKEND)
    val gemmaDownloadUnmeteredOnly = booleanPreferencesKey(GEMMA_DOWNLOAD_UNMETERED_ONLY)
}

internal object ReaderPreferenceKeys {
    const val PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
    const val BIONIC_READING_ENABLED = "bionic_reading_enabled"
    const val CREDIBILITY_SCORE_ENABLED = "credibility_score_enabled"

    val paywallBypassMethod = stringPreferencesKey(PAYWALL_BYPASS_METHOD)
    val bionicReadingEnabled = booleanPreferencesKey(BIONIC_READING_ENABLED)
    val credibilityScoreEnabled = booleanPreferencesKey(CREDIBILITY_SCORE_ENABLED)
}

internal object SentryPreferenceKeys {
    const val SENTRY_REPORTING_ENABLED = "sentry_reporting_enabled"

    val reportingEnabled = booleanPreferencesKey(SENTRY_REPORTING_ENABLED)
}

internal object PerformancePreferenceKeys {
    const val SYNC_PERFORMANCE_RECORDS = "sync_performance_records"

    val syncPerformanceRecords = stringPreferencesKey(SYNC_PERFORMANCE_RECORDS)
}

internal object SyncPreferenceKeys {
    const val LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    const val CACHE_OWNER = "cache_owner"
    const val CACHE_CLEANUP_PENDING = "cache_cleanup_pending"
    const val LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp"
    const val SYNC_CHECKPOINT = "sync_checkpoint"
    const val SYNC_HEALTH = "sync_health"
    const val OFFLINE_BACKLOG_TARGET = "offline_backlog_target"
    const val IMAGE_DOWNLOAD_ENABLED = "image_download_enabled"
    const val IMAGE_CACHE_BUDGET_MB = "image_cache_budget_mb"
    const val LAST_CHAINED_SYNC_TIMESTAMP = "last_chained_sync_timestamp"
    const val SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
    const val SYNC_MODE = "sync_mode"
    const val SYNC_UNMETERED_ONLY = "sync_unmetered_only"
    const val SYNC_WHILE_ROAMING = "sync_while_roaming"
    const val QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    const val QUIET_HOURS_START = "quiet_hours_start"
    const val QUIET_HOURS_END = "quiet_hours_end"

    val lastSyncTimestamp = longPreferencesKey(LAST_SYNC_TIMESTAMP)
    val cacheOwner = stringPreferencesKey(CACHE_OWNER)
    val cacheCleanupPending = booleanPreferencesKey(CACHE_CLEANUP_PENDING)
    val lastFullSyncTimestamp = longPreferencesKey(LAST_FULL_SYNC_TIMESTAMP)
    val syncCheckpoint = stringPreferencesKey(SYNC_CHECKPOINT)
    val syncHealth = stringPreferencesKey(SYNC_HEALTH)
    val offlineBacklogTarget = intPreferencesKey(OFFLINE_BACKLOG_TARGET)
    val imageDownloadEnabled = booleanPreferencesKey(IMAGE_DOWNLOAD_ENABLED)
    val imageCacheBudgetMegabytes = intPreferencesKey(IMAGE_CACHE_BUDGET_MB)
    val lastChainedSyncTimestamp = longPreferencesKey(LAST_CHAINED_SYNC_TIMESTAMP)
    val syncIntervalMinutes = intPreferencesKey(SYNC_INTERVAL_MINUTES)
    val syncMode = stringPreferencesKey(SYNC_MODE)
    val syncOnUnmeteredOnly = booleanPreferencesKey(SYNC_UNMETERED_ONLY)
    val syncWhileRoaming = booleanPreferencesKey(SYNC_WHILE_ROAMING)
    val quietHoursEnabled = booleanPreferencesKey(QUIET_HOURS_ENABLED)
    val quietHoursStart = intPreferencesKey(QUIET_HOURS_START)
    val quietHoursEnd = intPreferencesKey(QUIET_HOURS_END)
}

internal object TtsPreferenceKeys {
    const val TTS_MODEL = "tts_model"
    const val TTS_SPEED = "tts_speed"
    const val TTS_LANGUAGE_OVERRIDES = "tts_language_overrides"
    const val TTS_THREADS = "tts_threads"
    const val TTS_SILENCE_SCALE = "tts_silence_scale"
    const val TTS_SUPERTONIC_SPEAKER = "tts_supertonic_speaker"
    const val TTS_SUPERTONIC_STEPS = "tts_supertonic_steps"
    const val TTS_KOKORO_SPEAKER = "tts_kokoro_speaker"
    const val TTS_KITTEN_SPEAKER = "tts_kitten_speaker"
    const val TTS_VITS_NOISE_SCALE = "tts_vits_noise_scale"
    const val TTS_VITS_DURATION_NOISE_SCALE = "tts_vits_duration_noise_scale"
    const val LEGACY_TTS_VITS_NOISE_SCALE = "tts_gosia_noise_scale"
    const val LEGACY_TTS_VITS_DURATION_NOISE_SCALE = "tts_gosia_duration_noise_scale"

    val ttsModel = stringPreferencesKey(TTS_MODEL)
    val ttsSpeed = floatPreferencesKey(TTS_SPEED)
    val ttsLanguageOverrides = stringSetPreferencesKey(TTS_LANGUAGE_OVERRIDES)
    val ttsThreads = intPreferencesKey(TTS_THREADS)
    val ttsSilenceScale = floatPreferencesKey(TTS_SILENCE_SCALE)
    val ttsSupertonicSpeaker = intPreferencesKey(TTS_SUPERTONIC_SPEAKER)
    val ttsSupertonicSteps = intPreferencesKey(TTS_SUPERTONIC_STEPS)
    val ttsKokoroSpeaker = intPreferencesKey(TTS_KOKORO_SPEAKER)
    val ttsKittenSpeaker = intPreferencesKey(TTS_KITTEN_SPEAKER)
    val ttsVitsNoiseScale = floatPreferencesKey(TTS_VITS_NOISE_SCALE)
    val ttsVitsDurationNoiseScale = floatPreferencesKey(TTS_VITS_DURATION_NOISE_SCALE)
    val legacyTtsVitsNoiseScale = floatPreferencesKey(LEGACY_TTS_VITS_NOISE_SCALE)
    val legacyTtsVitsDurationNoiseScale = floatPreferencesKey(LEGACY_TTS_VITS_DURATION_NOISE_SCALE)
}
