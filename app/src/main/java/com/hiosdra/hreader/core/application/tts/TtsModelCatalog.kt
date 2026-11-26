package com.hiosdra.hreader.core.application.tts

import java.util.Locale

data class TtsModelDefinition(
    val model: TtsModel,
    val supportedLanguages: Set<String>,
    val voiceNames: List<String> = emptyList()
) {
    val voiceIdRange: IntRange? = voiceNames.indices.takeUnless(IntRange::isEmpty)
}

object TtsModelCatalog {
    const val DEFAULT_KOKORO_VOICE = 3

    private val supertonicVoiceNames = listOf(
        "M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5"
    )

    private val kokoroVoiceNames = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole",
        "af_nova", "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir",
        "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma",
        "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis", "ef_dora",
        "em_alex", "ff_siwis", "hf_alpha", "hf_beta", "hm_omega", "hm_psi", "if_sara", "im_nicola",
        "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo", "pf_dora", "pm_alex",
        "pm_santa", "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi", "zm_yunjian", "zm_yunxi",
        "zm_yunxia", "zm_yunyang", "em_santa"
    )


    private val definitions = listOf(
        TtsModelDefinition(
            model = TtsModel.SUPERTONIC,
            supportedLanguages = setOf(
                "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el",
                "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru",
                "sk", "sl", "es", "sv", "tr", "uk", "vi"
            ),
            voiceNames = supertonicVoiceNames
        ),
        TtsModelDefinition(
            model = TtsModel.KOKORO,
            supportedLanguages = setOf("en", "zh"),
            voiceNames = kokoroVoiceNames
        ),
        TtsModelDefinition(
            model = TtsModel.COQUI_PL_MAI_FEMALE,
            supportedLanguages = setOf("pl")
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

    fun voiceName(model: TtsModel, voiceId: Int): String =
        definition(model).voiceNames.getOrNull(voiceId) ?: voiceId.toString()

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
