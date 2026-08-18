package com.hiosdra.hreader.core.application.port.out

interface SentryPreferences {
    fun getSentryReportingEnabled(): Boolean
    fun setSentryReportingEnabled(enabled: Boolean)
}
