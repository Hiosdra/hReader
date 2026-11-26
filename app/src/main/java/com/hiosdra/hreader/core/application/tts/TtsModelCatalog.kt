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
    const val DEFAULT_KOKORO_V1_0_VOICE = 3

    private val supertonicVoiceNames = listOf(
        "M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5"
    )

    private val kokoroV10VoiceNames = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole",
        "af_nova", "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir",
        "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma",
        "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis", "ef_dora",
        "em_alex", "ff_siwis", "hf_alpha", "hf_beta", "hm_omega", "hm_psi", "if_sara", "im_nicola",
        "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo", "pf_dora", "pm_alex",
        "pm_santa", "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi", "zm_yunjian", "zm_yunxi",
        "zm_yunxia", "zm_yunyang", "em_santa"
    )

    private val kokoroV11VoiceNames = listOf("af_maple", "af_sol", "bf_vale") +
        listOf(
            1, 2, 3, 4, 5, 6, 7, 8, 17, 18, 19, 21, 22, 23, 24, 26, 27, 28, 32, 36, 38, 39, 40, 42,
            43, 44, 46, 47, 48, 49, 51, 59, 60, 67, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 83, 84,
            85, 86, 87, 88, 90, 92, 93, 94, 99
        ).map { "zf_%03d".format(it) } +
        listOf(
            9, 10, 11, 12, 13, 14, 15, 16, 20, 25, 29, 30, 31, 33, 34, 35, 37, 41, 45, 50, 52, 53,
            54, 55, 56, 57, 58, 61, 62, 63, 64, 65, 66, 68, 69, 80, 81, 82, 89, 91, 95, 96, 97, 98,
            100
        ).map { "zm_%03d".format(it) }

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
            voiceNames = kokoroV11VoiceNames
        ),
        TtsModelDefinition(
            model = TtsModel.KOKORO_V1_0,
            supportedLanguages = setOf("en", "zh"),
            voiceNames = kokoroV10VoiceNames
        ),
        TtsModelDefinition(
            model = TtsModel.COQUI_PL_MAI_FEMALE,
            supportedLanguages = setOf("pl")
        ),
        TtsModelDefinition(
            model = TtsModel.KITTEN_MINI,
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
