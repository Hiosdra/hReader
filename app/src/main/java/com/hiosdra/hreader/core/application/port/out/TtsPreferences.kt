package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel

interface TtsPreferences {
    fun getTtsModel(): TtsModel
    fun setTtsModel(model: TtsModel)
    fun getTtsModelForLanguage(language: String): TtsModel
    fun getTtsLanguageOverrides(): Map<String, TtsModel>
    fun setTtsLanguageOverride(language: String, model: TtsModel?)
    fun getTtsSpeed(): Float
    fun setTtsSpeed(speed: Float)
    fun getTtsAdvancedSettings(): TtsAdvancedSettings
    fun setTtsAdvancedSettings(settings: TtsAdvancedSettings)
}
