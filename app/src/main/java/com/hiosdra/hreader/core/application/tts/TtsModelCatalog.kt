package com.hiosdra.hreader.core.application.tts

import java.util.Locale

data class TtsModelDefinition(
    val model: TtsModel,
    val supportedLanguages: Set<String>,
    val voiceIdRange: IntRange? = null
)

object TtsModelCatalog {
    private val supertonicVoiceNames = listOf(
        "M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5"
    )

    private val definitions = listOf(
        TtsModelDefinition(
            model = TtsModel.SUPERTONIC,
            supportedLanguages = setOf(
                "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
                "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
                "sk", "sl", "es", "sv", "tr", "uk", "vi"
            ),
            voiceIdRange = supertonicVoiceNames.indices
        ),
        TtsModelDefinition(
            model = TtsModel.KOKORO,
            supportedLanguages = setOf("en", "zh"),
            voiceIdRange = 0..102
        ),
        TtsModelDefinition(
            model = TtsModel.KOKORO_V1_0,
            supportedLanguages = setOf("en", "zh"),
            voiceIdRange = 0..53
        ),
        TtsModelDefinition(
            model = TtsModel.COQUI_PL_MAI_FEMALE,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_LESSAC_HIGH,
            supportedLanguages = setOf("en")
        ),
        TtsModelDefinition(
            model = TtsModel.KITTEN_MINI,
            supportedLanguages = setOf("en")
        ),
        TtsModelDefinition(
            model = TtsModel.MATCHA_LJSPEECH,
            supportedLanguages = setOf("en")
        ),
        TtsModelDefinition(
            model = TtsModel.ANDROID,
            supportedLanguages = emptySet()
        )
    )
    private val definitionsByModel = definitions.associateBy(TtsModelDefinition::model)

    val models: List<TtsModel> = definitions.map(TtsModelDefinition::model)
    val supportedLanguages: List<String> = definitions
        .flatMap(TtsModelDefinition::supportedLanguages)
        .distinct()
        .sorted()

    fun definition(model: TtsModel): TtsModelDefinition = definitionsByModel.getValue(model)

    fun voiceIdRange(model: TtsModel): IntRange = definition(model).voiceIdRange ?: 0..0

    fun supertonicVoiceName(voiceId: Int): String =
        supertonicVoiceNames[voiceId.coerceIn(supertonicVoiceNames.indices)]

    fun compatibleModels(language: String): List<TtsModel> {
        val normalized = normalizeLanguage(language)
        return definitions
            .filter { normalized in it.supportedLanguages }
            .map(TtsModelDefinition::model)
            .plus(TtsModel.ANDROID)
    }

    fun isCompatible(model: TtsModel, language: String): Boolean =
        model in compatibleModels(language)

    fun normalizeLanguage(language: String): String = when (language.lowercase(Locale.ROOT)) {
        "in" -> "id"
        else -> language.lowercase(Locale.ROOT)
    }
}
