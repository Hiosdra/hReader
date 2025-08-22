package com.hiosdra.hreader.data.preferences

import android.content.Context
import com.hiosdra.hreader.data.paywall.PaywallBypassMethod

class PreferencesManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("hreader_prefs", Context.MODE_PRIVATE)
    
    fun getPaywallBypassMethod(): PaywallBypassMethod {
        val savedMethod = sharedPreferences.getString(KEY_PAYWALL_BYPASS_METHOD, PaywallBypassMethod.SMRY_AI.name)
        return PaywallBypassMethod.entries.find { it.name == savedMethod } ?: PaywallBypassMethod.SMRY_AI
    }
    
    fun setPaywallBypassMethod(method: PaywallBypassMethod) {
        sharedPreferences.edit()
            .putString(KEY_PAYWALL_BYPASS_METHOD, method.name)
            .apply()
    }
    
    fun getBionicReadingEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIONIC_READING_ENABLED, false)
    }
    
    fun setBionicReadingEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BIONIC_READING_ENABLED, enabled)
            .apply()
    }
    
    companion object {
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_BIONIC_READING_ENABLED = "bionic_reading_enabled"
    }
}