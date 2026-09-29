package com.hiosdra.hreader.core.application.usecase.article

import com.hiosdra.hreader.core.application.paywall.PaywallBypassMethod
import com.hiosdra.hreader.core.application.port.out.ReaderPreferences
import com.hiosdra.hreader.core.application.port.out.TtsPreferences
import com.hiosdra.hreader.core.application.tts.TtsModel
import kotlinx.coroutines.flow.Flow

data class ArticleDisplayPreferences(
    val bionicReadingEnabled: Boolean,
    val defaultPaywallBypassMethod: PaywallBypassMethod,
    val ttsModel: TtsModel
)

class ArticleDisplayPreferencesUseCase(
    private val reader: ReaderPreferences,
    private val tts: TtsPreferences
) {
    fun current(): ArticleDisplayPreferences = ArticleDisplayPreferences(
        bionicReadingEnabled = reader.getBionicReadingEnabled(),
        defaultPaywallBypassMethod = reader.getPaywallBypassMethod(),
        ttsModel = tts.getTtsModel()
    )

    fun observeBionicReadingEnabled(): Flow<Boolean> = reader.observeBionicReadingEnabled()
}
