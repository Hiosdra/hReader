package com.hiosdra.hreader.data.preferences

import android.content.Context
import com.hiosdra.hreader.data.ai.AiModel
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
    
    fun getAiModel(): AiModel {
        val savedModel = sharedPreferences.getString(KEY_AI_MODEL, AiModel.getDefault().name)
        return AiModel.entries.find { it.name == savedModel } ?: AiModel.getDefault()
    }
    
    fun setAiModel(model: AiModel) {
        sharedPreferences.edit()
            .putString(KEY_AI_MODEL, model.name)
            .apply()
    }
    
    companion object {
        private const val KEY_PAYWALL_BYPASS_METHOD = "paywall_bypass_method"
        private const val KEY_AI_MODEL = "ai_model"
    }
}