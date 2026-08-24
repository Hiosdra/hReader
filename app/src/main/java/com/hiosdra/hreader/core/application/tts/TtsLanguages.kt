package com.hiosdra.hreader.core.application.tts

object TtsLanguages {
    val supported: List<String>
        get() = TtsModelCatalog.supportedLanguages

    fun resolve(detected: List<String>, fallback: String): String = detected.asSequence()
        .map(TtsModelCatalog::normalizeLanguage)
        .firstOrNull(supported::contains)
        ?: TtsModelCatalog.normalizeLanguage(fallback).takeIf(supported::contains)
        ?: "en"

    fun compatibleModels(language: String): List<TtsModel> = TtsModelCatalog.compatibleModels(language)

    fun isCompatible(model: TtsModel, language: String): Boolean =
        TtsModelCatalog.isCompatible(model, language)
}
