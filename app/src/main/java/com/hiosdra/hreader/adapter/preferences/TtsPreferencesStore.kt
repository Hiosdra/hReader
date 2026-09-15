package com.hiosdra.hreader.adapter.preferences

import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsAdvancedSettings
import com.hiosdra.hreader.core.application.tts.TtsModel
import com.hiosdra.hreader.core.application.tts.parseTtsLanguageOverrides

internal class TtsPreferencesStore(
    private val storage: PreferenceStorage
) : TtsPreferences {
    override fun getTtsModel(): TtsModel =
        TtsModel.fromName(storage.get(TtsPreferenceKeys.ttsModel))

    override fun setTtsModel(model: TtsModel) {
        storage.update { this[TtsPreferenceKeys.ttsModel] = model.name }
    }

    override fun getTtsModelForLanguage(language: String): TtsModel =
        getTtsLanguageOverrides()[language] ?: getTtsModel()

    override fun getTtsLanguageOverrides(): Map<String, TtsModel> =
        parseTtsLanguageOverrides(storage.get(TtsPreferenceKeys.ttsLanguageOverrides).orEmpty())

    override fun setTtsLanguageOverride(language: String, model: TtsModel?) {
        storage.update {
            val overrides = parseTtsLanguageOverrides(this[TtsPreferenceKeys.ttsLanguageOverrides].orEmpty())
                .updated(language, model)
            this[TtsPreferenceKeys.ttsLanguageOverrides] = overrides.toPreferenceSet()
        }
    }

    override fun getTtsSpeed(): Float =
        (storage.get(TtsPreferenceKeys.ttsSpeed) ?: 1f).coerceIn(0.7f, 1.4f)

    override fun setTtsSpeed(speed: Float) {
        val normalizedSpeed = speed.coerceIn(0.7f, 1.4f)
        storage.update { this[TtsPreferenceKeys.ttsSpeed] = normalizedSpeed }
    }

    override fun getTtsAdvancedSettings(): TtsAdvancedSettings = TtsAdvancedSettings(
        numThreads = (storage.get(TtsPreferenceKeys.ttsThreads) ?: 4).coerceIn(1, 4),
        silenceScale = (storage.get(TtsPreferenceKeys.ttsSilenceScale) ?: 0.2f).coerceIn(0f, 1f),
        supertonicSpeaker = (storage.get(TtsPreferenceKeys.ttsSupertonicSpeaker) ?: 0).coerceIn(0, 9),
        supertonicSteps = (storage.get(TtsPreferenceKeys.ttsSupertonicSteps) ?: 8).coerceIn(4, 12),
        kokoroSpeaker = (storage.get(TtsPreferenceKeys.ttsKokoroSpeaker) ?: 0).coerceIn(0, 102),
        kittenSpeaker = (storage.get(TtsPreferenceKeys.ttsKittenSpeaker) ?: 0).coerceIn(0, 7),
        vitsNoiseScale = (
            storage.get(TtsPreferenceKeys.ttsVitsNoiseScale)
                ?: storage.get(TtsPreferenceKeys.legacyTtsVitsNoiseScale)
                ?: 0.667f
            ).coerceIn(0f, 1f),
        vitsDurationNoiseScale = (
            storage.get(TtsPreferenceKeys.ttsVitsDurationNoiseScale)
                ?: storage.get(TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale)
                ?: 0.8f
            ).coerceIn(0f, 1f)
    )

    override fun setTtsAdvancedSettings(settings: TtsAdvancedSettings) {
        val normalizedSettings = settings.normalizedForStorage()
        storage.update {
            this[TtsPreferenceKeys.ttsThreads] = normalizedSettings.numThreads
            this[TtsPreferenceKeys.ttsSilenceScale] = normalizedSettings.silenceScale
            this[TtsPreferenceKeys.ttsSupertonicSpeaker] = normalizedSettings.supertonicSpeaker
            this[TtsPreferenceKeys.ttsSupertonicSteps] = normalizedSettings.supertonicSteps
            this[TtsPreferenceKeys.ttsKokoroSpeaker] = normalizedSettings.kokoroSpeaker
            this[TtsPreferenceKeys.ttsKittenSpeaker] = normalizedSettings.kittenSpeaker
            this[TtsPreferenceKeys.ttsVitsNoiseScale] = normalizedSettings.vitsNoiseScale
            this[TtsPreferenceKeys.ttsVitsDurationNoiseScale] = normalizedSettings.vitsDurationNoiseScale
            remove(TtsPreferenceKeys.legacyTtsVitsNoiseScale)
            remove(TtsPreferenceKeys.legacyTtsVitsDurationNoiseScale)
        }
    }

    private fun Map<String, TtsModel>.updated(
        language: String,
        model: TtsModel?
    ): Map<String, TtsModel> = toMutableMap().apply {
        if (model == null) remove(language) else this[language] = model
    }.toMap()

    private fun Map<String, TtsModel>.toPreferenceSet(): Set<String> =
        map { (language, model) -> "$language=${model.name}" }.toSet()
}
