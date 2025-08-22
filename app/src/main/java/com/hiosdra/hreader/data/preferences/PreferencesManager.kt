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
    
    fun getLastSyncTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }
    
    fun setLastSyncTimestamp(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp)
            .apply()
    }
    
    companion object {
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }
}