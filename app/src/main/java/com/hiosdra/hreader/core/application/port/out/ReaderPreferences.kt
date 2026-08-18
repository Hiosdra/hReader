package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import kotlinx.coroutines.flow.Flow

interface ReaderPreferences {
    fun getPaywallBypassMethod(): PaywallBypassMethod
    fun setPaywallBypassMethod(method: PaywallBypassMethod)
    fun getBionicReadingEnabled(): Boolean
    fun setBionicReadingEnabled(enabled: Boolean)
    fun observeBionicReadingEnabled(): Flow<Boolean>
    fun getCredibilityScoreEnabled(): Boolean
    fun setCredibilityScoreEnabled(enabled: Boolean)
}
