package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.port.out.SentryPreferences

internal class SentryPreferencesStore(
    private val storage: PreferenceStorage
) : SentryPreferences {
    override fun getSentryReportingEnabled(): Boolean =
        storage.value(SentryPreferenceKeys.reportingEnabled, true)

    override fun setSentryReportingEnabled(enabled: Boolean) {
        storage.set(SentryPreferenceKeys.reportingEnabled, enabled)
    }
}
