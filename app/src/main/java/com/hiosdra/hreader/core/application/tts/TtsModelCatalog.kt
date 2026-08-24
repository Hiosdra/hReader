package com.hiosdra.hreader.core.application.tts

import java.util.Locale

data class TtsModelDefinition(
    val model: TtsModel,
    val supportedLanguages: Set<String>
)

object TtsModelCatalog {
    private val definitions = listOf(
        TtsModelDefinition(
            model = TtsModel.SUPERTONIC,
            supportedLanguages = setOf(
                "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
                "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
                "sk", "sl", "es", "sv", "tr", "uk", "vi"
            )
        ),
        TtsModelDefinition(
            model = TtsModel.KOKORO,
            supportedLanguages = setOf("en", "zh")
        ),
        TtsModelDefinition(
            model = TtsModel.GOSIA,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_BASS_HIGH,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_DARKMAN_MEDIUM,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_JARVIS_MEDIUM,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_JUSTYNA_MEDIUM,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_MC_SPEECH_MEDIUM,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_MESKI_MEDIUM,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.PIPER_ZENSKI_MEDIUM,
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
