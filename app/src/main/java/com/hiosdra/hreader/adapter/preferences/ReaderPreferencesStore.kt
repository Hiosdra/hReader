package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import kotlinx.coroutines.flow.Flow

internal class ReaderPreferencesStore(
    private val storage: PreferenceStorage
) : ReaderPreferences {
    override fun getPaywallBypassMethod(): PaywallBypassMethod =
        storage.get(ReaderPreferenceKeys.paywallBypassMethod).toPaywallBypassMethod()

    override fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        storage.set(ReaderPreferenceKeys.paywallBypassMethod, method.name)
    }

    override fun getBionicReadingEnabled(): Boolean =
        storage.value(ReaderPreferenceKeys.bionicReadingEnabled, false)

    override fun setBionicReadingEnabled(enabled: Boolean) {
        storage.set(ReaderPreferenceKeys.bionicReadingEnabled, enabled)
    }

    override fun observeBionicReadingEnabled(): Flow<Boolean> =
        storage.observeValue(ReaderPreferenceKeys.bionicReadingEnabled, false)

    override fun getCredibilityScoreEnabled(): Boolean =
        storage.value(ReaderPreferenceKeys.credibilityScoreEnabled, false)

    override fun setCredibilityScoreEnabled(enabled: Boolean) {
        storage.set(ReaderPreferenceKeys.credibilityScoreEnabled, enabled)
    }

    private fun String?.toPaywallBypassMethod(): PaywallBypassMethod =
        PaywallBypassMethod.entries.firstOrNull { it.name == this } ?: PaywallBypassMethod.SMRY_AI
}
