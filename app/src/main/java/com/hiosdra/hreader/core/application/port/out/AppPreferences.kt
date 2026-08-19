package com.hiosdra.hreader.core.application.port.out

interface AppPreferences :
    BackendPreferences,
    AiPreferences,
    ReaderPreferences,
    SentryPreferences,
    PerformancePreferences,
    SyncPreferences,
    TtsPreferences {
    suspend fun awaitReady() = Unit
}
