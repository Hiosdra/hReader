package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ReaderPreferencesStore(
    private val storage: PreferenceStorage
) : ReaderPreferences {
    override fun getPaywallBypassMethod(): PaywallBypassMethod =
        storage.get(ReaderPreferenceKeys.paywallBypassMethod).toPaywallBypassMethod()

    override fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        storage.update { this[ReaderPreferenceKeys.paywallBypassMethod] = method.name }
    }

    override fun getBionicReadingEnabled(): Boolean =
        storage.get(ReaderPreferenceKeys.bionicReadingEnabled) ?: false

    override fun setBionicReadingEnabled(enabled: Boolean) {
        storage.update { this[ReaderPreferenceKeys.bionicReadingEnabled] = enabled }
    }

    override fun observeBionicReadingEnabled(): Flow<Boolean> =
        storage.observe(ReaderPreferenceKeys.bionicReadingEnabled).map { it ?: false }

    override fun getCredibilityScoreEnabled(): Boolean =
        storage.get(ReaderPreferenceKeys.credibilityScoreEnabled) ?: false

    override fun setCredibilityScoreEnabled(enabled: Boolean) {
        storage.update { this[ReaderPreferenceKeys.credibilityScoreEnabled] = enabled }
    }

    private fun String?.toPaywallBypassMethod(): PaywallBypassMethod =
        PaywallBypassMethod.entries.firstOrNull { it.name == this } ?: PaywallBypassMethod.SMRY_AI
}
